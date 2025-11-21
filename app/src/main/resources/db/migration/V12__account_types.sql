-- V12__account_types.sql

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS video_asset_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE,
    video_asset_ids UUID[] NOT NULL DEFAULT '{}'::uuid[]
);

CREATE TABLE IF NOT EXISTS account_type (
    name TEXT PRIMARY KEY,
    video_group_ids UUID[]
);

INSERT INTO account_type (name, video_group_ids)
VALUES
    ('tier_0', '{}'::uuid[]),
    ('tier_1', '{}'::uuid[]),
    ('admin', NULL)
ON CONFLICT (name) DO NOTHING;

ALTER TABLE user_subscription RENAME TO user_account_types;

ALTER TABLE user_account_types
    RENAME CONSTRAINT fk_user_subscription_user_profile TO fk_user_account_types_user_profile;
ALTER TABLE user_account_types
    RENAME CONSTRAINT uq_user_subscription_profile TO uq_user_account_types_profile;

ALTER TABLE user_account_types
    ADD COLUMN account_type TEXT NOT NULL DEFAULT 'tier_0';

UPDATE user_account_types
SET account_type = CASE WHEN subscribed THEN 'tier_1' ELSE 'tier_0' END;

ALTER TABLE user_account_types
    ADD CONSTRAINT fk_user_account_types_account_type
        FOREIGN KEY (account_type)
        REFERENCES account_type (name);

ALTER TABLE user_account_types DROP COLUMN subscribed;
