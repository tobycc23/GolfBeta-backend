## Secure Video Delivery Pipeline

This document captures the full end-to-end flow for producing, protecting, and serving lesson videos. Keep it updated when any piece of the pipeline changes.

---

### 1. Encoding → Uploading → Registering Asset (end-to-end)

> **Key:** 🛠️ = Admin performs an action, 🔍 = Admin verifies/inspects something.
> Need to run the entire flow in one command? Use `infra/video-management/video_management.sh` with `--parts full` (or `encode-only`, `encode-upload`) to orchestrate the stages described below.

**Step 1: Run the encoder 🛠️**

```bash
cd infra/video-management
./encode_ready.sh \
  --input INPUT.mp4 \
  --output-dir OUTPUT_DIR \
  [--video-path analysis/lesson-abc] \
  [--clockwise-rotate 90]
```

- Generates H.264 and HEVC MP4 masters.
- Packages encrypted HLS playlists/segments (default 6s segments).
- Optional flag `--clockwise-rotate 90/180/270` injects the necessary transpose filters.

**Step 2: Review the local outputs 🔍**

The script emits four groups of files inside `OUTPUT_DIR`:

| File(s) | Purpose | Where it ultimately lives |
| --- | --- | --- |
| `*_metadata.json` | bitrate ladder, rendition info | Uploads to S3 | 
| `hls/` directory | playlists + encrypted TS segments (`key_uri_*` already pointing at `/user/video/license/key`) | Uploads to S3 |
| MP4 masters | Archival copy of H.264 / HEVC | Optional upload (controlled by `UPLOAD_MP4=true`) |
| `*_h264.keyinfo`, `*_hevc.keyinfo` | ffmpeg directives (`key_uri`, local key path, IV) generated during packaging | **Local only.** They reference the raw `.key` file and never need to leave your machine. Safe to delete once the encode is registered. |
| `*_license_material.json` + `<basename>_hls.key` | Licensing payload + raw AES-128 key (16 bytes) | **Never uploaded to S3**. The JSON is POSTed to the backend; the `.key` file is only for local re-encodes. |

The license JSON mirrors what `/admin/video-assets` expects:
- `video_path` &mdash; canonical path shared by apps, S3 prefix, and Postgres row.
- `key_hex` / `key_base64` &mdash; two encodings of the same AES key that protect the HLS segments. The backend stores both representations.
- `key_uri_h264` / `key_uri_hevc` &mdash; URLs already embedded in the playlists. Once the backend knows about the `video_path` and key, these URIs begin working without playlist edits.
The `.keyinfo` files in this folder simply remind ffmpeg which key file and IV were used; after the encode finishes they have no role in distribution or registration.

**Step 3: Upload only the distributable artifacts 🛠️**

- Run `upload_ready.sh` to push the HLS tree (and optionally MP4 masters) to S3. Pass the same output directory you used for the encode:

```bash
cd infra/video-management
./upload_ready.sh \
  --input-dir "<output-dir>" \
  --bucket golfbeta-eu-north-1-videos-39695a \
  --prefix videos/lessons/lesson-abc \
  --upload-mp4 false
```

- The AES key (`*_hls.key`) **never leaves your machine**. Treat it as secret working material: delete it after confirming the backend was updated, and let the DB be the single source of truth for the key.

**Step 4: Register the key / video asset 🛠️**

If you didn’t enable auto-registration, run `register_ready.sh` manually with a Firebase admin token (UID must be listed in `security.admin-uids`). The helper transforms the snake_case JSON into the camelCase payload the API expects and issues the `POST /admin/video-assets` call:

```bash
cd infra/video-management
./register_ready.sh \
  --license-json "<output>/<basename>_license_material.json" \
  --admin-token "<firebase admin token>" \
  --endpoint http://13.53.98.159/admin/video-assets
```

> To run everything end-to-end in one go, use `./video_management.sh --parts full --input-mp4 INPUT.mp4 --output-dir OUTPUT_DIR --bucket ... --prefix ... --admin-token ...`.

> **Reminder:** This POST is the only way the backend learns about the AES key. Always run it (or use the Admin Console’s equivalent form) immediately after an encode.  
This writes/updates the `video_asset` row (`video_path`, `key_hex`, `key_base64`, optional `key_version`). If you prefer a UI, open `/admin-console`, sign in, and use the “Register / Update Asset” form—it sends the same API request using the ID token acquired in the browser. Either method is acceptable as long as it uses an admin token. Use `DELETE /admin/video-assets?videoPath=...` (via curl or the console’s “Delete Asset” button) to retire keys.

**Backend flow triggered by registration**

- `register_ready.sh` (or the Admin Console form) calls `POST /admin/video-assets`, which lands in the backend’s admin controller, persists the AES materials to Postgres, and timestamps the change in `admin_audit_log`.
- The `video_asset` table is the only datastore that ever holds keys; CloudFront and S3 only serve encrypted segments. The `/user/video/license/key` endpoint described in Section 2 is the sole reader of this table and streams the bytes with `Cache-Control: no-store` once a license check passes.
- `VideoAssetService` (shared by the admin APIs and the key-delivery endpoint) normalises `video_path`, validates the key encodings/lengths, and persists `keyVersion` (defaulting to 1 when omitted) so rotations can bump the version explicitly.

