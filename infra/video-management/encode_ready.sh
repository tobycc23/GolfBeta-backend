#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# --------------------------------------------------
# Video management pipeline – Stage 1 (encode)
# --------------------------------------------------
# Responsibilities:
#   1. Transcode the source MP4 into 1080x1920 H.264 + HEVC masters
#   2. Package both codecs as AES-128 encrypted HLS playlists/segments
#   3. Emit metadata + license JSON for later stages
# Uploading and registration are handled separately by
# upload_ready.sh and register_ready.sh (or via video_management.sh).
# Example:
#   ./encode_ready.sh --input swing.mp4 --output-dir out/lesson-abc

# ---- Defaults / tunables ----
DEFAULT_KEY_URI_BASE="http://localhost:8080/user/video/license/key"
DEFAULT_SEGMENT_SECONDS="6"
KEY_URI_BASE="${KEY_URI_BASE:-$DEFAULT_KEY_URI_BASE}"
HLS_SEGMENT_SECONDS="${HLS_SEGMENT_SECONDS:-$DEFAULT_SEGMENT_SECONDS}"

# Requirements: ffmpeg, ffprobe, jq, awscli, openssl, python3 (and optionally bc/xxd)
need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing dependency: $1"; exit 1; }; }
need ffmpeg; need ffprobe; need jq; need openssl; need python3; need xxd

print_usage() {
  cat <<'EOF'
Usage: ./encode_ready.sh --input PATH --output-dir PATH [options]
Options:
  --input PATH             Source MP4 (required)
  --output-dir PATH        Directory for local artifacts (required)
  --video-path PATH        Logical video path (defaults to basename)
  --clockwise-rotate DEG   Rotate source clockwise by 0/90/180/270 before scaling
  -h, --help               Show this help
Examples:
  ./encode_ready.sh --input swing.mp4 --output-dir out/lesson-abc
  ./encode_ready.sh --input swing.mp4 --output-dir out/lesson-abc --video-path analysis/lesson-abc --clockwise-rotate 90
EOF
}

# urlencode: percent-encode an arbitrary string for use in query params
urlencode() {
  python3 - <<'PY' "$1"
import urllib.parse, sys
print(urllib.parse.quote(sys.argv[1], safe=''))
PY
}

# write_key_info_file: produce the ffmpeg-compatible .keyinfo file (URI, key path, IV).
# ffmpeg reads this file to know which key+IV should be used for a given variant.
write_key_info_file() {
  local uri="$1"
  local info_file="$2"
  local iv_hex="$3"
  {
    echo "$uri"
    echo "$HLS_KEY_FILE"
    echo "$iv_hex"
  } > "$info_file"
}

# generate_hls_variant: run ffmpeg to create an encrypted HLS playlist for a codec
# (copying streams, applying the IV/key info, and writing AES-128 segments).
generate_hls_variant() {
  local codec="$1"
  local input="$2"
  local playlist="$3"
  local keyinfo="$4"
  local segment_dir
  segment_dir="$(dirname "$playlist")"
  mkdir -p "$segment_dir"
  local segment_pattern="${segment_dir}/${BASENAME}_${codec}_%05d.ts"

  ffmpeg -nostdin -y -i "$input" \
    -c copy -map 0 \
    -hls_time "$HLS_SEGMENT_SECONDS" \
    -hls_playlist_type vod \
    -hls_flags independent_segments \
    -hls_segment_filename "$segment_pattern" \
    -hls_key_info_file "$keyinfo" \
    "$playlist"
}

# probe_bitrate: attempt to discover bitrate for master manifest metadata so the
# generated master playlist advertises realistic bandwidth numbers.
probe_bitrate() {
  local file="$1"
  local rate
  rate="$(ffprobe -v error -select_streams v:0 -show_entries stream=bit_rate -of default=noprint_wrappers=1:nokey=1 "$file" | head -n1 || true)"
  if [ -z "$rate" ] || [ "$rate" = "N/A" ]; then
    rate="$(ffprobe -v error -show_entries format=bit_rate -of default=noprint_wrappers=1:nokey=1 "$file" | head -n1 || true)"
  fi
  if [[ ! "$rate" =~ ^[0-9]+$ ]]; then
    rate=8000000
  fi
  echo "$rate"
}

