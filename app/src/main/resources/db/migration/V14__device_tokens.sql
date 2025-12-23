CREATE TABLE device_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_profile_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
    token TEXT NOT NULL UNIQUE,
    platform TEXT,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_device_token_user ON device_token(user_profile_id);

CREATE TRIGGER trg_device_token_touch
BEFORE UPDATE ON device_token
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
