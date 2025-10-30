CREATE INDEX IF NOT EXISTS idx_golfr_practice_hundred_user_completed_at
  ON golfr_practice_hundred (user_id, completed_at);

