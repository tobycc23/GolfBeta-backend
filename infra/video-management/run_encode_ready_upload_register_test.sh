#!/usr/bin/env bash
set -euo pipefail

# --------------------------------------------------
# Integration test – Full pipeline (encode + upload + register)
# --------------------------------------------------
# Responsibilities:
#   * Run video_management.sh --parts full with the sample MP4
#   * Leave artifacts in the real videos bucket/prefix for verification
#   * Ensure video_asset row exists (and optionally clean it up)
# Examples:
#   ./run_encode_ready_upload_register_test.sh --bucket golfbeta-eu-north-1-videos-39695a --prefix videos/test/e2e --admin-token-file ~/token.txt
#   ./run_encode_ready_upload_register_test.sh --bucket ... --prefix ... --admin-token TOKEN --keep-test-outputs true

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
VIDEO_MANAGER="$SCRIPT_DIR/video_management.sh"
TEST_VIDEO="$SCRIPT_DIR/resources/test.mp4"

DEFAULT_BUCKET="golfbeta-eu-north-1-videos-39695a"
RUN_ID="$(date +%Y%m%d%H%M%S)"
DEFAULT_PREFIX="videos/test/e2e-${RUN_ID}"
DEFAULT_REGISTER_ENDPOINT="http://localhost:8080/admin/video-assets"

TARGET_BUCKET="$DEFAULT_BUCKET"
TARGET_PREFIX="$DEFAULT_PREFIX"
AWS_CLI_PROFILE="${AWS_PROFILE:-golfbeta}"
AWS_CLI_REGION="${AWS_REGION:-eu-north-1}"
REGISTER_ENDPOINT="$DEFAULT_REGISTER_ENDPOINT"

ADMIN_TOKEN=""
ADMIN_TOKEN_FILE=""
KEEP_REMOTE="false"
KEEP_TEST_OUTPUTS="false"
KEEP_DB_ROW="false"
UPLOAD_SUCCEEDED="false"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-golfbeta}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"

usage() {
  cat <<EOF
Usage: $0 [options]
Options:
  --bucket NAME             S3 bucket (default ${DEFAULT_BUCKET})
  --prefix PATH             S3 prefix (default ${DEFAULT_PREFIX})
  --aws-profile NAME        AWS CLI profile (default ${AWS_CLI_PROFILE})
  --aws-region NAME         AWS region (default ${AWS_CLI_REGION})
  --register-endpoint URL   Backend endpoint (default ${DEFAULT_REGISTER_ENDPOINT})
  --admin-token TOKEN       Firebase admin token (required)
  --admin-token-file PATH   Read admin token from file
  --db-host HOST            Postgres host (default ${DB_HOST})
  --db-port PORT            Postgres port (default ${DB_PORT})
  --db-name NAME            Postgres DB (default ${DB_NAME})
  --db-user USER            Postgres user (default ${DB_USER})
  --db-password PASS        Postgres password (default env/postgres)
  --keep-db-row true|false  Leave inserted DB row (default false)
  --keep-test-outputs true|false  Keep local encode outputs (default false)
  --keep-remote true|false  Leave uploaded objects in S3 (default false)
  -h, --help                Show this help
Examples:
  ./run_encode_ready_upload_register_test.sh --bucket golfbeta-eu-north-1-videos-39695a --prefix videos/test/demo --admin-token-file ~/token.txt
  ./run_encode_ready_upload_register_test.sh --bucket ... --prefix ... --admin-token TOKEN --keep-test-outputs true --keep-remote true
EOF
}

trim_prefix() {
  local val="$1"
  val="${val#/}"
  val="${val%/}"
  echo "$val"
}

aws_cli() {
  aws --profile "$AWS_CLI_PROFILE" --region "$AWS_CLI_REGION" "$@"
}

escape_sql_literal() {
  local input="$1"
  input="${input//\'/''}"
  printf '%s' "$input"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --bucket) TARGET_BUCKET="$2"; shift 2 ;;
    --prefix) TARGET_PREFIX="$2"; shift 2 ;;
    --aws-profile) AWS_CLI_PROFILE="$2"; shift 2 ;;
    --aws-region) AWS_CLI_REGION="$2"; shift 2 ;;
    --register-endpoint) REGISTER_ENDPOINT="$2"; shift 2 ;;
    --admin-token) ADMIN_TOKEN="$2"; shift 2 ;;
    --admin-token-file) ADMIN_TOKEN_FILE="$2"; shift 2 ;;
    --db-host) DB_HOST="$2"; shift 2 ;;
    --db-port) DB_PORT="$2"; shift 2 ;;
    --db-name) DB_NAME="$2"; shift 2 ;;
    --db-user) DB_USER="$2"; shift 2 ;;
    --db-password) DB_PASSWORD="$2"; shift 2 ;;
    --keep-db-row) KEEP_DB_ROW="$2"; shift 2 ;;
    --keep-test-outputs) KEEP_TEST_OUTPUTS="$2"; shift 2 ;;
    --keep-remote) KEEP_REMOTE="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
done

case "$KEEP_DB_ROW" in true|false) ;; *) echo "--keep-db-row must be true or false" >&2; exit 1 ;; esac
case "$KEEP_TEST_OUTPUTS" in true|false) ;; *) echo "--keep-test-outputs must be true or false" >&2; exit 1 ;; esac
case "$KEEP_REMOTE" in true|false) ;; *) echo "--keep-remote must be true or false" >&2; exit 1 ;; esac

