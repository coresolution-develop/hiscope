-- Template: load legacy measured data into staging tables (PostgreSQL)
-- IMPORTANT:
-- 1) Replace all placeholders wrapped by <...>
-- 2) This template assumes CSV input files produced from the legacy system.
-- 3) Run per pilot organization (1 org each run).

BEGIN;

CREATE SCHEMA IF NOT EXISTS migration_staging;

-- Drop/recreate for deterministic rehearsal rerun.
DROP TABLE IF EXISTS migration_staging.stg_legacy_organizations;
DROP TABLE IF EXISTS migration_staging.stg_legacy_departments;
DROP TABLE IF EXISTS migration_staging.stg_legacy_employees;
DROP TABLE IF EXISTS migration_staging.stg_legacy_accounts;
DROP TABLE IF EXISTS migration_staging.stg_legacy_user_accounts;
DROP TABLE IF EXISTS migration_staging.stg_legacy_employee_attributes;
DROP TABLE IF EXISTS migration_staging.stg_legacy_employee_attribute_values;
DROP TABLE IF EXISTS migration_staging.stg_legacy_templates;
DROP TABLE IF EXISTS migration_staging.stg_legacy_questions;
DROP TABLE IF EXISTS migration_staging.stg_legacy_sessions;
DROP TABLE IF EXISTS migration_staging.stg_legacy_relationships;
DROP TABLE IF EXISTS migration_staging.stg_legacy_assignments;
DROP TABLE IF EXISTS migration_staging.stg_legacy_responses;
DROP TABLE IF EXISTS migration_staging.stg_legacy_response_items;

CREATE TABLE migration_staging.stg_legacy_organizations (
    legacy_org_id TEXT PRIMARY KEY,
    org_code TEXT NOT NULL,
    org_name TEXT NOT NULL,
    org_type_raw TEXT,
    org_profile_raw TEXT,
    org_status_raw TEXT
);

CREATE TABLE migration_staging.stg_legacy_departments (
    legacy_dept_id TEXT PRIMARY KEY,
    legacy_org_id TEXT NOT NULL,
    legacy_parent_dept_id TEXT,
    dept_code TEXT,
    dept_name TEXT,
    is_active_raw TEXT
);

CREATE TABLE migration_staging.stg_legacy_employees (
    legacy_employee_id TEXT PRIMARY KEY,
    legacy_org_id TEXT NOT NULL,
    legacy_dept_id TEXT,
    employee_number TEXT,
    employee_name TEXT NOT NULL,
    position_text TEXT,
    job_title_text TEXT,
    email_text TEXT,
    employee_status_raw TEXT
);

CREATE TABLE migration_staging.stg_legacy_accounts (
    legacy_account_id TEXT PRIMARY KEY,
    legacy_org_id TEXT,
    login_id TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    display_name TEXT NOT NULL,
    email_text TEXT,
    role_raw TEXT,
    status_raw TEXT,
    must_change_password_raw TEXT,
    created_at_raw TEXT,
    updated_at_raw TEXT
);

CREATE TABLE migration_staging.stg_legacy_user_accounts (
    legacy_user_account_id TEXT PRIMARY KEY,
    legacy_org_id TEXT NOT NULL,
    legacy_employee_id TEXT NOT NULL,
    login_id TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    role_raw TEXT,
    must_change_password_raw TEXT,
    last_login_at_raw TEXT,
    created_at_raw TEXT,
    updated_at_raw TEXT
);

CREATE TABLE migration_staging.stg_legacy_employee_attributes (
    legacy_attribute_id TEXT PRIMARY KEY,
    legacy_org_id TEXT NOT NULL,
    attribute_key TEXT NOT NULL,
    attribute_name TEXT,
    is_active_raw TEXT
);

CREATE TABLE migration_staging.stg_legacy_employee_attribute_values (
    legacy_attr_value_id TEXT PRIMARY KEY,
    legacy_employee_id TEXT NOT NULL,
    legacy_attribute_id TEXT NOT NULL,
    value_text TEXT NOT NULL
);

CREATE TABLE migration_staging.stg_legacy_templates (
    legacy_template_id TEXT PRIMARY KEY,
    legacy_org_id TEXT NOT NULL,
    template_name TEXT NOT NULL,
    template_description TEXT,
    is_active_raw TEXT,
    created_at_raw TEXT,
    updated_at_raw TEXT
);

CREATE TABLE migration_staging.stg_legacy_questions (
    legacy_question_id TEXT PRIMARY KEY,
    legacy_template_id TEXT NOT NULL,
    legacy_org_id TEXT NOT NULL,
    category_text TEXT,
    question_content TEXT NOT NULL,
    question_type_raw TEXT,
    max_score_raw TEXT,
    sort_order_raw TEXT,
    is_active_raw TEXT,
    question_group_code TEXT
);

CREATE TABLE migration_staging.stg_legacy_sessions (
    legacy_session_id TEXT PRIMARY KEY,
    legacy_org_id TEXT NOT NULL,
    session_name TEXT NOT NULL,
    session_description TEXT,
    session_status_raw TEXT,
    start_date_raw TEXT,
    end_date_raw TEXT,
    allow_resubmit_raw TEXT,
    legacy_template_id TEXT,
    legacy_created_by_account_id TEXT,
    relationship_generation_mode_raw TEXT,
    legacy_relationship_definition_set_id TEXT
);

