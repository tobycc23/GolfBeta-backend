import os
import urllib.parse
import boto3
import logging
import json

# ----- Config -----
REGION = os.environ.get("AWS_REGION", "eu-north-1")
MEDIACONVERT_ROLE_ARN = os.environ["MEDIACONVERT_ROLE_ARN"]  # set via Terraform

# ----- Clients & logging -----
s3 = boto3.client("s3")
_mc = None

log = logging.getLogger()
log.setLevel(logging.INFO)

def _mc_client():
    """Return a MediaConvert client bound to the account-specific endpoint."""
    global _mc
    if _mc:
        return _mc
    emc = boto3.client("mediaconvert", region_name=REGION)
    endpoint = emc.describe_endpoints()["Endpoints"][0]["Url"]
    _mc = boto3.client("mediaconvert", region_name=REGION, endpoint_url=endpoint)
    return _mc

def _variant(name_suffix: str, width: int, height: int, max_bitrate: int):
    """One HLS output (TS, H.264 QVBR single-pass) with AAC stereo."""
    return {
        "NameModifier": name_suffix,  # e.g. "-1080p" -> master-1080p.m3u8
        "VideoDescription": {
            "ScalingBehavior": "DEFAULT",
            "AfdSignaling": "NONE",
            "ColorMetadata": "INSERT",
            "Width": width,
            "Height": height,
            "CodecSettings": {
                "Codec": "H_264",
                "H264Settings": {
                    "RateControlMode": "QVBR",
                    "QvbrSettings": { "QvbrQualityLevel": 7 },   # quality target
                    "MaxBitrate": max_bitrate,
                    "CodecProfile": "MAIN",
                    "FramerateControl": "INITIALIZE_FROM_SOURCE",
                    "GopSize": 2.0,
                    "GopSizeUnits": "SECONDS",
                    "GopBReference": "ENABLED",
                    "NumberBFramesBetweenReferenceFrames": 3,
                    "SceneChangeDetect": "ENABLED",
                    "AdaptiveQuantization": "HIGH",
                    "ParControl": "INITIALIZE_FROM_SOURCE",
                    "QualityTuningLevel": "SINGLE_PASS"
                }
            }
        },
        "AudioDescriptions": [{
            "AudioSourceName": "Audio Selector 1",
            "CodecSettings": {
                "Codec": "AAC",
                "AacSettings": {
                    "Bitrate": 128000,
                    "CodingMode": "CODING_MODE_2_0",
                    "SampleRate": 48000
                }
            }
        }],
        "ContainerSettings": {
            "Container": "M3U8",
            "M3u8Settings": {
                "PcrControl": "PCR_EVERY_PES_PACKET",
                "ProgramNumber": 1,
                "AudioFramesPerPes": 4,
                "Scte35Source": "NONE",
                "PatInterval": 0,
                "PmtInterval": 0,
                "TransportStreamId": 1
            }
        }
    }

def handler(event, context):
    # Loud, helpful logging
    print("EVENT:", json.dumps(event))
    try:
        rec = event["Records"][0]
        bucket = rec["s3"]["bucket"]["name"]
        key = urllib.parse.unquote(rec["s3"]["object"]["key"])
    except Exception as e:
        print(f"ERROR: malformed S3 event: {e}")
        return {"error": "malformed event"}

    print(f"BUCKET={bucket} KEY={key}")

    # Only process MP4s under demo/
    if not key.startswith("demo/") or not key.lower().endswith(".mp4"):
        print("SKIP: not under demo/ or not an .mp4")
        return {"skipped": key}

    # Accept:
    #   demo/<lesson>/source.mp4  -> lesson = <lesson>
    #   demo/<slug>.mp4           -> lesson = <slug>
    parts = key.split("/")
    if len(parts) == 3:
        _, lesson, _filename = parts
    elif len(parts) == 2:
        import os as _os
        lesson = _os.path.splitext(parts[1])[0]
    else:
        print(f"ERROR: unexpected key layout: {key}")
        return {"error": "unexpected key layout", "key": key}

    print(f"LESSON={lesson}")

    # Idempotency: skip if any HLS output exists for this lesson
    probe = s3.list_objects_v2(Bucket=bucket, Prefix=f"hls/{lesson}/", MaxKeys=1)
    if probe.get("KeyCount", 0) > 0:
        print(f"SKIP: hls/{lesson}/ already exists")
        return {"skipped": f"hls/{lesson}/ already exists"}

    # Build MediaConvert job
    job = {
        "Role": MEDIACONVERT_ROLE_ARN,
        "Settings": {
            "Inputs": [{
                "FileInput": f"s3://{bucket}/{key}",
                "AudioSelectors": { "Audio Selector 1": { "DefaultSelection": "DEFAULT" } },
                "VideoSelector": {}
            }],
            "OutputGroups": [{
                "Name": "Apple HLS",
                "OutputGroupSettings": {
                    "Type": "HLS_GROUP_SETTINGS",
                    "HlsGroupSettings": {
                        "Destination": f"s3://{bucket}/hls/{lesson}/",
                        "SegmentLength": 6,
                        "MinSegmentLength": 0,
                        "ManifestDurationFormat": "INTEGER",
                        "DirectoryStructure": "SINGLE_DIRECTORY",
                        "ManifestCompression": "NONE"
                    }
                },
                "Outputs": [
                    _variant("-1080p", 1920, 1080, 7800000),
                    _variant("-540p",   960,  540, 3000000),
                    _variant("-360p",   640,  360, 1500000),
                ]
            }]
        },
        "UserMetadata": { "project": "golfbeta", "lesson": lesson }
    }

    # Create job
    try:
        resp = _mc_client().create_job(**job)
    except Exception as e:
        print(f"ERROR: create_job failed: {e}")
        raise

    job_id = resp["Job"]["Id"]
    print(f"JOB: {job_id} -> s3://{bucket}/hls/{lesson}/ (try master.m3u8)")
    return {"jobId": job_id, "lesson": lesson, "dest": f"hls/{lesson}/"}
