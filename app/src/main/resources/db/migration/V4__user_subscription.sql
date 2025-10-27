-- V4__user_subscription.sql

-- Ensure we can generate UUID values inside migrations
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS user_subscription (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_profile_id TEXT NOT NULL,
  subscribed BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT fk_user_subscription_user_profile
    FOREIGN KEY (user_profile_id)
    REFERENCES user_profile (user_id) ON DELETE CASCADE,
  CONSTRAINT uq_user_subscription_profile UNIQUE (user_profile_id)
);