# normalise_video_path: derive the logical video path used by backend/mobile clients.
# The video path doubles as both the DB key and the S3 prefix used later.
normalise_video_path() {
  local candidate="$1"
  local fallback="$2"
  local trimmed="${candidate:-$fallback}"
  trimmed="${trimmed#/}"
  trimmed="${trimmed%/}"
  if [[ "$trimmed" == videos/* ]]; then
    trimmed="${trimmed#videos/}"
  fi
  if [ -z "$trimmed" ]; then
    trimmed="$fallback"
  fi
  echo "$trimmed"
}

# initialise_paths: compute all derived output paths (MP4, HLS, key files, JSON).
# This keeps the naming scheme consistent across encode/upload/register stages.
initialise_paths() {
  local input_path="$1"
  local requested_outdir="$2"
  local s3_prefix="$3"

  mkdir -p "$requested_outdir"
  OUTDIR="$(cd "$requested_outdir" && pwd)"

  local source_basename
  source_basename="$(basename "${input_path%.*}")"
  VIDEO_PATH_FOR_LICENSE="$(normalise_video_path "${s3_prefix:-$source_basename}" "$source_basename")"
  local derived_basename="${VIDEO_PATH_FOR_LICENSE##*/}"
  if [[ -z "$derived_basename" ]]; then
    derived_basename="$source_basename"
  fi
  BASENAME="$derived_basename"

  H264_OUT="$OUTDIR/${BASENAME}_sourcefps_h264.mp4"
  HEVC_OUT="$OUTDIR/${BASENAME}_sourcefps_hevc.mp4"
  META_JSON="$OUTDIR/${BASENAME}_metadata.json"

  HLS_DIR="$OUTDIR/hls"
  H264_HLS_DIR="$HLS_DIR/h264"
  HEVC_HLS_DIR="$HLS_DIR/hevc"
  H264_PLAYLIST="$H264_HLS_DIR/${BASENAME}_h264.m3u8"
  HEVC_PLAYLIST="$HEVC_HLS_DIR/${BASENAME}_hevc.m3u8"
  # Master manifest advertises both codec variants; players/readers start here.
  MASTER_PLAYLIST="$HLS_DIR/${BASENAME}_master.m3u8"

  ENCODED_VIDEO_PATH="$(urlencode "$VIDEO_PATH_FOR_LICENSE")"
  KEY_URI_H264="${KEY_URI_BASE}?videoPath=${ENCODED_VIDEO_PATH}&codec=h264"
  KEY_URI_HEVC="${KEY_URI_BASE}?videoPath=${ENCODED_VIDEO_PATH}&codec=hevc"

  HLS_KEY_FILE="$OUTDIR/${BASENAME}_hls.key"
  H264_KEYINFO_FILE="$OUTDIR/${BASENAME}_h264.keyinfo"
  HEVC_KEYINFO_FILE="$OUTDIR/${BASENAME}_hevc.keyinfo"
  LICENSE_PAYLOAD_JSON="$OUTDIR/${BASENAME}_license_material.json"
}

INPUT_MP4=""
OUTPUT_DIR=""
VIDEO_PATH_HINT=""
ROTATE_DEGREES="0"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --input)
      INPUT_MP4="$2"; shift 2 ;;
    --output-dir)
      OUTPUT_DIR="$2"; shift 2 ;;
    --video-path)
      VIDEO_PATH_HINT="$2"; shift 2 ;;
    --clockwise-rotate)
      if [[ $# -lt 2 ]]; then
        echo "--clockwise-rotate requires a degree value (0/90/180/270)" >&2
        exit 1
      fi
      ROTATE_DEGREES="$2"
      shift 2 ;;
    -h|--help)
      print_usage
      exit 0 ;;
    *)
      echo "Unknown option: $1" >&2
      print_usage
      exit 1 ;;
  esac
done

if [[ -z "$INPUT_MP4" || -z "$OUTPUT_DIR" ]]; then
  echo "--input and --output-dir are required." >&2
  print_usage
  exit 1
fi

if [[ ! -f "$INPUT_MP4" ]]; then
  echo "Input file not found: $INPUT_MP4" >&2
  exit 1
