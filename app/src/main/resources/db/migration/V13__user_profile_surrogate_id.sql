-- V13__user_profile_surrogate_id.sql

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Add a durable surrogate identifier that will become the new primary key.
ALTER TABLE user_profile
    ADD COLUMN IF NOT EXISTS id UUID;

UPDATE user_profile
SET id = gen_random_uuid()
WHERE id IS NULL;

ALTER TABLE user_profile
    ALTER COLUMN id SET DEFAULT gen_random_uuid(),
    ALTER COLUMN id SET NOT NULL;

-- Prepare referencing tables to use UUID foreign keys.
ALTER TABLE user_account_types
    ADD COLUMN user_profile_id_v2 UUID;

UPDATE user_account_types AS uat
SET user_profile_id_v2 = up.id
FROM user_profile AS up
WHERE up.user_id = uat.user_profile_id;

ALTER TABLE user_account_types
    ALTER COLUMN user_profile_id_v2 SET NOT NULL;

ALTER TABLE golfr_practice_hundred
    ADD COLUMN user_id_v2 UUID;

UPDATE golfr_practice_hundred AS gph
SET user_id_v2 = up.id
FROM user_profile AS up
WHERE up.user_id = gph.user_id;

ALTER TABLE golfr_practice_hundred
    ALTER COLUMN user_id_v2 SET NOT NULL;

ALTER TABLE friends
    ADD COLUMN user_id_a_v2 UUID,
    ADD COLUMN user_id_b_v2 UUID,
    ADD COLUMN requester_id_v2 UUID;

UPDATE friends AS f
SET user_id_a_v2 = (SELECT id FROM user_profile WHERE user_id = f.user_id_a),
    user_id_b_v2 = (SELECT id FROM user_profile WHERE user_id = f.user_id_b),
    requester_id_v2 = (SELECT id FROM user_profile WHERE user_id = f.requester_id);

ALTER TABLE friends
    ALTER COLUMN user_id_a_v2 SET NOT NULL,
    ALTER COLUMN user_id_b_v2 SET NOT NULL,
    ALTER COLUMN requester_id_v2 SET NOT NULL;

ALTER TABLE user_video_license
    ADD COLUMN user_profile_id_v2 UUID;

UPDATE user_video_license AS uvl
SET user_profile_id_v2 = up.id
FROM user_profile AS up
WHERE up.user_id = uvl.user_profile_id;

ALTER TABLE user_video_license
    ALTER COLUMN user_profile_id_v2 SET NOT NULL;

-- Remove the old foreign keys and supporting indexes.
ALTER TABLE user_account_types
    DROP CONSTRAINT IF EXISTS fk_user_account_types_user_profile,
    DROP CONSTRAINT IF EXISTS uq_user_account_types_profile;

ALTER TABLE golfr_practice_hundred
    DROP CONSTRAINT IF EXISTS golfr_practice_hundred_user_id_fkey;

ALTER TABLE friends
    DROP CONSTRAINT IF EXISTS friends_user_id_a_fkey,
    DROP CONSTRAINT IF EXISTS friends_user_id_b_fkey,
    DROP CONSTRAINT IF EXISTS friends_requester_id_fkey;

ALTER TABLE user_video_license
    DROP CONSTRAINT IF EXISTS fk_user_video_license_profile,
    DROP CONSTRAINT IF EXISTS user_video_license_user_profile_id_fkey,
    DROP CONSTRAINT IF EXISTS uq_user_video_license_profile_video;

DROP INDEX IF EXISTS idx_golfr_practice_hundred_user_id;
DROP INDEX IF EXISTS friends_unique_pair;
DROP INDEX IF EXISTS friends_status_requester_idx;
DROP INDEX IF EXISTS friends_user_a_idx;
DROP INDEX IF EXISTS friends_user_b_idx;
DROP INDEX IF EXISTS idx_user_video_license_user_profile;

ALTER TABLE user_profile
    DROP CONSTRAINT user_profile_pkey,
    ADD CONSTRAINT user_profile_pkey PRIMARY KEY (id);

