-- Template: transform migration_staging legacy data into new schema tables (PostgreSQL)
-- IMPORTANT:
-- 1) Replace placeholders like <TARGET_ORG_CODE>, <TARGET_ORG_TYPE>, <TARGET_ORG_PROFILE>.
-- 2) Execute after load_staging_template.sql.
-- 3) This template is for pilot (single organization) and must be reviewed before production.

BEGIN;

-- ======================================================================
-- 0) Parameters (replace placeholders)
-- ======================================================================
-- <TARGET_ORG_CODE>      : org code to migrate (same value to organizations.code)
-- <TARGET_ORG_TYPE>      : HOSPITAL | AFFILIATE
-- <TARGET_ORG_PROFILE>   : HOSPITAL_DEFAULT | AFFILIATE_HOSPITAL | AFFILIATE_GENERAL

WITH selected_org AS (
    SELECT s.*
    FROM migration_staging.stg_legacy_organizations s
    WHERE s.org_code = '<TARGET_ORG_CODE>'
)
INSERT INTO organizations (name, code, status, organization_type, organization_profile)
SELECT
    so.org_name,
    so.org_code,
    CASE WHEN UPPER(COALESCE(so.org_status_raw, 'ACTIVE')) = 'INACTIVE' THEN 'INACTIVE' ELSE 'ACTIVE' END,
    '<TARGET_ORG_TYPE>',
    '<TARGET_ORG_PROFILE>'
FROM selected_org so
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    status = EXCLUDED.status,
    organization_type = EXCLUDED.organization_type,
    organization_profile = EXCLUDED.organization_profile;

-- id mapping tables (legacy id -> new id)
DROP TABLE IF EXISTS migration_staging.map_org;
CREATE TABLE migration_staging.map_org AS
SELECT s.legacy_org_id, o.id AS new_org_id
FROM migration_staging.stg_legacy_organizations s
JOIN organizations o ON o.code = s.org_code
WHERE s.org_code = '<TARGET_ORG_CODE>';

DROP TABLE IF EXISTS migration_staging.map_department;
CREATE TABLE migration_staging.map_department (
    legacy_dept_id TEXT PRIMARY KEY,
    new_dept_id BIGINT NOT NULL
);

DROP TABLE IF EXISTS migration_staging.map_employee;
CREATE TABLE migration_staging.map_employee (
    legacy_employee_id TEXT PRIMARY KEY,
    new_employee_id BIGINT NOT NULL
);

DROP TABLE IF EXISTS migration_staging.map_account;
CREATE TABLE migration_staging.map_account (
    legacy_account_id TEXT PRIMARY KEY,
    new_account_id BIGINT NOT NULL
);

DROP TABLE IF EXISTS migration_staging.map_template;
CREATE TABLE migration_staging.map_template (
    legacy_template_id TEXT PRIMARY KEY,
    new_template_id BIGINT NOT NULL
);

DROP TABLE IF EXISTS migration_staging.map_question;
CREATE TABLE migration_staging.map_question (
    legacy_question_id TEXT PRIMARY KEY,
    new_question_id BIGINT NOT NULL
);

DROP TABLE IF EXISTS migration_staging.map_session;
CREATE TABLE migration_staging.map_session (
    legacy_session_id TEXT PRIMARY KEY,
    new_session_id BIGINT NOT NULL
);

DROP TABLE IF EXISTS migration_staging.map_relationship;
CREATE TABLE migration_staging.map_relationship (
    legacy_relationship_id TEXT PRIMARY KEY,
    new_relationship_id BIGINT NOT NULL
);

DROP TABLE IF EXISTS migration_staging.map_assignment;
CREATE TABLE migration_staging.map_assignment (
    legacy_assignment_id TEXT PRIMARY KEY,
    new_assignment_id BIGINT NOT NULL
);

DROP TABLE IF EXISTS migration_staging.map_response;
CREATE TABLE migration_staging.map_response (
    legacy_response_id TEXT PRIMARY KEY,
    new_response_id BIGINT NOT NULL
);

