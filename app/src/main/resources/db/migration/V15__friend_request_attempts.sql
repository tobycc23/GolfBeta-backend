CREATE TABLE friend_request_attempt (
    id BIGSERIAL PRIMARY KEY,
    requester_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
    target_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_friend_request_attempt_recent
    ON friend_request_attempt (requester_id, target_id, attempted_at DESC);
