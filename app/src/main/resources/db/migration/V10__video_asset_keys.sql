-- V10__video_asset_keys.sql

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS video_asset (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_path TEXT NOT NULL,
    key_hex TEXT NOT NULL,
    key_base64 TEXT NOT NULL,
    key_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_video_asset_video_path UNIQUE (video_path),
    CONSTRAINT chk_video_asset_key_hex_length CHECK (char_length(key_hex) = 32),
    CONSTRAINT chk_video_asset_key_base64_length CHECK (char_length(key_base64) BETWEEN 22 AND 24)
);

CREATE INDEX IF NOT EXISTS idx_video_asset_video_path ON video_asset (video_path);