-- ======================================================================
-- 1) departments
-- ======================================================================
INSERT INTO departments (organization_id, parent_id, name, code, is_active)
SELECT
    mo.new_org_id,
    NULL,
    COALESCE(d.dept_name, d.dept_code, 'UNKNOWN_DEPT_' || d.legacy_dept_id),
    COALESCE(d.dept_code, 'DEPT_' || d.legacy_dept_id),
    CASE WHEN LOWER(COALESCE(d.is_active_raw, 'true')) IN ('false', '0', 'n', 'no') THEN FALSE ELSE TRUE END
FROM migration_staging.stg_legacy_departments d
JOIN migration_staging.map_org mo ON mo.legacy_org_id = d.legacy_org_id
ON CONFLICT (organization_id, code) DO NOTHING;

INSERT INTO migration_staging.map_department (legacy_dept_id, new_dept_id)
SELECT d.legacy_dept_id, nd.id
FROM migration_staging.stg_legacy_departments d
JOIN migration_staging.map_org mo ON mo.legacy_org_id = d.legacy_org_id
JOIN departments nd
  ON nd.organization_id = mo.new_org_id
 AND nd.code = COALESCE(d.dept_code, 'DEPT_' || d.legacy_dept_id)
ON CONFLICT (legacy_dept_id) DO NOTHING;

-- Parent department backfill (2nd pass)
UPDATE departments child
SET parent_id = parent_map.new_dept_id
FROM migration_staging.stg_legacy_departments sd
JOIN migration_staging.map_department child_map ON child_map.legacy_dept_id = sd.legacy_dept_id
JOIN migration_staging.map_department parent_map ON parent_map.legacy_dept_id = sd.legacy_parent_dept_id
WHERE child.id = child_map.new_dept_id
  AND sd.legacy_parent_dept_id IS NOT NULL;

-- ======================================================================
-- 2) employees
-- ======================================================================
INSERT INTO employees (organization_id, department_id, name, employee_number, position, job_title, email, status)
SELECT
    mo.new_org_id,
    md.new_dept_id,
    e.employee_name,
    NULLIF(TRIM(e.employee_number), ''),
    NULLIF(TRIM(e.position_text), ''),
    NULLIF(TRIM(e.job_title_text), ''),
    NULLIF(TRIM(e.email_text), ''),
    CASE WHEN UPPER(COALESCE(e.employee_status_raw, 'ACTIVE')) = 'INACTIVE' THEN 'INACTIVE' ELSE 'ACTIVE' END
FROM migration_staging.stg_legacy_employees e
JOIN migration_staging.map_org mo ON mo.legacy_org_id = e.legacy_org_id
LEFT JOIN migration_staging.map_department md ON md.legacy_dept_id = e.legacy_dept_id
ON CONFLICT (organization_id, employee_number) DO NOTHING;

INSERT INTO migration_staging.map_employee (legacy_employee_id, new_employee_id)
SELECT e.legacy_employee_id, ne.id
FROM migration_staging.stg_legacy_employees e
JOIN migration_staging.map_org mo ON mo.legacy_org_id = e.legacy_org_id
JOIN employees ne
  ON ne.organization_id = mo.new_org_id
 AND ne.name = e.employee_name
 AND COALESCE(ne.employee_number, '') = COALESCE(NULLIF(TRIM(e.employee_number), ''), '')
ON CONFLICT (legacy_employee_id) DO NOTHING;

-- ======================================================================
-- 3) accounts (super/org-admin)
-- ======================================================================
INSERT INTO accounts (organization_id, login_id, password_hash, name, email, role, status, must_change_password)
SELECT
    CASE WHEN a.legacy_org_id IS NULL THEN NULL ELSE mo.new_org_id END,
    a.login_id,
    a.password_hash,
    a.display_name,
    NULLIF(TRIM(a.email_text), ''),
    COALESCE(NULLIF(TRIM(a.role_raw), ''), 'ROLE_ORG_ADMIN'),
    CASE WHEN UPPER(COALESCE(a.status_raw, 'ACTIVE')) = 'INACTIVE' THEN 'INACTIVE' ELSE 'ACTIVE' END,
    CASE WHEN LOWER(COALESCE(a.must_change_password_raw, 'false')) IN ('true', '1', 'y', 'yes') THEN TRUE ELSE FALSE END
