#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENCODE_SCRIPT="$SCRIPT_DIR/encode_ready.sh"
UPLOAD_SCRIPT="$SCRIPT_DIR/upload_ready.sh"
REGISTER_SCRIPT="$SCRIPT_DIR/register_ready.sh"

# --------------------------------------------------
# Video management pipeline – Orchestrator
# --------------------------------------------------
# Responsibilities:
#   1. Provide a single entry point to run encode, upload, and register
#   2. Allow partial runs via --parts (encode-only, encode-upload, full)
#   3. Collect the inputs (MP4 path, S3 details, admin token) needed by each stage
# This script simply shells out to the dedicated stage scripts so they can
# remain decoupled and still be used manually.
# Example (full flow with mandatory flags):
#   ./video_management.sh --parts full \
#     --input-mp4 swing.mp4 --output-dir out/lesson-abc \
#     --bucket my-video-bucket --prefix videos/lesson-abc \
#     --admin-token-file ~/.firebase_admin_token

PARTS="full"
INPUT_MP4=""
OUTPUT_DIR=""
CLOCKWISE_ROTATE=""
VIDEO_PATH_HINT=""
UPLOAD_BUCKET=""
UPLOAD_MP4="false"
ACTUAL_VIDEO_PATH=""
META_JSON_PATH=""
AWS_PROFILE="${AWS_PROFILE:-golfbeta}"
AWS_REGION="${AWS_REGION:-eu-north-1}"
ADMIN_TOKEN=""
ADMIN_TOKEN_FILE=""
REGISTER_ENDPOINT="${REGISTER_READY_ENDPOINT:-http://localhost:8080/admin/video-assets}"

usage() {
  cat <<EOF
Usage: $0 --input-mp4 PATH --output-dir PATH [options]
Options:
  --parts MODE             encode-only | encode-upload | full (default full)
  --input-mp4 PATH         Source MP4 (required)
  --output-dir PATH        Output directory (required)
  --video-path PATH        Logical video path (used for naming + default prefix)
  --clockwise-rotate DEG   Rotate source clockwise by 0/90/180/270
  --bucket NAME            S3 bucket for upload stage
  --aws-profile NAME       AWS CLI profile (default ${AWS_PROFILE})
  --aws-region NAME        AWS region (default ${AWS_REGION})
  --upload-mp4 true|false  Upload MP4 masters during upload stage (default false)
  --admin-token TOKEN      Firebase admin token for register stage
  --admin-token-file PATH  Read token from file
  --register-endpoint URL  Register endpoint (default ${REGISTER_ENDPOINT})
  -h, --help               Show this help
Examples:
  ./video_management.sh --parts encode-only --input-mp4 swing.mp4 --output-dir out/lesson-abc
  ./video_management.sh --parts encode-upload --input-mp4 swing.mp4 --video-path lessons/abc --output-dir out/lesson-abc --bucket my-bucket
  ./video_management.sh --parts full --input-mp4 swing.mp4 --video-path lessons/abc --output-dir out/lesson-abc --bucket my-bucket --admin-token-file ~/token.txt
EOF
}

need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing dependency: $1"; exit 1; }; }
need jq

trim_prefix() {
  local val="$1"
  val="${val#/}"
  val="${val%/}"
  echo "$val"
}

