#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_REGISTER_ENDPOINT="${REGISTER_READY_ENDPOINT:-http://localhost:8080/admin/video-assets}"

# --------------------------------------------------
# Video management pipeline – Stage 2 (upload)
# --------------------------------------------------
# Responsibilities:
#   1. Take the output directory from encode_ready.sh
#   2. Upload metadata + HLS tree (and optionally MP4 masters) to S3
#   3. Remind the operator to run register_ready.sh afterwards
# Registration is intentionally decoupled so operators can run it later
# or use video_management.sh to drive all stages at once.
# Example:
#   ./upload_ready.sh --input-dir out/lesson-abc --bucket golfbeta-eu-north-1-videos-39695a --prefix videos/test/lesson-abc

DEFAULT_BUCKET=""
DEFAULT_PREFIX=""
AWS_PROFILE="${AWS_PROFILE:-golfbeta}"
AWS_REGION="${AWS_REGION:-eu-north-1}"
AWS_PROFILE_FLAG=(--profile "$AWS_PROFILE")
AWS_REGION_FLAG=(--region "$AWS_REGION")

INPUT_DIR=""
UPLOAD_MP4="false"
usage() {
  cat <<EOF
Usage: $0 --input-dir PATH [options]
Options:
  --input-dir PATH         Directory produced by encode_ready.sh (required)
  --bucket NAME            Target S3 bucket
  --prefix PATH            Target prefix (e.g. videos/my-lesson)
  --aws-profile NAME       AWS CLI profile (default ${AWS_PROFILE})
  --aws-region NAME        AWS region (default ${AWS_REGION})
  --upload-mp4 true|false  Upload MP4 masters (default false)
  -h, --help               Show this help
Examples:
  ./upload_ready.sh --input-dir out/lesson-abc --bucket golfbeta-eu-north-1-videos-39695a --prefix videos/test/lesson-abc
EOF
}

trim_prefix() {
  local val="$1"
  val="${val#/}"
  val="${val%/}"
  echo "$val"
}

need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing dependency: $1"; exit 1; }; }
need aws

while [[ $# -gt 0 ]]; do
  case "$1" in
    --input-dir) INPUT_DIR="$2"; shift 2 ;;
    --bucket) DEFAULT_BUCKET="$2"; shift 2 ;;
    --prefix) DEFAULT_PREFIX="$2"; shift 2 ;;
    --aws-profile) AWS_PROFILE="$2"; AWS_PROFILE_FLAG=(--profile "$2"); shift 2 ;;
    --aws-region) AWS_REGION="$2"; AWS_REGION_FLAG=(--region "$2"); shift 2 ;;
    --upload-mp4) UPLOAD_MP4="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
done

if [[ -z "$INPUT_DIR" ]]; then
  echo "--input-dir is required" >&2
  usage
  exit 1
fi
INPUT_DIR="$(cd "$INPUT_DIR" && pwd)"

if [[ ! -d "$INPUT_DIR" ]]; then
  echo "Input directory not found: $INPUT_DIR" >&2
  exit 1
fi

if ! aws "${AWS_PROFILE_FLAG[@]}" "${AWS_REGION_FLAG[@]}" sts get-caller-identity >/dev/null 2>&1; then
  echo "AWS SSO not logged in for profile '${AWS_PROFILE}'. Run: aws sso login --profile ${AWS_PROFILE}"
  exit 1
fi

if [[ -z "$DEFAULT_BUCKET" ]]; then
  read -r -p "Enter S3 bucket name: " DEFAULT_BUCKET
fi

if [[ -z "$DEFAULT_PREFIX" ]]; then
  read -r -p "Enter S3 destination prefix (e.g. analysis/lesson-abc): " DEFAULT_PREFIX
fi

UPLOAD_MP4="${UPLOAD_MP4:-false}"
TARGET_BUCKET="$DEFAULT_BUCKET"
TARGET_PREFIX="$(trim_prefix "$DEFAULT_PREFIX")"

META_JSON="$(find "$INPUT_DIR" -maxdepth 1 -name '*_metadata.json' | head -n1 || true)"
LICENSE_JSON="$(find "$INPUT_DIR" -maxdepth 1 -name '*_license_material.json' | head -n1 || true)"
H264_OUT="$(find "$INPUT_DIR" -maxdepth 1 -name '*_sourcefps_h264.mp4' | head -n1 || true)"
HEVC_OUT="$(find "$INPUT_DIR" -maxdepth 1 -name '*_sourcefps_hevc.mp4' | head -n1 || true)"
HLS_DIR="$INPUT_DIR/hls"

if [[ -z "$META_JSON" || -z "$LICENSE_JSON" || -z "$H264_OUT" || -z "$HEVC_OUT" || ! -d "$HLS_DIR" ]]; then
  echo "Input directory missing expected encode outputs. Ensure encode_ready.sh completed successfully." >&2
  exit 1
fi

if [[ -z "$TARGET_BUCKET" ]]; then
  echo "S3 bucket is required." >&2
  exit 1
fi

if [[ -n "$TARGET_PREFIX" ]]; then
  DEST_URI="s3://${TARGET_BUCKET}/${TARGET_PREFIX}"
else
  DEST_URI="s3://${TARGET_BUCKET}"
fi

upload_one() {
  local SRC="$1"
  local DEST_REL="${2:-}"
  local CT_OVERRIDE="${3:-}"
  local CT="$CT_OVERRIDE"
  if [[ -z "$DEST_REL" ]]; then
    DEST_REL="$(basename "$SRC")"
  fi
  DEST_REL="${DEST_REL#/}"
  if [[ -z "$CT" ]]; then
    case "$SRC" in
      *.mp4) CT="video/mp4" ;;
      *.json) CT="application/json" ;;
      *.m3u8) CT="application/vnd.apple.mpegurl" ;;
      *.ts) CT="video/mp2t" ;;
      *) CT="application/octet-stream" ;;
    esac
  fi
  aws s3 cp "$SRC" "${DEST_URI}/${DEST_REL}" \
    --content-type "$CT" \
    --cache-control "public,max-age=31536000,immutable" \
    --sse AES256 \
    --no-progress \
    "${AWS_PROFILE_FLAG[@]}" "${AWS_REGION_FLAG[@]}"
}

upload_hls_tree() {
  if [[ ! -d "$HLS_DIR" ]]; then
    return
  fi
  LC_ALL=C find "$HLS_DIR" -type f | sort | while read -r FILE; do
    local REL_PATH="${FILE#${INPUT_DIR}/}"
    upload_one "$FILE" "$REL_PATH"
  done
}

echo "==> Uploading to ${DEST_URI}"
echo

if [[ "$UPLOAD_MP4" = "true" ]]; then
  echo "Uploading MP4 masters (UPLOAD_MP4=true)"
  upload_one "$H264_OUT"
  upload_one "$HEVC_OUT"
else
  echo "Skipping MP4 uploads (set --upload-mp4 true to include them)"
fi

upload_one "$META_JSON"
upload_hls_tree

echo
echo "Upload complete:"
aws s3 ls "${DEST_URI}/" --human-readable --summarize "${AWS_PROFILE_FLAG[@]}" "${AWS_REGION_FLAG[@]}"

cat <<EOF

Next step: register the asset so the backend can serve keys.

  ${SCRIPT_DIR}/register_ready.sh \\
    --license-json "$LICENSE_JSON" \\
    --admin-token "<firebase admin token>" \\
    --endpoint ${DEFAULT_REGISTER_ENDPOINT}

EOF
