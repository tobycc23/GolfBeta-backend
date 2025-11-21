## Account Type Administration

Account types determine which `video_asset_groups` (and therefore which encrypted lessons) a user can play without needing individual overrides in `user_video_license`. They are intentionally minimal:

- `account_type.name` &mdash; stable identifier (`tier_0`, `tier_1`, `admin`, etc.).
- `account_type.video_group_ids` &mdash; UUID array of the `video_asset_groups` that belong to the tier. `NULL` means “all groups” (used by `admin`). An empty array means “no groups” (`tier_0`).

### Safety Rules

1. **Never delete an account type through SQL, curl, or the Admin Console.** Deleting (for example) `admin` would strand existing admins and block them from using the console. The API intentionally omits any delete capability.
2. Account type names are immutable. Choose descriptive, versioned names (`tier_2`, `promo_june2025`, etc.) before creating them.
3. Treat `video_group_ids` as append-only history. Removing a group immediately revokes access for everyone assigned to that account type.

### Supported Admin Actions

Use the “Account Types & Video Groups” section in the Admin Console (or the equivalent curl calls) for the following:

1. **Create account type** &mdash; `POST /admin/account-types` with `{"name":"tier_2"}`. New rows start with an empty `video_group_ids` array; no video access is granted until you attach groups.
2. **Attach video group** &mdash; `POST /admin/account-types/{name}/video-groups` with `{"videoGroupId":"<uuid>"}`. Fails if the account type already grants ALL videos (`video_group_ids IS NULL`) or if the group UUID does not exist.
3. **Remove video group** &mdash; `DELETE /admin/account-types/{name}/video-groups/{videoGroupId}`. This instantly revokes that group for every user on the account type but leaves per-user overrides intact.

Because these APIs are destructive at scale, double-check account type names and group IDs before submitting. If you need to retire a tier, create a new account type, migrate users, then stop assigning the legacy one—do not delete it.