CREATE TABLE migration_staging.stg_legacy_relationships (
    legacy_relationship_id TEXT PRIMARY KEY,
    legacy_session_id TEXT NOT NULL,
    legacy_org_id TEXT NOT NULL,
    legacy_evaluator_employee_id TEXT NOT NULL,
    legacy_evaluatee_employee_id TEXT NOT NULL,
    relation_type_raw TEXT,
    source_raw TEXT,
    is_active_raw TEXT,
    resolved_question_group_code TEXT
);

CREATE TABLE migration_staging.stg_legacy_assignments (
    legacy_assignment_id TEXT PRIMARY KEY,
    legacy_session_id TEXT NOT NULL,
    legacy_org_id TEXT NOT NULL,
    legacy_relationship_id TEXT,
    legacy_evaluator_employee_id TEXT NOT NULL,
    legacy_evaluatee_employee_id TEXT NOT NULL,
    assignment_status_raw TEXT,
    submitted_at_raw TEXT,
    resolved_question_group_code TEXT
);

CREATE TABLE migration_staging.stg_legacy_responses (
    legacy_response_id TEXT PRIMARY KEY,
    legacy_assignment_id TEXT NOT NULL,
    legacy_org_id TEXT NOT NULL,
    is_final_raw TEXT,
    submitted_at_raw TEXT,
    created_at_raw TEXT,
    updated_at_raw TEXT
);

CREATE TABLE migration_staging.stg_legacy_response_items (
    legacy_response_item_id TEXT PRIMARY KEY,
    legacy_response_id TEXT NOT NULL,
    legacy_question_id TEXT NOT NULL,
    score_value_raw TEXT,
    text_value_raw TEXT,
    created_at_raw TEXT,
    updated_at_raw TEXT
);

-- ----------------------------------------------------------------------
-- Replace each <PATH_...> with absolute CSV path generated from legacy DB.
-- CSV header must match staging column names exactly.
-- ----------------------------------------------------------------------

\copy migration_staging.stg_legacy_organizations FROM '<PATH_ORGANIZATIONS_CSV>' CSV HEADER;
\copy migration_staging.stg_legacy_departments FROM '<PATH_DEPARTMENTS_CSV>' CSV HEADER;
\copy migration_staging.stg_legacy_employees FROM '<PATH_EMPLOYEES_CSV>' CSV HEADER;
\copy migration_staging.stg_legacy_accounts FROM '<PATH_ACCOUNTS_CSV>' CSV HEADER;
\copy migration_staging.stg_legacy_user_accounts FROM '<PATH_USER_ACCOUNTS_CSV>' CSV HEADER;
\copy migration_staging.stg_legacy_employee_attributes FROM '<PATH_EMPLOYEE_ATTRIBUTES_CSV>' CSV HEADER;
\copy migration_staging.stg_legacy_employee_attribute_values FROM '<PATH_EMPLOYEE_ATTRIBUTE_VALUES_CSV>' CSV HEADER;
\copy migration_staging.stg_legacy_templates FROM '<PATH_TEMPLATES_CSV>' CSV HEADER;
\copy migration_staging.stg_legacy_questions FROM '<PATH_QUESTIONS_CSV>' CSV HEADER;
\copy migration_staging.stg_legacy_sessions FROM '<PATH_SESSIONS_CSV>' CSV HEADER;
\copy migration_staging.stg_legacy_relationships FROM '<PATH_RELATIONSHIPS_CSV>' CSV HEADER;
\copy migration_staging.stg_legacy_assignments FROM '<PATH_ASSIGNMENTS_CSV>' CSV HEADER;
\copy migration_staging.stg_legacy_responses FROM '<PATH_RESPONSES_CSV>' CSV HEADER;
\copy migration_staging.stg_legacy_response_items FROM '<PATH_RESPONSE_ITEMS_CSV>' CSV HEADER;

-- Quick row count snapshot
SELECT 'stg_legacy_organizations' AS table_name, COUNT(*) AS cnt FROM migration_staging.stg_legacy_organizations
UNION ALL SELECT 'stg_legacy_departments', COUNT(*) FROM migration_staging.stg_legacy_departments
UNION ALL SELECT 'stg_legacy_employees', COUNT(*) FROM migration_staging.stg_legacy_employees
UNION ALL SELECT 'stg_legacy_accounts', COUNT(*) FROM migration_staging.stg_legacy_accounts
UNION ALL SELECT 'stg_legacy_user_accounts', COUNT(*) FROM migration_staging.stg_legacy_user_accounts
UNION ALL SELECT 'stg_legacy_templates', COUNT(*) FROM migration_staging.stg_legacy_templates
UNION ALL SELECT 'stg_legacy_questions', COUNT(*) FROM migration_staging.stg_legacy_questions
UNION ALL SELECT 'stg_legacy_sessions', COUNT(*) FROM migration_staging.stg_legacy_sessions
UNION ALL SELECT 'stg_legacy_relationships', COUNT(*) FROM migration_staging.stg_legacy_relationships
UNION ALL SELECT 'stg_legacy_assignments', COUNT(*) FROM migration_staging.stg_legacy_assignments
UNION ALL SELECT 'stg_legacy_responses', COUNT(*) FROM migration_staging.stg_legacy_responses
UNION ALL SELECT 'stg_legacy_response_items', COUNT(*) FROM migration_staging.stg_legacy_response_items
ORDER BY table_name;

COMMIT;
