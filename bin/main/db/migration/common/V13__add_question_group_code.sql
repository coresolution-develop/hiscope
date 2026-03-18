ALTER TABLE evaluation_questions
    ADD COLUMN question_group_code VARCHAR(30);

CREATE INDEX IF NOT EXISTS idx_eval_questions_template_group
    ON evaluation_questions (template_id, question_group_code);

