#!/usr/bin/env bash
set -euo pipefail

# -------------------------------------------
# Script to encode videos for GolfBeta!
# chmod +x encode_ready.sh
# run via: ./encode_ready.sh INPUT_FILE.mp4 OUTPUT_DIR
# or:      ./encode_ready.sh INPUT_FILE.mp4 OUTPUT_DIR MY_BUCKET BUCKET_OUTPUT_DIR
# e.g.:    ./encode_ready.sh /Users/tobycc/Downloads/putting1.mp4 /Users/tobycc/Downloads/out golfbeta-eu-north-1-videos-39695a videos/putting/putting1
# -------------------------------------------

# ---- AWS profile/region (constant) ----
AWS_PROFILE="golfbeta"
AWS_REGION="eu-north-1"
AWS_PROFILE_FLAG="--profile ${AWS_PROFILE}"
AWS_REGION_FLAG="--region ${AWS_REGION}"

# Requirements: ffmpeg, ffprobe, jq, awscli (and optionally bc)
need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing dependency: $1"; exit 1; }; }
need ffmpeg; need ffprobe; need jq; need aws

# Quick sanity: confirm creds are available for the chosen profile
if ! aws sts get-caller-identity ${AWS_PROFILE_FLAG} ${AWS_REGION_FLAG} >/dev/null 2>&1; then
  echo "AWS SSO not logged in for profile '${AWS_PROFILE}'. Run: aws sso login --profile ${AWS_PROFILE}"
  exit 1
fi