---

### 2. Admin Licensing Controls

This section explains the higher-level primitives that now drive licensing. User-facing playback flows are in Section 3.

**Datastores**

- `video_asset` (unchanged): stores the AES keys per `video_path`.
- `video_asset_groups`: `id`, `name`, `video_asset_ids[]`. Treat each row as a curated bucket (e.g., `foundations_short_game`) that lists the UUIDs of the `video_asset` rows included in that tier.
- `account_type`: `name`, `video_group_ids[]`. `video_group_ids=null` means “allow ALL videos” (used for `admin`). `video_group_ids='{}'` means “allow none” (`tier_0`). Other account types (e.g., `tier_1`, `tier_2`) reference one or more group UUIDs so you can grant batches of assets at a time.
- `user_account_types`: `id`, `user_profile_id`, `account_type`. Every profile points at exactly one `account_type`; new users default to `tier_1` and can be moved to `tier_0`, `tier_2`, or `admin` via admin tooling.
- `user_video_license`: still records per-user overrides with `status`, optional `expires_at`, and `last_validated_at`. This table is now expressly for manual grants/denials (e.g., gifting a single course or revoking one lesson even though the account type would normally include it).

**How a license decision is made**

1. `UserVideoLicenseService` first looks for an explicit `user_video_license` row. If one exists, its status (ACTIVE/SUSPENDED/REVOKED + expiry) wins—even if the user’s account type would normally allow the video. This is how you force exceptions.
2. If no row exists, the service resolves the user’s `account_type`:
   - `admin` (or any type with `video_group_ids = NULL`) grants every `video_asset`.
   - `tier_0` (empty array) denies everything unless a per-video row exists.
   - All other account types expand their `video_group_ids` into `video_asset_groups`, flatten the `video_asset_ids`, and grant the video if its `video_asset.id` appears anywhere in that flattened list.

Because the derived rights happen at read time, changing an account type or editing a group takes effect immediately—no need to seed thousands of rows in `user_video_license`.

**Admin operations**

- `POST /admin/video-licenses` / `DELETE /admin/video-licenses` still manage explicit per-user overrides. Use them sparingly to gift single lessons or suspend an outlier while leaving the parent account type untouched.
- Updating `account_type.video_group_ids` is how you move entire tiers (e.g., `tier_1` vs `tier_2`). Keep a changelog in version control that maps group names to the UUIDs you expect in production.
- `user_account_types` is what you edit when someone upgrades/downgrades. Setting a profile to `admin` bypasses `video_group_ids` and implicitly licenses every asset; setting it to `tier_0` removes access everywhere unless an override exists.
- Account type lifecycle is intentionally gated: use the Admin Console’s “Account Types” section (or `POST /admin/account-types`, `POST /admin/account-types/{name}/video-groups`, `DELETE /admin/account-types/{name}/video-groups/{videoGroupId}`) to create tiers and attach/detach groups. There is **no** API to delete an account type—see `docs/account-types.md` for the safety rationale.
- Video groups are curated via the console’s “Video Groups” section (or `POST /admin/video-groups`, `POST /admin/video-groups/{groupId}/assets`, `DELETE /admin/video-groups/{groupId}/assets/{videoAssetId}`). Groups themselves cannot be deleted; only add/remove `video_asset` IDs. Always double-check UUIDs before submitting because changes affect every account type referencing that group.

**Auditing**

- Every admin API call still lands in `admin_audit_log` (endpoint, UID, summary). Keep using it to track who changed account types, pushed overrides, or edited groups.

---

### 3. Frontend Flow (`golfbeta` repo)

Every playback session runs through the same sequence: license check → cache inspection → remote fetch/download (only when needed) → decrypt/play. The hooks inside `VideoPlayerCard` enforce this order.

1. **License guard (must pass before anything else)**

   - `useVideoLicense` (`src/features/video/player/hooks/useVideoLicense.ts`) calls `GET /user/video/license/status?videoPath=...` whenever the screen mounts, regains focus, or the video changes. It surfaces `isGranted`, denial copy, and optional expiry.
   - Backend path: `UserVideoLicenseService.checkLicenseStatus` first looks for an explicit `user_video_license` row (manual override). If one does not exist, it derives entitlement from the user’s `account_type` → `video_group_ids` → `video_asset_groups`. Suspended/revoked/expired rows immediately block the request.
   - The hook drives everything downstream: controls stay disabled, downloads are blocked, and cached entries are deleted if a denial is returned. Only when `isGranted=true` does the UI enable downloads/playback.