normalise_video_path() {
  local candidate="$1"
  local fallback="$2"
  local trimmed="${candidate:-$fallback}"
  trimmed="${trimmed#/}"
  trimmed="${trimmed%/}"
  if [[ "$trimmed" == videos/* ]]; then
    trimmed="${trimmed#videos/}"
  fi
  if [[ -z "$trimmed" ]]; then
    trimmed="$fallback"
  fi
  echo "$trimmed"
}

find_metadata_file() {
  find "$OUTPUT_DIR" -maxdepth 1 -name '*_metadata.json' | head -n1 || true
}

extract_video_path_from_meta() {
  local meta="$1"
  if [[ -f "$meta" ]]; then
    jq -r '.video_path // empty' "$meta"
  fi
}

refresh_metadata_state() {
  META_JSON_PATH="$(find_metadata_file)"
  if [[ -n "$META_JSON_PATH" ]]; then
    local candidate
    candidate="$(extract_video_path_from_meta "$META_JSON_PATH")"
    if [[ -n "$candidate" && "$candidate" != "null" ]]; then
      ACTUAL_VIDEO_PATH="$candidate"
    fi
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --parts) PARTS="$2"; shift 2 ;;
    --input-mp4) INPUT_MP4="$2"; shift 2 ;;
    --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
    --clockwise-rotate) CLOCKWISE_ROTATE="$2"; shift 2 ;;
    --video-path) VIDEO_PATH_HINT="$2"; shift 2 ;;
    --bucket) UPLOAD_BUCKET="$2"; shift 2 ;;
    --aws-profile) AWS_PROFILE="$2"; shift 2 ;;
    --aws-region) AWS_REGION="$2"; shift 2 ;;
    --upload-mp4) UPLOAD_MP4="$2"; shift 2 ;;
    --admin-token) ADMIN_TOKEN="$2"; shift 2 ;;
    --admin-token-file) ADMIN_TOKEN_FILE="$2"; shift 2 ;;
    --register-endpoint) REGISTER_ENDPOINT="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
done

case "$PARTS" in
  encode-only) RUN_UPLOAD=0; RUN_REGISTER=0 ;;
  encode-upload) RUN_UPLOAD=1; RUN_REGISTER=0 ;;
  full) RUN_UPLOAD=1; RUN_REGISTER=1 ;;
  *) echo "--parts must be encode-only, encode-upload, or full" >&2; exit 1 ;;
esac
RUN_ENCODE=1

if [[ -z "$INPUT_MP4" || -z "$OUTPUT_DIR" ]]; then
  echo "--input-mp4 and --output-dir are required" >&2
  usage
  exit 1
fi

INPUT_MP4="$(cd "$(dirname "$INPUT_MP4")" && pwd)/$(basename "$INPUT_MP4")"
OUTPUT_PARENT="$(dirname "$OUTPUT_DIR")"
OUTPUT_BASENAME="$(basename "$OUTPUT_DIR")"
mkdir -p "$OUTPUT_PARENT"
OUTPUT_DIR="$(cd "$OUTPUT_PARENT" && pwd)/$OUTPUT_BASENAME"
mkdir -p "$OUTPUT_DIR"
SOURCE_BASENAME="$(basename "${INPUT_MP4%.*}")"
if [[ -n "$VIDEO_PATH_HINT" ]]; then
  ACTUAL_VIDEO_PATH="$(normalise_video_path "$VIDEO_PATH_HINT" "$SOURCE_BASENAME")"
fi

if [[ "$RUN_UPLOAD" -eq 1 && -z "$UPLOAD_BUCKET" ]]; then
  echo "--bucket is required when parts include upload" >&2
  exit 1
fi

if [[ "$RUN_REGISTER" -eq 1 ]]; then
  if [[ -n "$ADMIN_TOKEN_FILE" ]]; then
    if [[ ! -f "$ADMIN_TOKEN_FILE" ]]; then
      echo "Admin token file not found: $ADMIN_TOKEN_FILE" >&2
      exit 1
    fi
    ADMIN_TOKEN="$(<"$ADMIN_TOKEN_FILE")"
  fi
  if [[ -z "$ADMIN_TOKEN" ]]; then
    echo "Provide --admin-token or --admin-token-file for full parts" >&2
    exit 1
  fi
fi

if [[ "$RUN_ENCODE" -eq 1 ]]; then
  ENCODE_CMD=("$ENCODE_SCRIPT" --input "$INPUT_MP4" --output-dir "$OUTPUT_DIR")
  if [[ -n "$ACTUAL_VIDEO_PATH" ]]; then
    ENCODE_CMD+=(--video-path "$ACTUAL_VIDEO_PATH")
  fi
  if [[ -n "$CLOCKWISE_ROTATE" ]]; then
    ENCODE_CMD+=(--clockwise-rotate "$CLOCKWISE_ROTATE")
  fi
  "${ENCODE_CMD[@]}"
  refresh_metadata_state
elif [[ "$RUN_UPLOAD" -eq 1 ]]; then
  # Even if we skipped encode (future reuse), load whatever metadata exists.
  refresh_metadata_state
fi

if [[ "$RUN_UPLOAD" -eq 1 ]]; then
  if [[ -z "$META_JSON_PATH" ]]; then
    refresh_metadata_state
  fi
  if [[ -z "$ACTUAL_VIDEO_PATH" ]]; then
    ACTUAL_VIDEO_PATH="$(normalise_video_path "" "$SOURCE_BASENAME")"
  fi
  if [[ -z "$ACTUAL_VIDEO_PATH" ]]; then
    echo "Unable to determine video path to derive upload prefix. Supply --video-path." >&2
    exit 1
  fi
  UPLOAD_PREFIX="$(trim_prefix "videos/${ACTUAL_VIDEO_PATH}")"
  UPLOAD_CMD=(
    "$UPLOAD_SCRIPT"
    --input-dir "$OUTPUT_DIR"
    --bucket "$UPLOAD_BUCKET"
    --prefix "$UPLOAD_PREFIX"
    --aws-profile "$AWS_PROFILE"
    --aws-region "$AWS_REGION"
    --upload-mp4 "$UPLOAD_MP4"
  )
  "${UPLOAD_CMD[@]}"
fi

if [[ "$RUN_REGISTER" -eq 1 ]]; then
  LICENSE_JSON="$(find "$OUTPUT_DIR" -maxdepth 1 -name '*_license_material.json' | head -n1 || true)"
  if [[ -z "$LICENSE_JSON" ]]; then
    echo "License JSON not found in $OUTPUT_DIR" >&2
    exit 1
  fi
  REGISTER_CMD=(
    "$REGISTER_SCRIPT"
    --license-json "$LICENSE_JSON"
    --admin-token "$ADMIN_TOKEN"
    --endpoint "$REGISTER_ENDPOINT"
  )
  "${REGISTER_CMD[@]}"
fi
