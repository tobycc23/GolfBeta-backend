CREATE TYPE notification_type AS ENUM ('FRIEND_REQUEST', 'FRIEND_REQUEST_ACCEPTED');

CREATE TABLE notifications_inbox (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
    notification_type notification_type NOT NULL,
    notification_message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    seen BOOLEAN NOT NULL DEFAULT FALSE,
    from_user_id UUID NULL REFERENCES user_profile(id) ON DELETE SET NULL
);

CREATE INDEX idx_notifications_inbox_user_created_at
    ON notifications_inbox (user_id, created_at DESC);
