#!/usr/bin/env bash
set -euo pipefail

# --------------------------------------------------
# Integration test – Stage 2 (encode + upload)
# --------------------------------------------------
# Responsibilities:
#   * Run video_management.sh --parts encode-upload with the sample MP4
#   * Push artifacts to the test bucket/prefix and verify expected objects
#   * Clean up remote objects unless --keep-remote true
# Examples:
#   ./run_encode_ready_upload_test.sh --bucket golfbeta-eu-north-1-videos-39695a --prefix videos/test/demo
#   ./run_encode_ready_upload_test.sh --bucket ... --prefix ... --keep-test-outputs true

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
VIDEO_MANAGER="$SCRIPT_DIR/video_management.sh"
TEST_VIDEO="$SCRIPT_DIR/resources/test.mp4"

DEFAULT_BUCKET="golfbeta-eu-north-1-videos-39695a"
RUN_ID="$(date +%Y%m%d%H%M%S)"
DEFAULT_PREFIX="videos/test/test-${RUN_ID}"

KEEP_TEST_OUTPUTS="false"
KEEP_REMOTE="false"
TARGET_BUCKET="$DEFAULT_BUCKET"
TARGET_PREFIX="$DEFAULT_PREFIX"
AWS_CLI_PROFILE="${AWS_PROFILE:-golfbeta}"
AWS_CLI_REGION="${AWS_REGION:-eu-north-1}"
SUMMARY_FILE=""

usage() {
  cat <<EOF
Usage: $0 [options]
Options:
  --bucket NAME             Override target S3 bucket (default ${DEFAULT_BUCKET})
  --prefix PATH             Override target prefix (default ${DEFAULT_PREFIX})
  --aws-profile NAME        AWS CLI profile (default ${AWS_CLI_PROFILE})
  --aws-region NAME         AWS region (default ${AWS_CLI_REGION})
  --keep-test-outputs true|false  Keep local temp outputs (default false)
  --keep-remote true|false  Keep uploaded S3 artifacts (default false)
  --summary-file PATH      Write run metadata (temp dir, remote prefix, etc.) to PATH so other scripts can reuse it
  -h, --help                Show this help
Examples:
  ./run_encode_ready_upload_test.sh --bucket golfbeta-eu-north-1-videos-39695a --prefix videos/test/demo
  ./run_encode_ready_upload_test.sh --bucket ... --prefix ... --keep-test-outputs true --keep-remote true
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --bucket)
      TARGET_BUCKET="$2"; shift 2 ;;
    --prefix)
      TARGET_PREFIX="$2"; shift 2 ;;
    --aws-profile)
      AWS_CLI_PROFILE="$2"; shift 2 ;;
    --aws-region)
      AWS_CLI_REGION="$2"; shift 2 ;;
    --keep-test-outputs)
      KEEP_TEST_OUTPUTS="$2"; shift 2 ;;
    --keep-remote)
      KEEP_REMOTE="$2"; shift 2 ;;
    --summary-file)
      SUMMARY_FILE="$2"; shift 2 ;;
    -h|--help)
      usage; exit 0 ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

case "$KEEP_TEST_OUTPUTS" in
  true|false) ;;
  *) echo "--keep-test-outputs must be true or false" >&2; exit 1 ;;
esac
case "$KEEP_REMOTE" in
  true|false) ;;
  *) echo "--keep-remote must be true or false" >&2; exit 1 ;;
esac
if [[ -n "$SUMMARY_FILE" ]]; then
  SUMMARY_FILE="$(cd "$(dirname "$SUMMARY_FILE")" && pwd)/$(basename "$SUMMARY_FILE")"
fi

if [ ! -f "$VIDEO_MANAGER" ]; then
  echo "Cannot find video_management.sh at $VIDEO_MANAGER" >&2
  exit 1
fi
if [ ! -f "$TEST_VIDEO" ]; then
  echo "Cannot find test video at $TEST_VIDEO" >&2
  exit 1
fi

AWS_ARGS=(--profile "$AWS_CLI_PROFILE" --region "$AWS_CLI_REGION")

trim_prefix() {
  local val="$1"
  val="${val#/}"
  val="${val%/}"
  echo "$val"
}

join_key() {
  local prefix="$1"
  local suffix="$2"
  local clean_suffix="${suffix#/}"
  if [ -z "$prefix" ]; then
    echo "$clean_suffix"
  else
    echo "${prefix}/${clean_suffix}"
  fi
}

aws_cli() {
  aws "${AWS_ARGS[@]}" "$@"
}

REMOTE_PREFIX="$(trim_prefix "$TARGET_PREFIX")"
TEMP_OUTDIR="$(mktemp -d)"
VIDEO_MGMT_LOG="$TEMP_OUTDIR/video_management.log"
TEST_PASSED="false"
UPLOAD_SUCCEEDED="false"

