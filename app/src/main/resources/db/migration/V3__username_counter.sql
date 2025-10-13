-- V3__username_counter.sql

-- 1) Counter table: one row per base prefix (e.g., 'toby')
CREATE TABLE IF NOT EXISTS username_counters (
  base_prefix TEXT PRIMARY KEY,
  curr_seq    INTEGER NOT NULL
);

-- 2) Ensure we still enforce uniqueness on user_profile.username
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_profile_username ON user_profile (username);

-- 3) Optional: Speed LIKE queries if ever needed
CREATE INDEX IF NOT EXISTS idx_user_profile_username_pattern ON user_profile (username text_pattern_ops);
