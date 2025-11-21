#!/usr/bin/env bash
set -euo pipefail

API_BASE="http://localhost:8080"
VIDEO_PATH="test/test_video_DO_NOT_DELETE"
CODEC="h264"
ID_TOKEN=""

usage() {
  cat <<EOF
Usage: $0 --id-token TOKEN [options]
Options:
  --api-base URL       Base URL for the backend (default: ${API_BASE})
  --video-path PATH    Video path to verify (default: ${VIDEO_PATH})
  --codec CODEC        Codec to request (default: ${CODEC})
  --id-token TOKEN     Firebase admin/user ID token to authenticate (required)
  -h, --help           Show this help
Environment variables are ignored in favor of the CLI flags to keep tests consistent.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --api-base) API_BASE="$2"; shift 2 ;;
    --video-path) VIDEO_PATH="$2"; shift 2 ;;
    --codec) CODEC="$2"; shift 2 ;;
    --id-token) ID_TOKEN="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
done

if [[ -z "$ID_TOKEN" ]]; then
  echo "--id-token is required" >&2
  usage
  exit 1
fi

need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing dependency: $1" >&2; exit 1; }; }
need jq
need curl
need openssl
need xxd
need python3

urlencode() {
  python3 - <<'PY' "$1"
import sys, urllib.parse
print(urllib.parse.quote(sys.argv[1], safe=''))
PY
}

ENCODED_VIDEO_PATH="$(urlencode "$VIDEO_PATH")"
TMP_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

# Step 1: ask the backend for signed CloudFront URLs (exactly what the apps do).
# The response includes `videoUrl` + `metadataUrl`. Both are signed URLs pointing
# at CloudFront/S3, so subsequent downloads exercise the real CDN path.
echo ">> Requesting signed URLs for $VIDEO_PATH ($CODEC)"
VIDEO_JSON="$(curl -sSf \
  -H "Authorization: Bearer $ID_TOKEN" \
  "$API_BASE/user/video?videoPath=${ENCODED_VIDEO_PATH}&codec=$CODEC")"
METADATA_URL="$(echo "$VIDEO_JSON" | jq -r '.metadataUrl')"
if [[ -z "$METADATA_URL" || "$METADATA_URL" == "null" ]]; then
  echo "Failed to fetch metadataUrl from /user/video response" >&2
  exit 1
fi

COOKIE_HEADER=()
SIGNED_COOKIES="$(echo "$VIDEO_JSON" | jq -c '.signedCookies // empty')"
if [[ -n "$SIGNED_COOKIES" && "$SIGNED_COOKIES" != "null" ]]; then
  COOKIE_STRING="$(echo "$SIGNED_COOKIES" | jq -r 'to_entries | map(.value) | join("; ")')"
  if [[ -n "$COOKIE_STRING" ]]; then
    COOKIE_HEADER=(-H "Cookie: $COOKIE_STRING")
    echo ">> Using signed cookies: $COOKIE_STRING"
  fi
fi

echo ">> Downloading metadata: $METADATA_URL"
curl -sSf "${COOKIE_HEADER[@]}" "$METADATA_URL" -o "$TMP_DIR/metadata.json"
MASTER_REL="$(jq -r '.hls.master_playlist' "$TMP_DIR/metadata.json")"
VARIANT_REL="$(jq -r --arg codec "$CODEC" '.hls.variants[] | select(.codec == $codec) | .playlist' "$TMP_DIR/metadata.json")"
if [[ -z "$VARIANT_REL" || "$VARIANT_REL" == "null" ]]; then
  echo "Variant playlist for codec $CODEC not found in metadata" >&2
  exit 1
fi

METADATA_DIR="${METADATA_URL%/*}"
MASTER_URL="${METADATA_DIR%/}/$MASTER_REL"
VARIANT_URL="${METADATA_DIR%/}/$VARIANT_REL"

echo ">> Downloading master playlist: $MASTER_URL"
curl -sSf "${COOKIE_HEADER[@]}" "$MASTER_URL" -o "$TMP_DIR/master.m3u8"
echo ">> Downloading variant playlist: $VARIANT_URL"
curl -sSf "${COOKIE_HEADER[@]}" "$VARIANT_URL" -o "$TMP_DIR/variant.m3u8"

SEGMENT_PATH="$(awk 'NF && $0 !~ /^#/{print; exit}' "$TMP_DIR/variant.m3u8")"
if [[ -z "$SEGMENT_PATH" ]]; then
  echo "Unable to determine first segment from variant playlist" >&2
  exit 1
fi
SEGMENT_URL="${VARIANT_URL%/*}/$SEGMENT_PATH"
echo ">> Downloading first encrypted segment: $SEGMENT_URL"
curl -sSf "${COOKIE_HEADER[@]}" "$SEGMENT_URL" -o "$TMP_DIR/segment.ts.enc"

IV_HEX="$(perl -ne 'print $1 if /IV=0x([0-9A-Fa-f]+)/' "$TMP_DIR/variant.m3u8")"
if [[ -z "$IV_HEX" ]]; then
  echo "Unable to extract IV from variant playlist" >&2
  exit 1
fi

# Step 2: fetch the AES key from `/user/video/license/key`. This re-runs the
# entitlement guard and returns the same 16-byte key the mobile apps store inside
# their sandbox. We only fetch it once, matching the app’s behavior.
echo ">> Fetching AES-128 key from backend"
curl -sSf \
  -H "Authorization: Bearer $ID_TOKEN" \
  "$API_BASE/user/video/license/key?videoPath=${ENCODED_VIDEO_PATH}&codec=$CODEC" \
  -o "$TMP_DIR/key.bin"
KEY_HEX="$(xxd -p "$TMP_DIR/key.bin" | tr -d '\n')"
if [[ -z "$KEY_HEX" ]]; then
  echo "Failed to read key bytes" >&2
  exit 1
fi

echo ">> Decrypting the first segment locally"
openssl aes-128-cbc -d -in "$TMP_DIR/segment.ts.enc" -out "$TMP_DIR/segment.ts" \
  -nosalt -iv "$IV_HEX" -K "$KEY_HEX" >/dev/null 2>&1

FIRST_BYTE="$(xxd -p -l 1 "$TMP_DIR/segment.ts")"
if [[ "$FIRST_BYTE" != "47" ]]; then
  echo "Decrypted segment does not look like an MPEG-TS packet (expected 0x47, got $FIRST_BYTE)" >&2
  exit 1
fi

echo "✅ Successfully downloaded, decrypted, and validated segment for $VIDEO_PATH ($CODEC)"
