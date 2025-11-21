-- V9__user_video_license.sql

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS user_video_license (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_profile_id TEXT NOT NULL,
    video_id TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMPTZ,
    last_validated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_user_video_license_profile
        FOREIGN KEY (user_profile_id)
        REFERENCES user_profile (user_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_user_video_license_profile_video UNIQUE (user_profile_id, video_id),
    CONSTRAINT chk_user_video_license_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED'))
);

CREATE INDEX IF NOT EXISTS idx_user_video_license_user_profile
    ON user_video_license (user_profile_id);

CREATE INDEX IF NOT EXISTS idx_user_video_license_expires_at
    ON user_video_license (expires_at);