if [ $# -lt 2 ]; then
  echo "Usage: $0 INPUT.mp4 OUTPUT_DIR [S3_BUCKET] [S3_PREFIX]"
  echo "Example (local only): $0 swing.mp4 out/lesson-abc"
  echo "Example (with upload): $0 swing.mp4 out/lesson-abc my-bucket analysis/lesson-abc"
  exit 1
fi

IN="$1"
OUTDIR="$2"
S3_BUCKET="${3:-}"
S3_PREFIX="${4:-}"     # e.g. analysis/lesson-abc
mkdir -p "$OUTDIR"

BASENAME="$(basename "${IN%.*}")"
H264_OUT="$OUTDIR/${BASENAME}_sourcefps_h264.mp4"
HEVC_OUT="$OUTDIR/${BASENAME}_sourcefps_hevc.mp4"
META_JSON="$OUTDIR/${BASENAME}_metadata.json"

echo "==> Probing source..."
PROBE_JSON="$(ffprobe -v error -select_streams v:0 \
  -show_entries stream=codec_name,avg_frame_rate,width,height \
  -of json "$IN")"

FPS_RAW="$(echo "$PROBE_JSON" | jq -r '.streams[0].avg_frame_rate')"
WIDTH="$(echo "$PROBE_JSON" | jq -r '.streams[0].width')"
HEIGHT="$(echo "$PROBE_JSON" | jq -r '.streams[0].height')"
SRC_CODEC="$(echo "$PROBE_JSON" | jq -r '.streams[0].codec_name')"

# Fraction -> decimal (if bc available)
if command -v bc >/dev/null 2>&1; then
  NUM="${FPS_RAW%/*}"; DEN="${FPS_RAW#*/}"
  if [ "$DEN" = "$FPS_RAW" ]; then FPS_DEC="$FPS_RAW"; else FPS_DEC="$(echo "scale=6; $NUM/$DEN" | bc -l)"; fi
else
  FPS_DEC="$FPS_RAW"
fi

# Round to nearest whole number
FPS_ROUND="$(printf "%.0f" "$FPS_DEC")"

# GOP ≈ 0.1s
GOP_FRAMES="12"
if command -v bc >/dev/null 2>&1 && [[ "$FPS_ROUND" =~ ^[0-9.]+$ ]]; then
  GOP_FRAMES="$(printf "%.0f" "$(echo "$FPS_ROUND * 0.1" | bc -l)")"
  [ "$GOP_FRAMES" -lt 6 ] && GOP_FRAMES=6
fi

echo "Source: ${WIDTH}x${HEIGHT}, fps(raw)=${FPS_RAW}, fps(dec)=${FPS_DEC}, fps(round)=${FPS_ROUND}, codec=${SRC_CODEC}"
echo "Using GOP (frames): ${GOP_FRAMES}"
echo

############################################
# Encode H.264 (1080x1920 vertical)
############################################
echo "==> Encoding H.264 (1080x1920 vertical, ${FPS_ROUND}fps) -> $H264_OUT"

# -vf "transpose=1,scale... for 90degrees clockwise transpose (2 for anticlockwise)
ffmpeg -y -i "$IN" \
  -vf "transpose=1,fps=${FPS_ROUND},scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2,setsar=1" \
  -r "$FPS_ROUND" -vsync cfr \
  -c:v libx264 -profile:v high -pix_fmt yuv420p \
  -preset slow -crf 20 \
  -g "${GOP_FRAMES}" -keyint_min "${GOP_FRAMES}" -sc_threshold 0 \
  -x264-params "no-open-gop=1" \
  -movflags +faststart \
  -c:a aac -b:a 128k \
  "$H264_OUT"

############################################
# Encode HEVC (1080x1920 vertical)
############################################
echo "==> Encoding HEVC (1080x1920 vertical, ${FPS_ROUND}fps) -> $HEVC_OUT"

# -vf "transpose=1,scale... for 90degrees clockwise transpose (2 for anticlockwise)
ffmpeg -y -i "$IN" \
  -vf "transpose=1,fps=${FPS_ROUND},scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2,setsar=1" \
  -r "$FPS_ROUND" -vsync cfr \
  -c:v libx265 -tag:v hvc1 -pix_fmt yuv420p \
  -preset slow -crf 25 \
  -x265-params "keyint=${GOP_FRAMES}:min-keyint=${GOP_FRAMES}:scenecut=0:open-gop=0" \
  -movflags +faststart \
  -c:a aac -b:a 128k \
  "$HEVC_OUT"

############################################
# Emit single metadata.json for the pair
############################################
probe_out () {
  local FILE="$1"
  ffprobe -v error -select_streams v:0 \
    -show_entries stream=codec_name,avg_frame_rate,width,height \
    -of json "$FILE"
}

H264_INFO="$(probe_out "$H264_OUT")"
HEVC_INFO="$(probe_out "$HEVC_OUT")"

echo "==> Writing metadata -> $META_JSON"
jq -n --arg in_name "$IN" \
      --arg src_codec "$SRC_CODEC" \
      --arg fps_round "$FPS_ROUND" \
      --arg width "$WIDTH" \
      --arg height "$HEIGHT" \
      --arg gop "$GOP_FRAMES" \
      --argjson h264 "$H264_INFO" \
      --argjson hevc "$HEVC_INFO" \
'
  {
    source: {
      file: $in_name,
      codec: $src_codec,
      width: ($width|tonumber),
      height: ($height|tonumber),
      fps_raw: $fps_round,
      fps: ($fps_round|tonumber)
    },
    params: {
      gop_frames: ($gop|tonumber),
      fast_start: true,
      cfr: true
    },
    outputs: {
      h264: {
        filename: "'"${BASENAME}_sourcefps_h264.mp4"'",
        codec: $h264.streams[0].codec_name,
        width: ($h264.streams[0].width),
        height: ($h264.streams[0].height),
        fps_raw: $fps_round,
        fps: ($fps_round|tonumber)
      },
      hevc: {
        filename: "'"${BASENAME}_sourcefps_hevc.mp4"'",
        codec: $hevc.streams[0].codec_name,
        width: ($hevc.streams[0].width),
        height: ($hevc.streams[0].height),
        fps_raw: $fps_round,
        fps: ($fps_round|tonumber)
      }
    }
  }
' > "$META_JSON"

echo
ls -lh "$H264_OUT" "$HEVC_OUT" "$META_JSON"
echo

read -r -p "Ready for upload. Are you happy with these files? (y/n) " ANSW
if [[ ! "$ANSW" =~ ^[Yy]$ ]]; then
  echo "Okay — not uploading. You can re-run after tweaking settings."
  exit 0
fi

# If you didn't provide bucket/prefix, ask now
if [ -z "${S3_BUCKET}" ]; then
  read -r -p "Enter S3 bucket name: " S3_BUCKET
fi
if [ -z "${S3_PREFIX}" ]; then
  read -r -p "Enter S3 destination prefix (e.g. analysis/lesson-abc): " S3_PREFIX
fi

DEST_URI="s3://${S3_BUCKET}/${S3_PREFIX}"
echo "==> Uploading to ${DEST_URI}"
echo

upload_one () {
  local SRC="$1"
  local NAME="$(basename "$SRC")"
  local CT="application/octet-stream"
  case "$NAME" in
    *.mp4) CT="video/mp4" ;;
    *.json) CT="application/json" ;;
  esac
  aws s3 cp "$SRC" "${DEST_URI}/${NAME}" \
    --content-type "$CT" \
    --cache-control "public,max-age=31536000,immutable" \
    --sse AES256 \
    --no-progress \
    ${AWS_PROFILE_FLAG} ${AWS_REGION_FLAG}
}

upload_one "$H264_OUT"
upload_one "$HEVC_OUT"
upload_one "$META_JSON"

echo
echo "Upload complete:"
aws s3 ls "${DEST_URI}/" --human-readable --summarize ${AWS_PROFILE_FLAG} ${AWS_REGION_FLAG}