FROM migration_staging.stg_legacy_accounts a
LEFT JOIN migration_staging.map_org mo ON mo.legacy_org_id = a.legacy_org_id
ON CONFLICT (organization_id, login_id) DO NOTHING;

INSERT INTO migration_staging.map_account (legacy_account_id, new_account_id)
SELECT a.legacy_account_id, na.id
FROM migration_staging.stg_legacy_accounts a
LEFT JOIN migration_staging.map_org mo ON mo.legacy_org_id = a.legacy_org_id
JOIN accounts na
  ON COALESCE(na.organization_id, -1) = COALESCE(mo.new_org_id, -1)
 AND na.login_id = a.login_id
ON CONFLICT (legacy_account_id) DO NOTHING;

-- ======================================================================
-- 4) user_accounts
-- ======================================================================
INSERT INTO user_accounts (employee_id, organization_id, login_id, password_hash, role, must_change_password, last_login_at)
SELECT
    me.new_employee_id,
    mo.new_org_id,
    ua.login_id,
    ua.password_hash,
    COALESCE(NULLIF(TRIM(ua.role_raw), ''), 'ROLE_USER'),
    CASE WHEN LOWER(COALESCE(ua.must_change_password_raw, 'false')) IN ('true', '1', 'y', 'yes') THEN TRUE ELSE FALSE END,
    NULLIF(ua.last_login_at_raw, '')::timestamp
FROM migration_staging.stg_legacy_user_accounts ua
JOIN migration_staging.map_org mo ON mo.legacy_org_id = ua.legacy_org_id
JOIN migration_staging.map_employee me ON me.legacy_employee_id = ua.legacy_employee_id
ON CONFLICT (organization_id, login_id) DO NOTHING;

-- ======================================================================
-- 5) employee_attributes / values
-- ======================================================================
INSERT INTO employee_attributes (organization_id, attribute_key, attribute_name, is_active)
SELECT DISTINCT
    mo.new_org_id,
    ea.attribute_key,
    COALESCE(NULLIF(TRIM(ea.attribute_name), ''), ea.attribute_key),
    CASE WHEN LOWER(COALESCE(ea.is_active_raw, 'true')) IN ('false', '0', 'n', 'no') THEN FALSE ELSE TRUE END
FROM migration_staging.stg_legacy_employee_attributes ea
JOIN migration_staging.map_org mo ON mo.legacy_org_id = ea.legacy_org_id
ON CONFLICT (organization_id, attribute_key) DO NOTHING;

INSERT INTO employee_attribute_values (employee_id, attribute_id, value_text)
SELECT
    me.new_employee_id,
    na.id,
    eav.value_text
FROM migration_staging.stg_legacy_employee_attribute_values eav
JOIN migration_staging.stg_legacy_employee_attributes sea ON sea.legacy_attribute_id = eav.legacy_attribute_id
JOIN migration_staging.map_employee me ON me.legacy_employee_id = eav.legacy_employee_id
JOIN migration_staging.map_org mo ON mo.legacy_org_id = sea.legacy_org_id
JOIN employee_attributes na
  ON na.organization_id = mo.new_org_id
 AND na.attribute_key = sea.attribute_key
ON CONFLICT (employee_id, attribute_id, value_text) DO NOTHING;

-- ======================================================================
-- 6) templates / questions
-- ======================================================================
INSERT INTO evaluation_templates (organization_id, name, description, is_active)
SELECT
    mo.new_org_id,
    t.template_name,
    t.template_description,
    CASE WHEN LOWER(COALESCE(t.is_active_raw, 'true')) IN ('false', '0', 'n', 'no') THEN FALSE ELSE TRUE END
