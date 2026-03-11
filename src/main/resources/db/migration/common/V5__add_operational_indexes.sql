CREATE INDEX IF NOT EXISTS idx_upload_histories_org_created_at
    ON upload_histories (organization_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_eval_relationships_session_active
    ON evaluation_relationships (session_id, is_active);

CREATE INDEX IF NOT EXISTS idx_eval_assignments_org_evaluator_status
    ON evaluation_assignments (organization_id, evaluator_id, status);
