ALTER TABLE evaluation_sessions
    ADD COLUMN IF NOT EXISTS relationship_generation_mode VARCHAR(20) NOT NULL DEFAULT 'LEGACY';

ALTER TABLE evaluation_sessions
    ADD COLUMN IF NOT EXISTS relationship_definition_set_id BIGINT NULL;

CREATE TABLE IF NOT EXISTS relationship_definition_sets (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT       NOT NULL REFERENCES organizations (id),
    name            VARCHAR(200) NOT NULL,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by      BIGINT       REFERENCES accounts (id),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, name)
);

CREATE TABLE IF NOT EXISTS relationship_definition_rules (
    id              BIGSERIAL PRIMARY KEY,
    set_id          BIGINT       NOT NULL REFERENCES relationship_definition_sets (id),
    rule_name       VARCHAR(200) NOT NULL,
    relation_type   VARCHAR(20)  NOT NULL,
    priority        INTEGER      NOT NULL DEFAULT 100,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (set_id, rule_name)
);

CREATE TABLE IF NOT EXISTS relationship_rule_matchers (
    id              BIGSERIAL PRIMARY KEY,
    rule_id         BIGINT       NOT NULL REFERENCES relationship_definition_rules (id),
    subject_type    VARCHAR(20)  NOT NULL,
    matcher_type    VARCHAR(30)  NOT NULL,
    operator        VARCHAR(20)  NOT NULL,
    value_text      VARCHAR(300),
    value_json      TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS employee_attributes (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT       NOT NULL REFERENCES organizations (id),
    attribute_key   VARCHAR(100) NOT NULL,
    attribute_name  VARCHAR(200) NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, attribute_key)
);

CREATE TABLE IF NOT EXISTS employee_attribute_values (
    id              BIGSERIAL PRIMARY KEY,
    employee_id     BIGINT       NOT NULL REFERENCES employees (id),
    attribute_id    BIGINT       NOT NULL REFERENCES employee_attributes (id),
    value_text      VARCHAR(300) NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (employee_id, attribute_id, value_text)
);

CREATE TABLE IF NOT EXISTS session_generated_relationships (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT      NOT NULL REFERENCES evaluation_sessions (id),
    organization_id BIGINT      NOT NULL REFERENCES organizations (id),
    evaluator_id    BIGINT      NOT NULL REFERENCES employees (id),
    evaluatee_id    BIGINT      NOT NULL REFERENCES employees (id),
    relation_type   VARCHAR(20) NOT NULL,
    source_rule_id  BIGINT      REFERENCES relationship_definition_rules (id),
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE (session_id, evaluator_id, evaluatee_id)
);

CREATE TABLE IF NOT EXISTS session_relationship_overrides (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT      NOT NULL REFERENCES evaluation_sessions (id),
    organization_id BIGINT      NOT NULL REFERENCES organizations (id),
    evaluator_id    BIGINT      NOT NULL REFERENCES employees (id),
    evaluatee_id    BIGINT      NOT NULL REFERENCES employees (id),
    action          VARCHAR(20) NOT NULL,
    reason          VARCHAR(500),
    created_by      BIGINT      REFERENCES accounts (id),
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- FK: 재실행 가능하도록 기존 제약 제거 후 재생성
ALTER TABLE evaluation_sessions
    DROP CONSTRAINT IF EXISTS fk_eval_sessions_rule_definition_set;

ALTER TABLE evaluation_sessions
    ADD CONSTRAINT fk_eval_sessions_rule_definition_set
        FOREIGN KEY (relationship_definition_set_id)
        REFERENCES relationship_definition_sets (id);

-- 인덱스: 이미 존재하면 스킵
CREATE INDEX IF NOT EXISTS idx_relationship_definition_sets_org
    ON relationship_definition_sets (organization_id);

CREATE INDEX IF NOT EXISTS idx_relationship_definition_rules_set_priority
    ON relationship_definition_rules (set_id, priority);

CREATE INDEX IF NOT EXISTS idx_relationship_rule_matchers_rule_subject
    ON relationship_rule_matchers (rule_id, subject_type, matcher_type);

CREATE INDEX IF NOT EXISTS idx_employee_attributes_org
    ON employee_attributes (organization_id);

CREATE INDEX IF NOT EXISTS idx_employee_attribute_values_employee
    ON employee_attribute_values (employee_id);

CREATE INDEX IF NOT EXISTS idx_employee_attribute_values_attribute
    ON employee_attribute_values (attribute_id);

CREATE INDEX IF NOT EXISTS idx_session_generated_relationships_session
    ON session_generated_relationships (session_id);

CREATE INDEX IF NOT EXISTS idx_session_generated_relationships_rule
    ON session_generated_relationships (source_rule_id);

CREATE INDEX IF NOT EXISTS idx_session_relationship_overrides_session
    ON session_relationship_overrides (session_id);