FROM migration_staging.stg_legacy_templates t
JOIN migration_staging.map_org mo ON mo.legacy_org_id = t.legacy_org_id;

INSERT INTO migration_staging.map_template (legacy_template_id, new_template_id)
SELECT t.legacy_template_id, nt.id
FROM migration_staging.stg_legacy_templates t
JOIN migration_staging.map_org mo ON mo.legacy_org_id = t.legacy_org_id
JOIN evaluation_templates nt
  ON nt.organization_id = mo.new_org_id
 AND nt.name = t.template_name
ON CONFLICT (legacy_template_id) DO NOTHING;

-- question_group_code 적재 기준:
--   1) stg_legacy_questions.question_group_code
--   2) (호환 fallback) question_type_raw 에 AA/AB/AC/AD/AE가 들어온 경우
--   3) 동일 legacy_template 내 group code가 1개로 수렴하면 그 값으로 보정
WITH legacy_template_single_group AS (
    SELECT
        src.legacy_template_id,
        MIN(src.group_code) AS single_group_code
    FROM (
        SELECT
            q.legacy_template_id,
            UPPER(NULLIF(TRIM(q.question_group_code), '')) AS group_code
        FROM migration_staging.stg_legacy_questions q

        UNION ALL

        SELECT
            s.legacy_template_id,
            UPPER(NULLIF(TRIM(a.resolved_question_group_code), '')) AS group_code
        FROM migration_staging.stg_legacy_assignments a
        JOIN migration_staging.stg_legacy_sessions s
          ON s.legacy_session_id = a.legacy_session_id

        UNION ALL

        SELECT
            s.legacy_template_id,
            UPPER(NULLIF(TRIM(r.resolved_question_group_code), '')) AS group_code
        FROM migration_staging.stg_legacy_relationships r
        JOIN migration_staging.stg_legacy_sessions s
          ON s.legacy_session_id = r.legacy_session_id
    ) src
    WHERE src.group_code IS NOT NULL
    GROUP BY src.legacy_template_id
    HAVING COUNT(DISTINCT src.group_code) = 1
)
INSERT INTO evaluation_questions (
    template_id, organization_id, category, content, question_type,
    max_score, sort_order, is_active, question_group_code
)
SELECT
    mt.new_template_id,
    mo.new_org_id,
    q.category_text,
    q.question_content,
    CASE
        WHEN UPPER(NULLIF(TRIM(q.question_type_raw), '')) IN ('DESCRIPTIVE', 'TEXT', 'ESSAY')
            THEN 'DESCRIPTIVE'
        ELSE 'SCALE'
    END,
    NULLIF(TRIM(q.max_score_raw), '')::integer,
    COALESCE(NULLIF(TRIM(q.sort_order_raw), '')::integer, 0),
    CASE WHEN LOWER(COALESCE(q.is_active_raw, 'true')) IN ('false', '0', 'n', 'no') THEN FALSE ELSE TRUE END,
    COALESCE(
        UPPER(NULLIF(TRIM(q.question_group_code), '')),
        CASE
            WHEN UPPER(NULLIF(TRIM(q.question_type_raw), '')) IN ('AA', 'AB', 'AC', 'AD', 'AE')
                THEN UPPER(NULLIF(TRIM(q.question_type_raw), ''))
            ELSE NULL
        END,
        ltsg.single_group_code
    )
FROM migration_staging.stg_legacy_questions q
JOIN migration_staging.map_org mo ON mo.legacy_org_id = q.legacy_org_id
JOIN migration_staging.map_template mt ON mt.legacy_template_id = q.legacy_template_id
LEFT JOIN legacy_template_single_group ltsg ON ltsg.legacy_template_id = q.legacy_template_id;

INSERT INTO migration_staging.map_question (legacy_question_id, new_question_id)
SELECT q.legacy_question_id, nq.id
FROM migration_staging.stg_legacy_questions q
JOIN migration_staging.map_org mo ON mo.legacy_org_id = q.legacy_org_id
JOIN migration_staging.map_template mt ON mt.legacy_template_id = q.legacy_template_id
JOIN evaluation_questions nq
  ON nq.organization_id = mo.new_org_id
 AND nq.template_id = mt.new_template_id
 AND nq.content = q.question_content
