#!/usr/bin/env bash
set -euo pipefail

# --------------------------------------------------
# Video management pipeline – Stage 3 (register)
# --------------------------------------------------
# Responsibilities:
#   1. Transform the snake_case license JSON emitted by encode_ready.sh
#   2. POST the camelCase payload to /admin/video-assets using a Firebase admin token
#   3. Provide simple error handling/logging for failed registrations
# Used standalone or via video_management.sh --parts full.
# Example:
#   ./register_ready.sh --license-json out/lesson-abc_license_material.json --admin-token-file ~/token.txt

LICENSE_JSON=""
ADMIN_TOKEN=""
ADMIN_TOKEN_FILE=""
ENDPOINT="${REGISTER_READY_ENDPOINT:-http://localhost:8080/admin/video-assets}"
PAYLOAD_FILE=""

usage() {
  cat <<EOF
Usage: $0 --license-json PATH (--admin-token TOKEN | --admin-token-file FILE) [options]
Options:
  --license-json PATH      Path to *_license_material.json produced by encode_ready.sh
  --admin-token TOKEN      Firebase admin ID token for Authorization header
  --admin-token-file FILE  File containing the admin token (preferred)
  --endpoint URL           Override backend endpoint (default ${ENDPOINT})
  --payload-file PATH      Write transformed request JSON to PATH (default temp file)
  -h, --help               Show this help
Examples:
  ./register_ready.sh --license-json out/lesson-abc_license_material.json --admin-token-file ~/tmp/admin.token
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --license-json) LICENSE_JSON="$2"; shift 2 ;;
    --admin-token) ADMIN_TOKEN="$2"; shift 2 ;;
    --admin-token-file) ADMIN_TOKEN_FILE="$2"; shift 2 ;;
    --endpoint) ENDPOINT="$2"; shift 2 ;;
    --payload-file) PAYLOAD_FILE="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
done

if [[ -z "$LICENSE_JSON" ]]; then
  echo "--license-json is required" >&2
  usage
  exit 1
fi

if [[ -n "$ADMIN_TOKEN_FILE" ]]; then
  if [[ ! -f "$ADMIN_TOKEN_FILE" ]]; then
    echo "Admin token file not found: $ADMIN_TOKEN_FILE" >&2
    exit 1
  fi
  ADMIN_TOKEN="$(<"$ADMIN_TOKEN_FILE")"
fi

if [[ -z "$ADMIN_TOKEN" ]]; then
  echo "Provide an admin token via --admin-token or --admin-token-file" >&2
  exit 1
fi

if [[ ! -f "$LICENSE_JSON" ]]; then
  echo "License JSON not found: $LICENSE_JSON" >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to transform the payload. Install via brew/apt/etc." >&2
  exit 1
fi

TMP_PAYLOAD=""
if [[ -z "$PAYLOAD_FILE" ]]; then
  TMP_PAYLOAD="$(mktemp)"
  PAYLOAD_FILE="$TMP_PAYLOAD"
else
  PAYLOAD_FILE="$(cd "$(dirname "$PAYLOAD_FILE")" && pwd)/$(basename "$PAYLOAD_FILE")"
fi

RESPONSE_FILE="$(mktemp)"

cleanup() {
  if [[ -n "$TMP_PAYLOAD" ]]; then
    rm -f "$TMP_PAYLOAD"
  fi
  rm -f "$RESPONSE_FILE"
}
trap cleanup EXIT

jq '{videoPath: .video_path, keyHex: .key_hex, keyBase64: .key_base64, keyVersion: (.key_version // 1)}' \
  "$LICENSE_JSON" > "$PAYLOAD_FILE"

HTTP_STATUS="$(curl -sS -o "$RESPONSE_FILE" -w "%{http_code}" \
  -X POST \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  --data-binary @"${PAYLOAD_FILE}" \
  "$ENDPOINT")" || HTTP_STATUS="000"

if [[ "$HTTP_STATUS" -lt 200 || "$HTTP_STATUS" -ge 300 ]]; then
  echo "Registration failed (HTTP ${HTTP_STATUS}). Response:" >&2
  cat "$RESPONSE_FILE" >&2
  exit 1
fi

echo "Registered video asset from ${LICENSE_JSON} via ${ENDPOINT}"
cat "$RESPONSE_FILE"