if [[ -n "$ADMIN_TOKEN_FILE" ]]; then
  if [[ ! -f "$ADMIN_TOKEN_FILE" ]]; then
    echo "Admin token file not found: $ADMIN_TOKEN_FILE" >&2
    exit 1
  fi
  ADMIN_TOKEN="$(<"$ADMIN_TOKEN_FILE")"
fi

if [[ -z "$ADMIN_TOKEN" ]]; then
  echo "--admin-token (or --admin-token-file) is required for the register stage." >&2
  exit 1
fi

if [ ! -f "$VIDEO_MANAGER" ]; then
  echo "Cannot find video_management.sh at $VIDEO_MANAGER" >&2
  exit 1
fi
if [ ! -f "$TEST_VIDEO" ]; then
  echo "Cannot find test video at $TEST_VIDEO" >&2
  exit 1
fi

REMOTE_PREFIX="$(trim_prefix "$TARGET_PREFIX")"
TEMP_OUTDIR="$(mktemp -d)"
VIDEO_MGMT_LOG="$TEMP_OUTDIR/video_management.log"
VIDEO_PATH=""

cleanup() {
  if [[ "$UPLOAD_SUCCEEDED" = "true" && "$KEEP_REMOTE" != "true" && -n "$REMOTE_PREFIX" ]]; then
    echo "Cleaning up remote artifacts under s3://${TARGET_BUCKET}/${REMOTE_PREFIX}"
    aws_cli s3 rm "s3://${TARGET_BUCKET}/${REMOTE_PREFIX}" --recursive >/dev/null 2>&1 || \
      echo "Warning: failed to delete remote artifacts under ${REMOTE_PREFIX}" >&2
  elif [[ "$UPLOAD_SUCCEEDED" = "true" && "$KEEP_REMOTE" = "true" ]]; then
    echo "Remote artifacts retained at s3://${TARGET_BUCKET}/${REMOTE_PREFIX}"
  fi

  if [[ "$KEEP_TEST_OUTPUTS" = "true" ]]; then
    echo "Local outputs retained in $TEMP_OUTDIR"
  else
    rm -rf "$TEMP_OUTDIR"
  fi

  if [[ -n "$VIDEO_PATH" && "$KEEP_DB_ROW" != "true" ]]; then
    if ! PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
      -c "DELETE FROM video_asset WHERE video_path = '$(escape_sql_literal "$VIDEO_PATH")';" >/dev/null; then
      echo "Warning: failed to delete video_asset row for ${VIDEO_PATH}. Remove manually if needed." >&2
    fi
  fi
}
trap cleanup EXIT

echo "Running video_management.sh (full pipeline)..."
if ! "$VIDEO_MANAGER" \
    --parts full \
    --input-mp4 "$TEST_VIDEO" \
    --output-dir "$TEMP_OUTDIR" \
    --bucket "$TARGET_BUCKET" \
    --prefix "$REMOTE_PREFIX" \
    --aws-profile "$AWS_CLI_PROFILE" \
    --aws-region "$AWS_CLI_REGION" \
    --upload-mp4 false \
    --admin-token "$ADMIN_TOKEN" \
    --register-endpoint "$REGISTER_ENDPOINT" >"$VIDEO_MGMT_LOG" 2>&1; then
  echo "video_management.sh exited with an error. See $VIDEO_MGMT_LOG" >&2
  exit 1
fi
UPLOAD_SUCCEEDED="true"

LICENSE_JSON="$(find "$TEMP_OUTDIR" -maxdepth 1 -name '*_license_material.json' | head -n1 || true)"
if [[ -z "$LICENSE_JSON" ]]; then
  echo "License JSON missing at $TEMP_OUTDIR" >&2
  exit 1
fi

VIDEO_PATH="$(jq -r '.video_path' "$LICENSE_JSON")"
EXPECTED_KEY_HEX="$(jq -r '.key_hex' "$LICENSE_JSON")"
EXPECTED_KEY_BASE64="$(jq -r '.key_base64' "$LICENSE_JSON")"

if [[ -z "$VIDEO_PATH" || "$VIDEO_PATH" == "null" ]]; then
  echo "Unable to parse video_path from $LICENSE_JSON" >&2
  exit 1
fi

DB_ROW=$(PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
  -At -F '|' -c "SELECT key_hex, key_base64 FROM video_asset WHERE video_path = '$(escape_sql_literal "$VIDEO_PATH")';" || true)

if [[ -z "$DB_ROW" ]]; then
  echo "video_asset row for ${VIDEO_PATH} not found in Postgres ${DB_HOST}:${DB_PORT}/${DB_NAME}" >&2
  exit 1
fi

IFS='|' read -r DB_KEY_HEX DB_KEY_BASE64 <<<"$DB_ROW"

if [[ "$DB_KEY_HEX" != "$EXPECTED_KEY_HEX" ]]; then
  echo "key_hex mismatch for ${VIDEO_PATH}. Expected ${EXPECTED_KEY_HEX}, found ${DB_KEY_HEX}" >&2
  exit 1
fi
if [[ "$DB_KEY_BASE64" != "$EXPECTED_KEY_BASE64" ]]; then
  echo "key_base64 mismatch for ${VIDEO_PATH}. Expected ${EXPECTED_KEY_BASE64}, found ${DB_KEY_BASE64}" >&2
  exit 1
fi

echo "encode_ready.sh encode/upload/register test PASSED."