ON CONFLICT (legacy_question_id) DO NOTHING;

-- ======================================================================
-- 7) sessions
-- ======================================================================
INSERT INTO evaluation_sessions (
    organization_id, name, description, status, start_date, end_date,
    allow_resubmit, template_id, created_by, relationship_generation_mode, relationship_definition_set_id
)
SELECT
    mo.new_org_id,
    s.session_name,
    s.session_description,
    COALESCE(NULLIF(TRIM(s.session_status_raw), ''), 'PENDING'),
    NULLIF(TRIM(s.start_date_raw), '')::date,
    NULLIF(TRIM(s.end_date_raw), '')::date,
    CASE WHEN LOWER(COALESCE(s.allow_resubmit_raw, 'false')) IN ('true', '1', 'y', 'yes') THEN TRUE ELSE FALSE END,
    mt.new_template_id,
    ma.new_account_id,
    CASE
        WHEN UPPER(COALESCE(s.relationship_generation_mode_raw, 'LEGACY')) = 'RULE_BASED' THEN 'RULE_BASED'
        ELSE 'LEGACY'
    END,
    CASE
        WHEN UPPER(COALESCE(s.relationship_generation_mode_raw, 'LEGACY')) = 'RULE_BASED' THEN NULL
        ELSE NULL
    END
FROM migration_staging.stg_legacy_sessions s
JOIN migration_staging.map_org mo ON mo.legacy_org_id = s.legacy_org_id
LEFT JOIN migration_staging.map_template mt ON mt.legacy_template_id = s.legacy_template_id
LEFT JOIN migration_staging.map_account ma ON ma.legacy_account_id = s.legacy_created_by_account_id;

-- NOTE: RULE_BASED 세션의 relationship_definition_set_id는 bootstrap 완료 후 별도 업데이트 필요.
--       현재 템플릿은 기관의 default active set을 붙이는 예시 SQL을 제공한다.
UPDATE evaluation_sessions es
SET relationship_definition_set_id = rds.id
FROM relationship_definition_sets rds
WHERE es.organization_id = rds.organization_id
  AND es.relationship_generation_mode = 'RULE_BASED'
  AND es.relationship_definition_set_id IS NULL
  AND rds.is_default = TRUE
  AND rds.is_active = TRUE;

INSERT INTO migration_staging.map_session (legacy_session_id, new_session_id)
SELECT s.legacy_session_id, ns.id
FROM migration_staging.stg_legacy_sessions s
JOIN migration_staging.map_org mo ON mo.legacy_org_id = s.legacy_org_id
JOIN evaluation_sessions ns
  ON ns.organization_id = mo.new_org_id
 AND ns.name = s.session_name
ON CONFLICT (legacy_session_id) DO NOTHING;

-- ======================================================================
-- 8) relationships / assignments
-- ======================================================================
-- resolved_question_group_code 적재 기준:
--   1) assignment/relationship 원천 값 우선
--   2) 템플릿 문항이 단일 group code인 경우 해당 값으로 보정
WITH template_single_group AS (
    SELECT
        q.template_id,
        MIN(q.question_group_code) AS single_group_code
    FROM evaluation_questions q
    WHERE q.question_group_code IS NOT NULL
      AND TRIM(q.question_group_code) <> ''
    GROUP BY q.template_id
    HAVING COUNT(DISTINCT q.question_group_code) = 1
)
INSERT INTO evaluation_relationships (
    session_id, organization_id, evaluator_id, evaluatee_id,
    relation_type, source, is_active, resolved_question_group_code
)
SELECT
    ms.new_session_id,
    mo.new_org_id,
    mev.new_employee_id,
    met.new_employee_id,
    COALESCE(NULLIF(TRIM(r.relation_type_raw), ''), 'MANUAL'),
    COALESCE(NULLIF(TRIM(r.source_raw), ''), 'ADMIN_ADDED'),
    CASE WHEN LOWER(COALESCE(r.is_active_raw, 'true')) IN ('false', '0', 'n', 'no') THEN FALSE ELSE TRUE END,
    COALESCE(
        UPPER(NULLIF(TRIM(r.resolved_question_group_code), '')),
        tsg.single_group_code
    )
