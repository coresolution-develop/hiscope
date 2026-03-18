ALTER TABLE session_relationship_generation_runs
    ADD COLUMN executed_by_login_id VARCHAR(100);

CREATE INDEX idx_session_relationship_generation_runs_actor_login
    ON session_relationship_generation_runs (executed_by_login_id);
