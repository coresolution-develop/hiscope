ALTER TABLE evaluation_relationships
    ADD COLUMN resolved_question_group_code VARCHAR(30);

ALTER TABLE evaluation_assignments
    ADD COLUMN resolved_question_group_code VARCHAR(30);

CREATE INDEX idx_eval_relationships_session_group_code
    ON evaluation_relationships (session_id, resolved_question_group_code);

CREATE INDEX idx_eval_assignments_session_group_code
    ON evaluation_assignments (session_id, resolved_question_group_code);