fi

IN="$INPUT_MP4"

case "$ROTATE_DEGREES" in
  ""|"0")
    ROTATE_FILTER_PREFIX=""
    ;;
  "90")
    ROTATE_FILTER_PREFIX="transpose=1,"
    ;;
  "180")
    ROTATE_FILTER_PREFIX="transpose=1,transpose=1,"
    ;;
  "270")
    ROTATE_FILTER_PREFIX="transpose=2,"
    ;;
  *)
    echo "Unsupported rotation angle: $ROTATE_DEGREES (allowed: 0,90,180,270)"
    exit 1
    ;;
esac

initialise_paths "$INPUT_MP4" "$OUTPUT_DIR" "$VIDEO_PATH_HINT"

# --- Inspect source file ---
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
VIDEO_FILTER_CHAIN="${ROTATE_FILTER_PREFIX}fps=${FPS_ROUND},scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2,setsar=1"

# --- Encode H.264 output ---
echo "==> Encoding H.264 (1080x1920 vertical, ${FPS_ROUND}fps) -> $H264_OUT"

ffmpeg -nostdin -y -i "$IN" \
  -vf "$VIDEO_FILTER_CHAIN" \
  -r "$FPS_ROUND" -vsync cfr \
  -c:v libx264 -profile:v high -pix_fmt yuv420p \
  -preset slow -crf 20 \
  -g "${GOP_FRAMES}" -keyint_min "${GOP_FRAMES}" -sc_threshold 0 \
  -x264-params "no-open-gop=1" \
  -movflags +faststart \
  -c:a aac -b:a 128k \
  "$H264_OUT"

# --- Encode HEVC output ---
echo "==> Encoding HEVC (1080x1920 vertical, ${FPS_ROUND}fps) -> $HEVC_OUT"

ffmpeg -nostdin -y -i "$IN" \
  -vf "$VIDEO_FILTER_CHAIN" \
  -r "$FPS_ROUND" -vsync cfr \
  -c:v libx265 -tag:v hvc1 -pix_fmt yuv420p \
  -preset slow -crf 25 \
  -x265-params "keyint=${GOP_FRAMES}:min-keyint=${GOP_FRAMES}:scenecut=0:open-gop=0" \
  -movflags +faststart \
  -c:a aac -b:a 128k \
  "$HEVC_OUT"

# --- HLS packaging + encryption ---
echo "==> Generating AES-128 key + encrypted HLS segments"
openssl rand -out "$HLS_KEY_FILE" 16
KEY_HEX="$(xxd -p "$HLS_KEY_FILE" | tr -d '\n' | tr '[:lower:]' '[:upper:]')"
KEY_BASE64="$(base64 < "$HLS_KEY_FILE" | tr -d '\n')"
H264_IV="$(openssl rand -hex 16 | tr '[:lower:]' '[:upper:]')"
HEVC_IV="$(openssl rand -hex 16 | tr '[:lower:]' '[:upper:]')"
mkdir -p "$HLS_DIR"
write_key_info_file "$KEY_URI_H264" "$H264_KEYINFO_FILE" "$H264_IV"
write_key_info_file "$KEY_URI_HEVC" "$HEVC_KEYINFO_FILE" "$HEVC_IV"
echo "   Key saved to: $HLS_KEY_FILE (keep this secret; not uploaded)"
echo "   H.264 key URI: $KEY_URI_H264"
echo "   HEVC key URI : $KEY_URI_HEVC"

generate_hls_variant "h264" "$H264_OUT" "$H264_PLAYLIST" "$H264_KEYINFO_FILE"
generate_hls_variant "hevc" "$HEVC_OUT" "$HEVC_PLAYLIST" "$HEVC_KEYINFO_FILE"

H264_BITRATE="$(probe_bitrate "$H264_OUT")"
HEVC_BITRATE="$(probe_bitrate "$HEVC_OUT")"

