## Admin Console (web UI)

The admin console served at `/admin-console` is the preferred way to run video-asset, video-group, account-type, and license operations. It wraps every endpoint exposed under `/admin/*`, handles Firebase authentication, and logs each action to `admin_audit_log`.

### Access + Authentication

1. Browse to `/admin-console`.
2. Sign in with a Firebase Auth account whose UID is listed in `SECURITY_ADMIN_UIDS`.
3. After authentication succeeds, the console:
   - Caches the ID token/UID for the session.
   - Shows a toast with the current ID token (use the “Copy” button if you need it for `curl`).
   - Unlocks every collapsible section for the signed-in admin.

### Sections

- **Video Assets** – register/update/delete assets and run fuzzy searches on `video_path` to discover IDs.
- **Video Groups** – create groups, add/remove `video_asset` IDs, and search by group name to inspect the UUID arrays.
- **Account Types** – create account tiers, attach/detach group IDs, list every `account_type`, and access the user-level tools for searching profiles / assigning tiers. Changes fire audit log entries such as `ACCOUNT_TYPE_CREATE`, `ACCOUNT_TYPE_ADD_GROUP`, `USER_ACCOUNT_TYPE_SET`, etc.
- **User Account Types** – fuzzy user search, lookup of the currently assigned tier, and “set account type” actions that call `/admin/user-account-types`.
- **Video License Management** – upsert/delete per-user licenses outside the automated account-type flow.
- **Logs** – rolling text output with timestamps so you can see each API result immediately.

### Tips

- Keep the console open while running encoding/registration workflows. Each subsection mirrors the CLI scripts, so you can copy/paste payloads or run searches to fetch UUIDs without touching the database directly.
- If you need to issue manual curl requests, use the “Firebase Admin Token” toast to copy your ID token; it refreshes automatically when you sign in again.
- Any error responses from the backend (validation failures, missing UUIDs, etc.) surface both in the log pane and as red toasts so you notice them immediately.