-- Swap the legacy text foreign key columns with the new UUID columns.
ALTER TABLE user_account_types
    DROP COLUMN user_profile_id;

ALTER TABLE user_account_types
    RENAME COLUMN user_profile_id_v2 TO user_profile_id;

ALTER TABLE golfr_practice_hundred
    DROP COLUMN user_id;

ALTER TABLE golfr_practice_hundred
    RENAME COLUMN user_id_v2 TO user_id;

ALTER TABLE friends
    DROP COLUMN user_id_a CASCADE,
    DROP COLUMN user_id_b CASCADE,
    DROP COLUMN requester_id CASCADE;

ALTER TABLE friends
    RENAME COLUMN user_id_a_v2 TO user_id_a;

ALTER TABLE friends
    RENAME COLUMN user_id_b_v2 TO user_id_b;

ALTER TABLE friends
    RENAME COLUMN requester_id_v2 TO requester_id;

ALTER TABLE user_video_license
    DROP COLUMN user_profile_id;

ALTER TABLE user_video_license
    RENAME COLUMN user_profile_id_v2 TO user_profile_id;

-- Re-establish canonical ordering for friend pairs now that UUIDs are in place.
UPDATE friends
SET user_id_a = CASE WHEN user_id_a > user_id_b THEN user_id_b ELSE user_id_a END,
    user_id_b = CASE WHEN user_id_a > user_id_b THEN user_id_a ELSE user_id_b END
WHERE user_id_a > user_id_b;

-- Re-create foreign keys, unique constraints, and indexes that now point at user_profile.id.
ALTER TABLE user_account_types
    ADD CONSTRAINT fk_user_account_types_user_profile
        FOREIGN KEY (user_profile_id)
        REFERENCES user_profile (id)
        ON DELETE CASCADE,
    ADD CONSTRAINT uq_user_account_types_profile UNIQUE (user_profile_id);

ALTER TABLE golfr_practice_hundred
    ADD CONSTRAINT golfr_practice_hundred_user_id_fkey
        FOREIGN KEY (user_id)
        REFERENCES user_profile (id)
        ON DELETE CASCADE;

CREATE INDEX idx_golfr_practice_hundred_user_id
    ON golfr_practice_hundred (user_id);

ALTER TABLE friends
    ADD CONSTRAINT friends_user_id_a_fkey
        FOREIGN KEY (user_id_a)
        REFERENCES user_profile (id)
        ON DELETE CASCADE,
    ADD CONSTRAINT friends_user_id_b_fkey
        FOREIGN KEY (user_id_b)
        REFERENCES user_profile (id)
        ON DELETE CASCADE,
    ADD CONSTRAINT friends_requester_id_fkey
        FOREIGN KEY (requester_id)
        REFERENCES user_profile (id)
        ON DELETE CASCADE,
    ADD CONSTRAINT friends_user_pair_order_chk
        CHECK (user_id_a < user_id_b),
    ADD CONSTRAINT friends_requester_member_chk
        CHECK (requester_id = user_id_a OR requester_id = user_id_b);

CREATE UNIQUE INDEX friends_unique_pair
    ON friends (user_id_a, user_id_b);
CREATE INDEX friends_status_requester_idx
    ON friends (status, requester_id);
CREATE INDEX friends_user_a_idx
    ON friends (user_id_a);
CREATE INDEX friends_user_b_idx
    ON friends (user_id_b);

ALTER TABLE user_video_license
    ADD CONSTRAINT user_video_license_user_profile_id_fkey
        FOREIGN KEY (user_profile_id)
        REFERENCES user_profile (id),
    ADD CONSTRAINT uq_user_video_license_profile_video
        UNIQUE (user_profile_id, video_id);

CREATE INDEX idx_user_video_license_user_profile
    ON user_video_license (user_profile_id);

-- Finally, promote the surrogate id to the new primary key and keep firebase_id unique.
ALTER TABLE user_profile
    RENAME COLUMN user_id TO firebase_id;

ALTER TABLE user_profile
    ADD CONSTRAINT uq_user_profile_firebase_id UNIQUE (firebase_id);