FROM migration_staging.stg_legacy_relationships r
JOIN migration_staging.map_org mo ON mo.legacy_org_id = r.legacy_org_id
JOIN migration_staging.map_session ms ON ms.legacy_session_id = r.legacy_session_id
JOIN migration_staging.map_employee mev ON mev.legacy_employee_id = r.legacy_evaluator_employee_id
JOIN migration_staging.map_employee met ON met.legacy_employee_id = r.legacy_evaluatee_employee_id
JOIN evaluation_sessions es ON es.id = ms.new_session_id
LEFT JOIN template_single_group tsg ON tsg.template_id = es.template_id
WHERE mev.new_employee_id <> met.new_employee_id
ON CONFLICT (session_id, evaluator_id, evaluatee_id) DO NOTHING;

INSERT INTO migration_staging.map_relationship (legacy_relationship_id, new_relationship_id)
SELECT r.legacy_relationship_id, nr.id
FROM migration_staging.stg_legacy_relationships r
JOIN migration_staging.map_session ms ON ms.legacy_session_id = r.legacy_session_id
JOIN migration_staging.map_employee mev ON mev.legacy_employee_id = r.legacy_evaluator_employee_id
JOIN migration_staging.map_employee met ON met.legacy_employee_id = r.legacy_evaluatee_employee_id
JOIN evaluation_relationships nr
  ON nr.session_id = ms.new_session_id
 AND nr.evaluator_id = mev.new_employee_id
 AND nr.evaluatee_id = met.new_employee_id
ON CONFLICT (legacy_relationship_id) DO NOTHING;

WITH template_single_group AS (
    SELECT
        q.template_id,
        MIN(q.question_group_code) AS single_group_code
    FROM evaluation_questions q
    WHERE q.question_group_code IS NOT NULL
      AND TRIM(q.question_group_code) <> ''
    GROUP BY q.template_id
    HAVING COUNT(DISTINCT q.question_group_code) = 1
)
INSERT INTO evaluation_assignments (
    session_id, organization_id, relationship_id,
    evaluator_id, evaluatee_id, status, submitted_at, resolved_question_group_code
)
SELECT
    ms.new_session_id,
    mo.new_org_id,
    mr.new_relationship_id,
    mev.new_employee_id,
    met.new_employee_id,
    COALESCE(NULLIF(TRIM(a.assignment_status_raw), ''), 'PENDING'),
    NULLIF(TRIM(a.submitted_at_raw), '')::timestamp,
    COALESCE(
        UPPER(NULLIF(TRIM(a.resolved_question_group_code), '')),
        UPPER(NULLIF(TRIM(r.resolved_question_group_code), '')),
        tsg.single_group_code
    )
FROM migration_staging.stg_legacy_assignments a
JOIN migration_staging.map_org mo ON mo.legacy_org_id = a.legacy_org_id
JOIN migration_staging.map_session ms ON ms.legacy_session_id = a.legacy_session_id
LEFT JOIN migration_staging.map_relationship mr ON mr.legacy_relationship_id = a.legacy_relationship_id
JOIN migration_staging.map_employee mev ON mev.legacy_employee_id = a.legacy_evaluator_employee_id
JOIN migration_staging.map_employee met ON met.legacy_employee_id = a.legacy_evaluatee_employee_id
JOIN evaluation_sessions es ON es.id = ms.new_session_id
LEFT JOIN migration_staging.stg_legacy_relationships r ON r.legacy_relationship_id = a.legacy_relationship_id
LEFT JOIN template_single_group tsg ON tsg.template_id = es.template_id
WHERE mev.new_employee_id <> met.new_employee_id;

