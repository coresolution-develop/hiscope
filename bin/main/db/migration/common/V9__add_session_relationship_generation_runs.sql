CREATE TABLE session_relationship_generation_runs (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES evaluation_sessions (id),
    organization_id BIGINT NOT NULL REFERENCES organizations (id),
    generation_mode VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    generated_count BIGINT NOT NULL DEFAULT 0,
    excluded_count BIGINT NOT NULL DEFAULT 0,
    self_removed_count BIGINT NOT NULL DEFAULT 0,
    deduplicated_count BIGINT NOT NULL DEFAULT 0,
    override_applied_count BIGINT NOT NULL DEFAULT 0,
    final_count BIGINT NOT NULL DEFAULT 0,
    rule_stats_json TEXT,
    error_message TEXT,
    executed_by BIGINT REFERENCES accounts (id),
    executed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_session_relationship_generation_runs_session
    ON session_relationship_generation_runs (session_id, executed_at DESC);

CREATE INDEX idx_session_relationship_generation_runs_org
    ON session_relationship_generation_runs (organization_id, executed_at DESC);
