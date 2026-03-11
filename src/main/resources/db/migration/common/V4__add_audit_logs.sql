CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    occurred_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    actor_id        BIGINT,
    actor_login_id  VARCHAR(100),
    actor_role      VARCHAR(30),
    organization_id BIGINT,
    action          VARCHAR(100) NOT NULL,
    target_type     VARCHAR(100),
    target_id       VARCHAR(100),
    outcome         VARCHAR(20)  NOT NULL,
    detail          TEXT,
    ip_address      VARCHAR(64),
    user_agent      VARCHAR(500),
    request_id      VARCHAR(100)
);

CREATE INDEX idx_audit_logs_occurred_at ON audit_logs (occurred_at DESC);
CREATE INDEX idx_audit_logs_org ON audit_logs (organization_id);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
