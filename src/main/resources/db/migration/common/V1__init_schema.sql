-- =============================================
-- 다면평가 시스템 초기 스키마
-- =============================================

-- 기관 테이블
CREATE TABLE organizations (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 관리자 계정 (슈퍼 관리자 / 기관 관리자)
CREATE TABLE accounts (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT       REFERENCES organizations (id),
    login_id        VARCHAR(100) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(200),
    role            VARCHAR(30)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 부서 테이블 (계층 구조 지원)
CREATE TABLE departments (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT       NOT NULL REFERENCES organizations (id),
    parent_id       BIGINT       REFERENCES departments (id),
    name            VARCHAR(200) NOT NULL,
    code            VARCHAR(50)  NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, code)
);

-- 직원 테이블
CREATE TABLE employees (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT      NOT NULL REFERENCES organizations (id),
    department_id   BIGINT      REFERENCES departments (id),
    name            VARCHAR(100) NOT NULL,
    employee_number VARCHAR(50),
    position        VARCHAR(50),
    job_title       VARCHAR(50),
    email           VARCHAR(200),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, employee_number)
);

-- 직원 로그인 계정
CREATE TABLE user_accounts (
    id            BIGSERIAL PRIMARY KEY,
    employee_id   BIGINT       NOT NULL UNIQUE REFERENCES employees (id),
    organization_id BIGINT     NOT NULL REFERENCES organizations (id),
    login_id      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(30)  NOT NULL DEFAULT 'ROLE_USER',
    last_login_at TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 평가 템플릿
CREATE TABLE evaluation_templates (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT       NOT NULL REFERENCES organizations (id),
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 평가 문항
CREATE TABLE evaluation_questions (
    id              BIGSERIAL PRIMARY KEY,
    template_id     BIGINT       NOT NULL REFERENCES evaluation_templates (id),
    organization_id BIGINT       NOT NULL REFERENCES organizations (id),
    category        VARCHAR(100),
    content         TEXT         NOT NULL,
    question_type   VARCHAR(20)  NOT NULL DEFAULT 'SCALE',
    max_score       INTEGER,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 평가 세션
CREATE TABLE evaluation_sessions (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT       NOT NULL REFERENCES organizations (id),
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    start_date      DATE,
    end_date        DATE,
    allow_resubmit  BOOLEAN      NOT NULL DEFAULT FALSE,
    template_id     BIGINT       REFERENCES evaluation_templates (id),
    created_by      BIGINT       REFERENCES accounts (id),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 평가 관계 (자동 생성 + 수동 추가)
-- source: AUTO_GENERATED | ADMIN_ADDED
-- relation_type: UPWARD | DOWNWARD | PEER | CROSS_DEPT | MANUAL
CREATE TABLE evaluation_relationships (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT      NOT NULL REFERENCES evaluation_sessions (id),
    organization_id BIGINT      NOT NULL REFERENCES organizations (id),
    evaluator_id    BIGINT      NOT NULL REFERENCES employees (id),
    evaluatee_id    BIGINT      NOT NULL REFERENCES employees (id),
    relation_type   VARCHAR(20) NOT NULL,
    source          VARCHAR(20) NOT NULL DEFAULT 'AUTO_GENERATED',
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE (session_id, evaluator_id, evaluatee_id)
);

-- 평가 배정 (세션 시작 시 스냅샷으로 생성)
CREATE TABLE evaluation_assignments (
    id               BIGSERIAL PRIMARY KEY,
    session_id       BIGINT      NOT NULL REFERENCES evaluation_sessions (id),
    organization_id  BIGINT      NOT NULL REFERENCES organizations (id),
    relationship_id  BIGINT      REFERENCES evaluation_relationships (id),
    evaluator_id     BIGINT      NOT NULL REFERENCES employees (id),
    evaluatee_id     BIGINT      NOT NULL REFERENCES employees (id),
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submitted_at     TIMESTAMP,
    created_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- 평가 응답 (헤더)
CREATE TABLE evaluation_responses (
    id              BIGSERIAL PRIMARY KEY,
    assignment_id   BIGINT    NOT NULL UNIQUE REFERENCES evaluation_assignments (id),
    organization_id BIGINT    NOT NULL REFERENCES organizations (id),
    is_final        BOOLEAN   NOT NULL DEFAULT FALSE,
    submitted_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 평가 응답 문항별 데이터
CREATE TABLE evaluation_response_items (
    id          BIGSERIAL PRIMARY KEY,
    response_id BIGINT    NOT NULL REFERENCES evaluation_responses (id),
    question_id BIGINT    NOT NULL REFERENCES evaluation_questions (id),
    score_value INTEGER,
    text_value  TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (response_id, question_id)
);

-- 엑셀 업로드 이력
CREATE TABLE upload_histories (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT       NOT NULL REFERENCES organizations (id),
    upload_type     VARCHAR(30)  NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    total_rows      INTEGER      NOT NULL DEFAULT 0,
    success_rows    INTEGER      NOT NULL DEFAULT 0,
    fail_rows       INTEGER      NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING',
    error_detail    TEXT,
    uploaded_by     BIGINT       REFERENCES accounts (id),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 인덱스
CREATE INDEX idx_departments_org ON departments (organization_id);
CREATE INDEX idx_employees_org ON employees (organization_id);
CREATE INDEX idx_employees_dept ON employees (department_id);
CREATE INDEX idx_user_accounts_org ON user_accounts (organization_id);
CREATE INDEX idx_eval_sessions_org ON evaluation_sessions (organization_id);
CREATE INDEX idx_eval_relationships_session ON evaluation_relationships (session_id);
CREATE INDEX idx_eval_relationships_evaluator ON evaluation_relationships (evaluator_id);
CREATE INDEX idx_eval_assignments_session ON evaluation_assignments (session_id);
CREATE INDEX idx_eval_assignments_evaluator ON evaluation_assignments (evaluator_id);
CREATE INDEX idx_eval_responses_assignment ON evaluation_responses (assignment_id);