INSERT INTO migration_staging.map_assignment (legacy_assignment_id, new_assignment_id)
SELECT a.legacy_assignment_id, na.id
FROM migration_staging.stg_legacy_assignments a
JOIN migration_staging.map_session ms ON ms.legacy_session_id = a.legacy_session_id
JOIN migration_staging.map_employee mev ON mev.legacy_employee_id = a.legacy_evaluator_employee_id
JOIN migration_staging.map_employee met ON met.legacy_employee_id = a.legacy_evaluatee_employee_id
JOIN evaluation_assignments na
  ON na.session_id = ms.new_session_id
 AND na.evaluator_id = mev.new_employee_id
 AND na.evaluatee_id = met.new_employee_id
ON CONFLICT (legacy_assignment_id) DO NOTHING;

-- ======================================================================
-- 9) responses / response_items
-- ======================================================================
INSERT INTO evaluation_responses (assignment_id, organization_id, is_final, submitted_at)
SELECT
    ma.new_assignment_id,
    mo.new_org_id,
    CASE WHEN LOWER(COALESCE(r.is_final_raw, 'false')) IN ('true', '1', 'y', 'yes') THEN TRUE ELSE FALSE END,
    NULLIF(TRIM(r.submitted_at_raw), '')::timestamp
FROM migration_staging.stg_legacy_responses r
JOIN migration_staging.map_org mo ON mo.legacy_org_id = r.legacy_org_id
JOIN migration_staging.map_assignment ma ON ma.legacy_assignment_id = r.legacy_assignment_id
ON CONFLICT (assignment_id) DO NOTHING;

INSERT INTO migration_staging.map_response (legacy_response_id, new_response_id)
SELECT r.legacy_response_id, nr.id
FROM migration_staging.stg_legacy_responses r
JOIN migration_staging.map_assignment ma ON ma.legacy_assignment_id = r.legacy_assignment_id
JOIN evaluation_responses nr ON nr.assignment_id = ma.new_assignment_id
ON CONFLICT (legacy_response_id) DO NOTHING;

INSERT INTO evaluation_response_items (response_id, question_id, score_value, text_value)
SELECT
    mr.new_response_id,
    mq.new_question_id,
    NULLIF(TRIM(ri.score_value_raw), '')::integer,
    NULLIF(ri.text_value_raw, '')
FROM migration_staging.stg_legacy_response_items ri
JOIN migration_staging.map_response mr ON mr.legacy_response_id = ri.legacy_response_id
JOIN migration_staging.map_question mq ON mq.legacy_question_id = ri.legacy_question_id
ON CONFLICT (response_id, question_id) DO NOTHING;

-- ======================================================================
-- 10) post-check snapshot for this pilot org
-- ======================================================================
SELECT 'organizations' AS table_name, COUNT(*) AS cnt
FROM organizations o
WHERE o.code = '<TARGET_ORG_CODE>'
UNION ALL
SELECT 'departments', COUNT(*)
FROM departments d
JOIN organizations o ON o.id = d.organization_id
WHERE o.code = '<TARGET_ORG_CODE>'
UNION ALL
SELECT 'employees', COUNT(*)
FROM employees e
JOIN organizations o ON o.id = e.organization_id
WHERE o.code = '<TARGET_ORG_CODE>'
UNION ALL
SELECT 'user_accounts', COUNT(*)
FROM user_accounts ua
JOIN organizations o ON o.id = ua.organization_id
WHERE o.code = '<TARGET_ORG_CODE>'
UNION ALL
SELECT 'evaluation_sessions', COUNT(*)
FROM evaluation_sessions s
JOIN organizations o ON o.id = s.organization_id
WHERE o.code = '<TARGET_ORG_CODE>'
UNION ALL
SELECT 'evaluation_assignments', COUNT(*)
FROM evaluation_assignments a
JOIN organizations o ON o.id = a.organization_id
WHERE o.code = '<TARGET_ORG_CODE>';

COMMIT;
