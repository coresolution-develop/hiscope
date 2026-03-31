-- 1. 세션 확정 시점 직원 스냅샷
CREATE TABLE IF NOT EXISTS session_employee_snapshots (
    id              BIGSERIAL    PRIMARY KEY,
    session_id      BIGINT       NOT NULL REFERENCES evaluation_sessions(id),
    employee_id     BIGINT       NOT NULL REFERENCES employees(id),
    name            VARCHAR(100) NOT NULL,
    department_name VARCHAR(100),
    position_name   VARCHAR(100),
    job_title       VARCHAR(100),
    snapshotted_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (session_id, employee_id)
);

-- 2. 세션 확정 후 참여자 변경 레이어
CREATE TABLE IF NOT EXISTS session_participant_overrides (
    id                       BIGSERIAL    PRIMARY KEY,
    session_id               BIGINT       NOT NULL REFERENCES evaluation_sessions(id),
    employee_id              BIGINT       NOT NULL REFERENCES employees(id),
    action                   VARCHAR(10)  NOT NULL
                             CHECK (action IN ('ADD', 'REMOVE', 'UPDATE')),
    override_name            VARCHAR(100),
    override_department_name VARCHAR(100),
    override_position_name   VARCHAR(100),
    reason                   VARCHAR(500) NOT NULL,
    created_by               BIGINT       NOT NULL REFERENCES accounts(id),
    created_at               TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ses_emp_snap_session
    ON session_employee_snapshots(session_id);

CREATE INDEX IF NOT EXISTS idx_ses_part_ovr_session
    ON session_participant_overrides(session_id);

CREATE INDEX IF NOT EXISTS idx_ses_part_ovr_session_emp
    ON session_participant_overrides(session_id, employee_id);
