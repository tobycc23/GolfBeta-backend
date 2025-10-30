CREATE TABLE IF NOT EXISTS golfr_practice_hundred (
  id UUID PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES user_profile(user_id) ON DELETE CASCADE,
  started_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  completed_at TIMESTAMP WITHOUT TIME ZONE,
  putting_3ft TEXT,
  putting_6ft TEXT,
  putting_15ft TEXT,
  chipping_10yards TEXT,
  chipping_20yards TEXT,
  pitching_fullpw TEXT,
  pitching_threequarterpw TEXT,
  pitching_highlobs TEXT,
  shortirons_straight TEXT,
  shortirons_draw TEXT,
  shortirons_fade TEXT,
  longirons_straight TEXT,
  longirons_draw TEXT,
  longirons_fade TEXT,
  woods_straight TEXT,
  woods_draw TEXT,
  woods_fade TEXT,
  driving_straight TEXT,
  driving_draw TEXT,
  driving_fade TEXT
);

CREATE INDEX IF NOT EXISTS idx_golfr_practice_hundred_user_id
  ON golfr_practice_hundred (user_id);
