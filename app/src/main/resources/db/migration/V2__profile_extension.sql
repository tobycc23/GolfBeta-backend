-- V2__profile_extension.sql

-- 1) New columns for onboarding flow
ALTER TABLE user_profile
  ADD COLUMN IF NOT EXISTS name TEXT,
  ADD COLUMN IF NOT EXISTS dob DATE,
  ADD COLUMN IF NOT EXISTS username TEXT,
  ADD COLUMN IF NOT EXISTS golf_handicap DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS break_number_target INTEGER,
  ADD COLUMN IF NOT EXISTS skill_level TEXT,
  ADD COLUMN IF NOT EXISTS improvement_areas TEXT[],
  ADD COLUMN IF NOT EXISTS profile_completed_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS profile_version INT NOT NULL DEFAULT 1;

-- 2) Constraints / checks
-- Keep skill_level flexible but constrained to a known set for now
ALTER TABLE user_profile
  ADD CONSTRAINT user_profile_skill_level_chk
  CHECK (skill_level IS NULL OR skill_level IN ('beginner','novice','intermediate','advanced','pro'));

-- 3) Uniqueness on username
-- (NULL values allowed; when set, must be unique)
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_profile_username ON user_profile (username);

-- 4) Helpful indices
CREATE INDEX IF NOT EXISTS idx_user_profile_completed
  ON user_profile (profile_completed_at);

-- 5) Backfill defaults for existing rows (if any)
-- Nothing required; existing users will have profile_completed_at = NULL (i.e., incomplete)