cleanup() {
  if [[ "$TEST_PASSED" != "true" && "$KEEP_TEST_OUTPUTS" = "true" ]]; then
    echo "Keeping test outputs in $TEMP_OUTDIR for inspection"
  elif [[ "$KEEP_TEST_OUTPUTS" != "true" ]]; then
    rm -rf "$TEMP_OUTDIR"
  else
    echo "Local outputs retained in $TEMP_OUTDIR"
  fi

  if [[ "$UPLOAD_SUCCEEDED" = "true" && "$KEEP_REMOTE" != "true" && -n "$REMOTE_PREFIX" && "$TEST_PASSED" = "true" ]]; then
    echo "Cleaning up remote artifacts under s3://${TARGET_BUCKET}/${REMOTE_PREFIX}"
    aws_cli s3 rm "s3://${TARGET_BUCKET}/${REMOTE_PREFIX}" --recursive >/dev/null 2>&1 || \
      echo "Warning: failed to delete remote artifacts under ${REMOTE_PREFIX}" >&2
  elif [[ "$UPLOAD_SUCCEEDED" = "true" && "$KEEP_REMOTE" != "true" && "$TEST_PASSED" != "true" ]]; then
    echo "Remote artifacts preserved (test failed). Delete manually when done."
  fi
}
trap cleanup EXIT

echo "Running video_management.sh (encode + upload)..."
if ! "$VIDEO_MANAGER" \
    --parts encode-upload \
    --input-mp4 "$TEST_VIDEO" \
    --output-dir "$TEMP_OUTDIR" \
    --bucket "$TARGET_BUCKET" \
    --prefix "$REMOTE_PREFIX" \
    --aws-profile "$AWS_CLI_PROFILE" \
    --aws-region "$AWS_CLI_REGION" \
    --upload-mp4 false >"$VIDEO_MGMT_LOG" 2>&1; then
  echo "video_management.sh exited with an error. See $VIDEO_MGMT_LOG" >&2
  exit 1
fi
UPLOAD_SUCCEEDED="true"

EXPECTED_BASENAME="$(basename "${TEST_VIDEO%.*}")"
REQUIRED_LOCAL=(
  "$TEMP_OUTDIR/${EXPECTED_BASENAME}_sourcefps_h264.mp4"
  "$TEMP_OUTDIR/${EXPECTED_BASENAME}_sourcefps_hevc.mp4"
  "$TEMP_OUTDIR/${EXPECTED_BASENAME}_metadata.json"
  "$TEMP_OUTDIR/${EXPECTED_BASENAME}_license_material.json"
  "$TEMP_OUTDIR/hls/${EXPECTED_BASENAME}_master.m3u8"
)

for f in "${REQUIRED_LOCAL[@]}"; do
  if [ ! -f "$f" ]; then
    echo "Missing expected local output: $f" >&2
    exit 1
  fi
done

metadata_key="$(join_key "$REMOTE_PREFIX" "${EXPECTED_BASENAME}_metadata.json")"
master_key="$(join_key "$REMOTE_PREFIX" "hls/${EXPECTED_BASENAME}_master.m3u8")"
h264_mp4_key="$(join_key "$REMOTE_PREFIX" "${EXPECTED_BASENAME}_sourcefps_h264.mp4")"
hevc_mp4_key="$(join_key "$REMOTE_PREFIX" "${EXPECTED_BASENAME}_sourcefps_hevc.mp4")"

check_remote_exists() {
  local key="$1"
  if ! aws_cli s3api head-object --bucket "$TARGET_BUCKET" --key "$key" >/dev/null 2>&1; then
    echo "Expected remote object missing: s3://${TARGET_BUCKET}/${key}" >&2
    return 1
  fi
}

if ! check_remote_exists "$metadata_key"; then
  exit 1
fi
if ! check_remote_exists "$master_key"; then
  exit 1
fi

if aws_cli s3api head-object --bucket "$TARGET_BUCKET" --key "$h264_mp4_key" >/dev/null 2>&1; then
  echo "H.264 master MP4 should not have been uploaded (found s3://${TARGET_BUCKET}/${h264_mp4_key})." >&2
  exit 1
fi
if aws_cli s3api head-object --bucket "$TARGET_BUCKET" --key "$hevc_mp4_key" >/dev/null 2>&1; then
  echo "HEVC master MP4 should not have been uploaded (found s3://${TARGET_BUCKET}/${hevc_mp4_key})." >&2
  exit 1
fi

if [[ -n "$SUMMARY_FILE" ]]; then
  # Expose a simple env-file so other scripts (e.g., full e2e test) can source details
  cat > "$SUMMARY_FILE" <<EOF
UPLOAD_TEST_TEMP_OUTDIR=$TEMP_OUTDIR
UPLOAD_TEST_REMOTE_PREFIX=$REMOTE_PREFIX
UPLOAD_TEST_TARGET_BUCKET=$TARGET_BUCKET
UPLOAD_TEST_AWS_PROFILE=$AWS_CLI_PROFILE
UPLOAD_TEST_AWS_REGION=$AWS_CLI_REGION
UPLOAD_TEST_EXPECTED_BASENAME=$EXPECTED_BASENAME
UPLOAD_TEST_ENCODER_LOG=$VIDEO_MGMT_LOG
EOF
fi

TEST_PASSED="true"
echo "encode_ready.sh live upload test PASSED."
