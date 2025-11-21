CREATE TABLE admin_audit_log (
    id BIGSERIAL PRIMARY KEY,
    admin_uid VARCHAR(128) NOT NULL,
    action VARCHAR(128) NOT NULL,
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_audit_log_created_at ON admin_audit_log (created_at DESC);
CREATE INDEX idx_admin_audit_log_admin_uid ON admin_audit_log (admin_uid);
