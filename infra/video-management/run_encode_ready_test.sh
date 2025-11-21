#!/usr/bin/env bash
set -euo pipefail

# --------------------------------------------------
# Integration test – Stage 1 (encode)
# --------------------------------------------------
# Responsibilities:
#   * Run video_management.sh --parts encode-only against the sample MP4
#   * Ensure all expected outputs (MP4/HLS/JSON) exist
#   * Optionally retain temp outputs via --keep-test-outputs true
# Examples:
#   ./run_encode_ready_test.sh
#   ./run_encode_ready_test.sh --keep-test-outputs true

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
VIDEO_MANAGER="$SCRIPT_DIR/video_management.sh"
TEST_VIDEO="$SCRIPT_DIR/resources/test.mp4"

KEEP_TEST_OUTPUTS="false"

usage() {
  cat <<EOF
Usage: $0 [--keep-test-outputs true|false]
Options:
  --keep-test-outputs true|false  Retain local temp outputs (default false)
  -h, --help                      Show this help
Examples:
  ./run_encode_ready_test.sh
  ./run_encode_ready_test.sh --keep-test-outputs true
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --keep-test-outputs)
      if [[ $# -lt 2 ]]; then
        echo "--keep-test-outputs requires true or false" >&2
        exit 1
      fi
      case "$2" in
        true|false) KEEP_TEST_OUTPUTS="$2" ;;
        *)
          echo "Invalid value for --keep-test-outputs: $2" >&2
          exit 1
          ;;
      esac
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

if [ ! -f "$VIDEO_MANAGER" ]; then
  echo "Cannot find video_management.sh at $VIDEO_MANAGER" >&2
  exit 1
fi
if [ ! -f "$TEST_VIDEO" ]; then
  echo "Cannot find test video at $TEST_VIDEO" >&2
  exit 1
fi

TEMP_OUTDIR="$(mktemp -d)"
ENCODER_LOG="$TEMP_OUTDIR/video_management.log"

cleanup() {
  if [[ "$KEEP_TEST_OUTPUTS" != "true" ]]; then
    rm -rf "$TEMP_OUTDIR"
  else
    echo "Keeping test outputs in $TEMP_OUTDIR"
  fi
}
trap cleanup EXIT

echo "Running encode-only integration test via video_management.sh..."
SUCCESS=true
if ! "$VIDEO_MANAGER" \
    --parts encode-only \
    --input-mp4 "$TEST_VIDEO" \
    --output-dir "$TEMP_OUTDIR" >"$ENCODER_LOG" 2>&1; then
  echo "video_management.sh exited with an error. See $ENCODER_LOG" >&2
  SUCCESS=false
fi

EXPECTED_BASENAME="$(basename "${TEST_VIDEO%.*}")"
REQUIRED_FILES=(
  "$TEMP_OUTDIR/${EXPECTED_BASENAME}_sourcefps_h264.mp4"
  "$TEMP_OUTDIR/${EXPECTED_BASENAME}_sourcefps_hevc.mp4"
  "$TEMP_OUTDIR/${EXPECTED_BASENAME}_metadata.json"
  "$TEMP_OUTDIR/${EXPECTED_BASENAME}_license_material.json"
  "$TEMP_OUTDIR/hls/${EXPECTED_BASENAME}_master.m3u8"
)

for f in "${REQUIRED_FILES[@]}"; do
  if [ ! -f "$f" ]; then
    echo "Missing expected output: $f" >&2
    SUCCESS=false
  fi
done

if [ "$SUCCESS" = true ]; then
  echo "encode_ready.sh test PASSED."
else
  echo "encode_ready.sh test FAILED."
  echo "Encoder output log: $ENCODER_LOG"
  if [ -f "$ENCODER_LOG" ]; then
    echo "---- Last 40 lines of encoder log ----"
    tail -n 40 "$ENCODER_LOG"
  fi
  exit 1
fi