cat > "$MASTER_PLAYLIST" <<EOF
#EXTM3U
#EXT-X-VERSION:7
#EXT-X-INDEPENDENT-SEGMENTS
#EXT-X-STREAM-INF:BANDWIDTH=${H264_BITRATE},AVERAGE-BANDWIDTH=${H264_BITRATE},CODECS="avc1.640028,mp4a.40.2",RESOLUTION=1080x1920
h264/${BASENAME}_h264.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=${HEVC_BITRATE},AVERAGE-BANDWIDTH=${HEVC_BITRATE},CODECS="hvc1.1.6.L123.B0,mp4a.40.2",RESOLUTION=1080x1920
hevc/${BASENAME}_hevc.m3u8
EOF

HLS_MASTER_REL="hls/${BASENAME}_master.m3u8"
H264_PLAYLIST_REL="hls/h264/${BASENAME}_h264.m3u8"
HEVC_PLAYLIST_REL="hls/hevc/${BASENAME}_hevc.m3u8"

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
      --arg video_path "$VIDEO_PATH_FOR_LICENSE" \
      --arg key_uri_base "$KEY_URI_BASE" \
      --arg key_uri_h264 "$KEY_URI_H264" \
      --arg key_uri_hevc "$KEY_URI_HEVC" \
      --arg hls_master "$HLS_MASTER_REL" \
      --arg hls_h264 "$H264_PLAYLIST_REL" \
      --arg hls_hevc "$HEVC_PLAYLIST_REL" \
      --arg hls_segment_seconds "$HLS_SEGMENT_SECONDS" \
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
    video_path: $video_path,
    encryption: {
      key_uri_base: $key_uri_base,
      key_uri_h264: $key_uri_h264,
      key_uri_hevc: $key_uri_hevc
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
    },
    hls: {
      master_playlist: $hls_master,
      segment_seconds: ($hls_segment_seconds|tonumber),
      variants: [
        {
          codec: "h264",
          playlist: $hls_h264,
          key_uri: $key_uri_h264
        },
        {
          codec: "hevc",
          playlist: $hls_hevc,
          key_uri: $key_uri_hevc
        }
      ]
    }
  }
' > "$META_JSON"

# Contains the AES material for the backend license manager (never uploaded automatically).
cat > "$LICENSE_PAYLOAD_JSON" <<EOF
{
  "video_path": "${VIDEO_PATH_FOR_LICENSE}",
  "key_uri_base": "${KEY_URI_BASE}",
  "key_uri_h264": "${KEY_URI_H264}",
  "key_uri_hevc": "${KEY_URI_HEVC}",
  "key_hex": "${KEY_HEX}",
  "key_base64": "${KEY_BASE64}"
}
EOF

echo
ls -lh "$H264_OUT" "$HEVC_OUT" "$META_JSON" "$LICENSE_PAYLOAD_JSON"
echo
echo "HLS playlists:"
find "$HLS_DIR" -name '*.m3u8' -print | sort
HLS_SEGMENT_COUNT="$(find "$HLS_DIR" -type f -name '*.ts' | wc -l | tr -d '[:space:]')"
echo "HLS segments: ${HLS_SEGMENT_COUNT} (.ts files under $HLS_DIR)"
echo "Key file (keep secret): $HLS_KEY_FILE"
echo "License payload JSON:   $LICENSE_PAYLOAD_JSON"
cat <<EOF

Next steps:
1. Keep ${HLS_KEY_FILE} secret (never upload it). The backend only needs the JSON below.
2. Upload the HLS artifacts when you're satisfied with the outputs:

   cd ${SCRIPT_DIR}
   ./upload_ready.sh \
     --input-dir "${OUTDIR}" \
     --bucket <bucket> \
     --prefix <prefix> \
     --upload-mp4 false

3. After uploading, register the key material so the backend can serve keys:

   ./register_ready.sh \
     --license-json "${LICENSE_PAYLOAD_JSON}" \
     --admin-token "<firebase admin token>" \
     --endpoint http://13.53.98.159/admin/video-assets

4. When rotating keys, delete with:

   curl -X DELETE \
     -H "Authorization: Bearer <admin token>" \
     "http://13.53.98.159/admin/video-assets?videoPath=${VIDEO_PATH_FOR_LICENSE}"

Or run everything in one go:

   cd ${SCRIPT_DIR}
   ./video_management.sh --parts full --input-mp4 "${IN}" --output-dir "${OUTDIR}" --bucket <bucket> --prefix <prefix> --admin-token "<firebase admin token>"

EOF
