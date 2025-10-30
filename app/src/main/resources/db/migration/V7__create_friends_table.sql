BEGIN;

CREATE TABLE friends (
  id            bigserial PRIMARY KEY,
  user_id_a     text NOT NULL REFERENCES user_profile(user_id) ON DELETE CASCADE,
  user_id_b     text NOT NULL REFERENCES user_profile(user_id) ON DELETE CASCADE,
  requester_id  text NOT NULL REFERENCES user_profile(user_id) ON DELETE CASCADE,
  status        text NOT NULL DEFAULT 'REQUESTED',
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),

  -- Keep the pair once in canonical order
  CHECK (user_id_a < user_id_b),

  -- The requester must be one of the two users
  CHECK (requester_id = user_id_a OR requester_id = user_id_b),

  -- Restrict valid states to match our Java enum names
  CHECK (status IN ('REQUESTED','FRIENDS'))
);

-- Uniqueness for the unordered pair
CREATE UNIQUE INDEX friends_unique_pair ON friends (user_id_a, user_id_b);

-- Helpful lookups
CREATE INDEX friends_status_requester_idx ON friends (status, requester_id);
CREATE INDEX friends_user_a_idx ON friends (user_id_a);
CREATE INDEX friends_user_b_idx ON friends (user_id_b);

-- Keep updated_at fresh
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_friends_touch
BEFORE UPDATE ON friends
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMIT;