2. **Check the local cache (LRU capped at 10, persisted across restarts)**

   - `useOfflineVideoSource` taps into `VideoCacheContext`, which subscribes to the `DownloadManager`. On startup (or after a crash/restart), the manager reloads its state from disk (`AsyncStorage` + the actual files under `Documents/VideoCache/...`), validates that the manifest/segments still exist, and rebuilds the entry list before notifying listeners. This means the in-memory view is just a live projection; the authoritative data is persisted so restarts do not lose downloads.
   - The provider sorts entries by `lastAccessedAt`, keeps the 10 most recent, and deletes everything else (including the on-disk files) via the LRU eviction logic. If the cache already contains a `ready` entry for the requested `videoId`, the hook marks it as accessed (to keep it inside the LRU window) and sets `useLocalSource=true`. No network calls are made in this path.

3. **Fetch signed URLs + download assets (only when cache miss)**

   - When no cached entry exists, `VideoPlayerCard` invokes `fetchVideoWithFallback`, which calls `GET /user/video?videoPath=...&codec=...`. The backend again runs `UserVideoLicenseService.ensureLicenseForPlayback` before issuing signed CloudFront URLs, so even if the client misbehaved, unsigned requests would still be rejected.
   - `useOfflineVideoSource` then enqueues the download with the `DownloadManager`. Because the hook received `enabled=licenseGranted`, downloads never start unless Step 1 succeeded.
   - The download manager:
     - Builds a per-video cache directory, rewrites HLS playlists to local paths, and labels each resource (`segment`, `asset`, `key`).
     - Pulls media segments via `react-native-fs` while key URIs go through `authedFetchAbsolute` → `GET /user/video/license/key`. That endpoint re-runs the same entitlement guard and streams AES bytes with `Cache-Control: no-store`. Each key is fetched once per download, stored as base64 alongside the manifest in the app’s sandbox (under the per-video cache directory), and never re-requested unless the cached entry is deleted and has to be re-downloaded. The rewritten `EXT-X-KEY` URIs point at those sandboxed files, so the key never leaves the app.
     - Keeps state (`status`, `progress`, `manifestPath`, `segmentPaths`, `videoPath`, `lastAccessedAt`). A background loader validates the files on app launch; missing data resets the entry to `pending`.
   - The hook waits until every segment + key completes before exposing `isReady=true`.

4. **Decrypt and play from cache (keys + entries stay sandboxed)**

   - Once `useLocalSource=true`, iOS spins up a lightweight HTTP server (`react-native-static-server`) to serve the cached manifest/segments because `react-native-video` expects HTTP URIs. Android points the player at `file://` paths directly.
   - Keys never leave the device or the app sandbox after Step 3. The cached key files live inside the app’s private storage, are referenced only by the rewritten manifests, and are inaccessible to other apps. Playback decrypts segments locally and cannot be copied into third-party players. If a cached entry is purged (LRU, manual delete, logout, or license denial), both the segments and their key file disappear and the app must redo Steps 1–3 before playback can resume.
   - Playback revalidates the license on navigation/foreground events (Step 1). If access is revoked mid-session, the download entry is removed and the UI prompts the user to refresh their entitlement.


### 4. Operational Checklist

1. Ensure the backend has `SECURITY_ADMIN_UIDS` (comma-separated Firebase UIDs) configured so you can call admin endpoints.
2. Encode + upload using the automation helpers: either run `./encode_ready.sh` followed by `./upload_ready.sh`, or invoke `video_management.sh --parts encode-upload` to perform both in sequence.
3. POST the generated `*_license_material.json` to `/admin/video-assets` (see `docs/admin-bearer-token.md` for instructions on minting a valid Bearer token).
4. Confirm mobile app updates include the new `videoPath`.
5. Users open a video → `/user/video/license/status` returns `licenseGranted=true`.
6. App downloads signed HLS playlists/segments + resolves keys through `/user/video/license/key`.
7. Playback runs entirely from local storage; recurring license checks keep enforcing entitlement even offline.

### 5. Known Limitations / Future Enhancements

- **Screen recording**: without platform DRM (Widevine/FairPlay/PlayReady), OS-level screen capture can still record video/audio. AES + license gating mainly prevents raw file redistribution.
- **Admin automation**: Currently the encoder requires a manual curl to seed `video_asset`. Consider adding a CLI or CI job that authenticates with the backend automatically (with scoped credentials) if this becomes a bottleneck.
- **Key rotation**: To rotate an existing video’s key, regenerate the HLS package, POST the new JSON (with incremented `keyVersion` if needed), delete the old key via the admin endpoint, and invalidate CloudFront caches so devices pull the new playlists.
- **DRM migration**: When ready to invest in DRM, the `video_asset` table can store key IDs/reference data instead of raw AES material, and `/user/video/license/key` would evolve into “issue DRM license” by proxying to Widevine/FairPlay providers after checking `user_video_license`.

---

Keep this document updated as the pipeline evolves. For questions, note the responsible modules:

- Encoder tooling: `infra/video-management/`
- Backend license/key service: `app/src/main/java/com/golfbeta/user/{license,asset}`
- Mobile downloader/player: `src/features/video/...` in the `golfbeta` repo.
