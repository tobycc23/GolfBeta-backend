## Getting an Admin Bearer Token (Firebase ID Token)

Admin endpoints (e.g. `POST /admin/video-assets`) expect the caller to provide a Firebase ID token that belongs to a user whose UID is listed in `security.admin-uids` / `SECURITY_ADMIN_UIDS`. In almost every case you should now use the Admin Console (`/admin-console`) to handle authentication and API calls—the page signs you in, caches the ID token, and exposes UI buttons for every supported admin action. After logging in, click “Copy” in the “Firebase Admin Token” toast to paste the token into other tooling. The console keeps the session cached and is the recommended path for register/upload/license/account-type/video-group operations.

The manual curl instructions below are still useful for scripting or debugging outside the console.

### 1. Locate the Firebase Web API Key

The Firebase Auth REST sign-in endpoint requires your project’s Web API key. Use whichever source is most convenient:

- `config/firebase.secrets.env` → `FIREBASE_API_KEY=...`
- `ios/GoogleService-Info.plist` → `<key>API_KEY</key>`
- Expo/React Native `.env` you already load into the app

Copy the key value (it looks like `AIza...`).

### 2. Sign in via the REST API

Use the admin user’s email/password to request an ID token. Example with `curl`:

```bash
API_KEY="AIzaSyExampleFromStep1"
ADMIN_EMAIL="admin@example.com"
ADMIN_PASSWORD="super-secret-password"

curl -sS "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${API_KEY}" \
  -H "Content-Type: application/json" \
  -d "{
        \"email\": \"${ADMIN_EMAIL}\",
        \"password\": \"${ADMIN_PASSWORD}\",
        \"returnSecureToken\": true
      }"
```

The JSON response contains:

```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6I...",
  "refreshToken": "...",
  "expiresIn": "3600",
  "localId": "firebase-uid-of-user",
  ...
}
```

- `idToken` → use this as the Bearer token in your admin curl commands.
- `localId` → UID of the account; ensure it appears in `SECURITY_ADMIN_UIDS` on the backend.

### 3. Call the Admin Endpoint

```bash
ID_TOKEN="eyJhbGciOiJSUzI1NiIsImtpZCI6I..."   # from step 2

curl -X POST http://13.53.98.159/admin/video-assets \
  -H "Authorization: Bearer ${ID_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @path/to/<basename>_license_material.json
```

### Optional: Refreshing Tokens

ID tokens expire after ~1 hour. Re-run the sign-in request whenever you need a new token, or store the `refreshToken` and call the `accounts:token` endpoint if automating this flow.

### Troubleshooting

- **403 Forbidden**: verify the UID from the sign-in response (`localId`) is present in `SECURITY_ADMIN_UIDS` (backend env var). Multiple UIDs can be provided as a comma-separated list.
- **INVALID_PASSWORD / EMAIL_NOT_FOUND**: confirm the credentials for the admin account in Firebase Auth.
- **Network blocks**: ensure your machine can reach `https://identitytoolkit.googleapis.com`.

Keep this doc handy so you can mint new admin tokens whenever you need to run the manual curl commands printed by `encode_ready.sh`. When possible, prefer the Admin Console so every change is guided, logged automatically, and less error-prone.
