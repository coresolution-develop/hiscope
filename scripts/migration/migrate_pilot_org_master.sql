-- Pilot execution SQL (master data)
-- 대상 기관: 효사랑가족요양병원 (1기관)
-- 전제:
--   - scripts/migration/load_pilot_csv_to_staging.sql 실행 완료
--   - migration_staging.stg_legacy_* 테이블 적재 완료

BEGIN;

CREATE SCHEMA IF NOT EXISTS migration_staging;

-- ----------------------------------------------------------------------
-- 0) 파일럿 실행 컨텍스트
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS migration_staging.pilot_execution_context (
    pilot_org_name VARCHAR(200) PRIMARY KEY,
    pilot_org_code VARCHAR(80),
    organization_type VARCHAR(20),
    organization_profile VARCHAR(30),
    updated_at TIMESTAMP
);

-- 구버전 테이블 호환: 기존 테이블이 남아 있어도 컬럼을 보정한다.
ALTER TABLE migration_staging.pilot_execution_context
    ADD COLUMN IF NOT EXISTS pilot_org_name VARCHAR(200);
ALTER TABLE migration_staging.pilot_execution_context
    ADD COLUMN IF NOT EXISTS pilot_org_code VARCHAR(80);
ALTER TABLE migration_staging.pilot_execution_context
    ADD COLUMN IF NOT EXISTS organization_type VARCHAR(20);
ALTER TABLE migration_staging.pilot_execution_context
    ADD COLUMN IF NOT EXISTS organization_profile VARCHAR(30);
ALTER TABLE migration_staging.pilot_execution_context
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

WITH v AS (
    SELECT
        '효사랑가족요양병원'::VARCHAR(200) AS pilot_org_name,
        'HYOSARANG_FAMILY_HOSPITAL'::VARCHAR(80) AS pilot_org_code,
        'HOSPITAL'::VARCHAR(20) AS organization_type,
        'HOSPITAL_DEFAULT'::VARCHAR(30) AS organization_profile,
        NOW()::TIMESTAMP AS updated_at
)
UPDATE migration_staging.pilot_execution_context c
SET pilot_org_code = v.pilot_org_code,
    organization_type = v.organization_type,
    organization_profile = v.organization_profile,
    updated_at = v.updated_at
FROM v
WHERE c.pilot_org_name = v.pilot_org_name;

INSERT INTO migration_staging.pilot_execution_context (
    pilot_org_name,
    pilot_org_code,
    organization_type,
    organization_profile,
    updated_at
)
SELECT
    v.pilot_org_name,
    v.pilot_org_code,
    v.organization_type,
    v.organization_profile,
    v.updated_at
FROM (
    SELECT
        '효사랑가족요양병원'::VARCHAR(200) AS pilot_org_name,
        'HYOSARANG_FAMILY_HOSPITAL'::VARCHAR(80) AS pilot_org_code,
        'HOSPITAL'::VARCHAR(20) AS organization_type,
        'HOSPITAL_DEFAULT'::VARCHAR(30) AS organization_profile,
        NOW()::TIMESTAMP AS updated_at
) v
WHERE NOT EXISTS (
    SELECT 1
    FROM migration_staging.pilot_execution_context c
    WHERE c.pilot_org_name = v.pilot_org_name
);

DROP TABLE IF EXISTS migration_staging.stg_pilot_master_unresolved;
CREATE TABLE migration_staging.stg_pilot_master_unresolved (
    domain_key TEXT NOT NULL,
    legacy_key TEXT,
    unresolved_reason TEXT NOT NULL,
    detail_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------------------------
-- 1) organization upsert + map_org
-- ----------------------------------------------------------------------
DO $$
DECLARE
    has_org_type BOOLEAN;
    has_org_profile BOOLEAN;
BEGIN
    SELECT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'organizations'
          AND column_name = 'organization_type'
    ) INTO has_org_type;

    SELECT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'organizations'
          AND column_name = 'organization_profile'
    ) INTO has_org_profile;

    IF has_org_type AND has_org_profile THEN
        EXECUTE $sql$
            UPDATE organizations o
            SET name = c.pilot_org_name,
                status = 'ACTIVE',
                organization_type = c.organization_type,
                organization_profile = c.organization_profile,
                updated_at = NOW()
            FROM migration_staging.pilot_execution_context c
            WHERE c.pilot_org_name = '효사랑가족요양병원'
              AND o.code = c.pilot_org_code
        $sql$;

        EXECUTE $sql$
            INSERT INTO organizations (name, code, status, organization_type, organization_profile)
            SELECT
                c.pilot_org_name,
                c.pilot_org_code,
                'ACTIVE',
                c.organization_type,
                c.organization_profile
            FROM migration_staging.pilot_execution_context c
            WHERE c.pilot_org_name = '효사랑가족요양병원'
              AND NOT EXISTS (
                  SELECT 1
                  FROM organizations o
                  WHERE o.code = c.pilot_org_code
              )
        $sql$;
    ELSIF has_org_type THEN
        EXECUTE $sql$
            UPDATE organizations o
            SET name = c.pilot_org_name,
                status = 'ACTIVE',
                organization_type = c.organization_type,
                updated_at = NOW()
            FROM migration_staging.pilot_execution_context c
            WHERE c.pilot_org_name = '효사랑가족요양병원'
              AND o.code = c.pilot_org_code
        $sql$;

        EXECUTE $sql$
            INSERT INTO organizations (name, code, status, organization_type)
            SELECT
                c.pilot_org_name,
                c.pilot_org_code,
                'ACTIVE',
                c.organization_type
            FROM migration_staging.pilot_execution_context c
            WHERE c.pilot_org_name = '효사랑가족요양병원'
              AND NOT EXISTS (
                  SELECT 1
                  FROM organizations o
                  WHERE o.code = c.pilot_org_code
              )
        $sql$;
    ELSIF has_org_profile THEN
        EXECUTE $sql$
            UPDATE organizations o
            SET name = c.pilot_org_name,
                status = 'ACTIVE',
                organization_profile = c.organization_profile,
                updated_at = NOW()
            FROM migration_staging.pilot_execution_context c
            WHERE c.pilot_org_name = '효사랑가족요양병원'
              AND o.code = c.pilot_org_code
        $sql$;

        EXECUTE $sql$
            INSERT INTO organizations (name, code, status, organization_profile)
            SELECT
                c.pilot_org_name,
                c.pilot_org_code,
                'ACTIVE',
                c.organization_profile
            FROM migration_staging.pilot_execution_context c
            WHERE c.pilot_org_name = '효사랑가족요양병원'
              AND NOT EXISTS (
                  SELECT 1
                  FROM organizations o
                  WHERE o.code = c.pilot_org_code
              )
        $sql$;
    ELSE
        EXECUTE $sql$
            UPDATE organizations o
            SET name = c.pilot_org_name,
                status = 'ACTIVE',
                updated_at = NOW()
            FROM migration_staging.pilot_execution_context c
            WHERE c.pilot_org_name = '효사랑가족요양병원'
              AND o.code = c.pilot_org_code
        $sql$;

        EXECUTE $sql$
            INSERT INTO organizations (name, code, status)
            SELECT
                c.pilot_org_name,
                c.pilot_org_code,
                'ACTIVE'
            FROM migration_staging.pilot_execution_context c
            WHERE c.pilot_org_name = '효사랑가족요양병원'
              AND NOT EXISTS (
                  SELECT 1
                  FROM organizations o
                  WHERE o.code = c.pilot_org_code
              )
        $sql$;
    END IF;
END $$;

DROP TABLE IF EXISTS migration_staging.map_org;
CREATE TABLE migration_staging.map_org AS
SELECT
    c.pilot_org_name::TEXT AS legacy_org_id,
    o.id AS new_org_id
FROM (
    SELECT DISTINCT ON (pilot_org_name)
        pilot_org_name,
        pilot_org_code
    FROM migration_staging.pilot_execution_context
    WHERE pilot_org_name = '효사랑가족요양병원'
    ORDER BY pilot_org_name, updated_at DESC NULLS LAST
) c
JOIN organizations o
  ON o.code = c.pilot_org_code
;

-- ----------------------------------------------------------------------
-- 2) departments (sub_management)
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS migration_staging.stg_pilot_sub_management_dedup;
CREATE TABLE migration_staging.stg_pilot_sub_management_dedup AS
WITH ranked AS (
    SELECT
        s.*,
        ROW_NUMBER() OVER (
            PARTITION BY s.eval_year, btrim(COALESCE(s.sub_code, ''))
            ORDER BY s.idx ASC
        ) AS rn
    FROM migration_staging.stg_legacy_sub_management s
)
SELECT *
FROM ranked
WHERE rn = 1;

INSERT INTO migration_staging.stg_pilot_master_unresolved (domain_key, legacy_key, unresolved_reason, detail_json)
SELECT
    'department',
    COALESCE(s.idx::text, '(null)'),
    'SUB_CODE_MISSING',
    jsonb_build_object(
        'eval_year', s.eval_year,
        'sub_name', s.sub_name,
        'sub_code', s.sub_code
    )
FROM migration_staging.stg_legacy_sub_management s
WHERE btrim(COALESCE(s.sub_code, '')) = '';

WITH source_depts AS (
    SELECT
        mo.new_org_id AS organization_id,
        NULL::BIGINT AS parent_id,
        COALESCE(NULLIF(btrim(s.sub_name), ''), btrim(s.sub_code)) AS name,
        btrim(s.sub_code) AS code,
        TRUE AS is_active
    FROM migration_staging.stg_pilot_sub_management_dedup s
    JOIN migration_staging.map_org mo ON TRUE
    WHERE btrim(COALESCE(s.sub_code, '')) <> ''
)
UPDATE departments d
SET name = sd.name,
    is_active = sd.is_active,
    updated_at = NOW()
FROM source_depts sd
WHERE d.organization_id = sd.organization_id
  AND d.code = sd.code;

WITH source_depts AS (
    SELECT
        mo.new_org_id AS organization_id,
        NULL::BIGINT AS parent_id,
        COALESCE(NULLIF(btrim(s.sub_name), ''), btrim(s.sub_code)) AS name,
        btrim(s.sub_code) AS code,
        TRUE AS is_active
    FROM migration_staging.stg_pilot_sub_management_dedup s
    JOIN migration_staging.map_org mo ON TRUE
    WHERE btrim(COALESCE(s.sub_code, '')) <> ''
)
INSERT INTO departments (organization_id, parent_id, name, code, is_active)
SELECT
    sd.organization_id,
    sd.parent_id,
    sd.name,
    sd.code,
    sd.is_active
FROM source_depts sd
WHERE NOT EXISTS (
    SELECT 1
    FROM departments d
    WHERE d.organization_id = sd.organization_id
      AND d.code = sd.code
);

DROP TABLE IF EXISTS migration_staging.map_department;
CREATE TABLE migration_staging.map_department (
    legacy_eval_year INTEGER NOT NULL,
    legacy_sub_code TEXT NOT NULL,
    new_dept_id BIGINT NOT NULL,
    PRIMARY KEY (legacy_eval_year, legacy_sub_code)
);

INSERT INTO migration_staging.map_department (legacy_eval_year, legacy_sub_code, new_dept_id)
SELECT DISTINCT
    s.eval_year,
    btrim(s.sub_code) AS legacy_sub_code,
    d.id AS new_dept_id
FROM migration_staging.stg_legacy_sub_management s
JOIN migration_staging.map_org mo ON TRUE
JOIN departments d
  ON d.organization_id = mo.new_org_id
 AND d.code = btrim(s.sub_code)
WHERE btrim(COALESCE(s.sub_code, '')) <> '';

-- ----------------------------------------------------------------------
-- 3) employees / user_accounts (users_2025)
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS migration_staging.stg_pilot_users_core;
CREATE TABLE migration_staging.stg_pilot_users_core AS
WITH ranked AS (
    SELECT
        u.*,
        ROW_NUMBER() OVER (
            PARTITION BY u.eval_year, btrim(COALESCE(u.id, ''))
            ORDER BY
                COALESCE(u.delete_at, u.create_at) DESC,
                u.idx DESC
        ) AS rn
    FROM migration_staging.stg_legacy_users_2025 u
)
SELECT *
FROM ranked
WHERE rn = 1
  AND btrim(COALESCE(id, '')) <> '';

INSERT INTO migration_staging.stg_pilot_master_unresolved (domain_key, legacy_key, unresolved_reason, detail_json)
SELECT
    'employee',
    COALESCE(u.idx::text, '(null)'),
    'EMPLOYEE_ID_MISSING',
    jsonb_build_object(
        'eval_year', u.eval_year,
        'name', u.name,
        'sub_code', u.sub_code
    )
FROM migration_staging.stg_legacy_users_2025 u
WHERE btrim(COALESCE(u.id, '')) = '';

INSERT INTO migration_staging.stg_pilot_master_unresolved (domain_key, legacy_key, unresolved_reason, detail_json)
SELECT
    'employee',
    concat_ws(':', u.eval_year::text, u.id),
    'DEPARTMENT_MAPPING_MISSING',
    jsonb_build_object(
        'sub_code', u.sub_code,
        'name', u.name
    )
FROM migration_staging.stg_pilot_users_core u
LEFT JOIN migration_staging.map_department md
  ON md.legacy_eval_year = u.eval_year
 AND md.legacy_sub_code = btrim(COALESCE(u.sub_code, ''))
WHERE md.new_dept_id IS NULL;

WITH source_employees AS (
    SELECT
        mo.new_org_id AS organization_id,
        md.new_dept_id AS department_id,
        COALESCE(NULLIF(btrim(u.name), ''), u.id) AS name,
        btrim(u.id) AS employee_number,
        NULLIF(btrim(u.position), '') AS position,
        NULLIF(btrim(u.position), '') AS job_title,
        NULL::TEXT AS email,
        CASE
            WHEN UPPER(COALESCE(btrim(u.del_yn), 'N')) = 'Y' THEN 'INACTIVE'
            WHEN u.delete_at IS NOT NULL THEN 'INACTIVE'
            ELSE 'ACTIVE'
        END AS status
    FROM migration_staging.stg_pilot_users_core u
    JOIN migration_staging.map_org mo ON TRUE
    LEFT JOIN migration_staging.map_department md
      ON md.legacy_eval_year = u.eval_year
     AND md.legacy_sub_code = btrim(COALESCE(u.sub_code, ''))
    WHERE md.new_dept_id IS NOT NULL
)
UPDATE employees e
SET department_id = se.department_id,
    name = se.name,
    position = se.position,
    job_title = se.job_title,
    status = se.status,
    updated_at = NOW()
FROM source_employees se
WHERE e.organization_id = se.organization_id
  AND e.employee_number = se.employee_number;

WITH source_employees AS (
    SELECT
        mo.new_org_id AS organization_id,
        md.new_dept_id AS department_id,
        COALESCE(NULLIF(btrim(u.name), ''), u.id) AS name,
        btrim(u.id) AS employee_number,
        NULLIF(btrim(u.position), '') AS position,
        NULLIF(btrim(u.position), '') AS job_title,
        NULL::TEXT AS email,
        CASE
            WHEN UPPER(COALESCE(btrim(u.del_yn), 'N')) = 'Y' THEN 'INACTIVE'
            WHEN u.delete_at IS NOT NULL THEN 'INACTIVE'
            ELSE 'ACTIVE'
        END AS status
    FROM migration_staging.stg_pilot_users_core u
    JOIN migration_staging.map_org mo ON TRUE
    LEFT JOIN migration_staging.map_department md
      ON md.legacy_eval_year = u.eval_year
     AND md.legacy_sub_code = btrim(COALESCE(u.sub_code, ''))
    WHERE md.new_dept_id IS NOT NULL
)
INSERT INTO employees (
    organization_id,
    department_id,
    name,
    employee_number,
    position,
    job_title,
    email,
    status
)
SELECT
    se.organization_id,
    se.department_id,
    se.name,
    se.employee_number,
    se.position,
    se.job_title,
    se.email,
    se.status
FROM source_employees se
WHERE NOT EXISTS (
    SELECT 1
    FROM employees e
    WHERE e.organization_id = se.organization_id
      AND e.employee_number = se.employee_number
);

DROP TABLE IF EXISTS migration_staging.map_employee;
CREATE TABLE migration_staging.map_employee (
    legacy_eval_year INTEGER NOT NULL,
    legacy_user_id TEXT NOT NULL,
    new_employee_id BIGINT NOT NULL,
    PRIMARY KEY (legacy_eval_year, legacy_user_id)
);

INSERT INTO migration_staging.map_employee (legacy_eval_year, legacy_user_id, new_employee_id)
SELECT
    u.eval_year,
    btrim(u.id) AS legacy_user_id,
    e.id AS new_employee_id
FROM migration_staging.stg_pilot_users_core u
JOIN migration_staging.map_org mo ON TRUE
JOIN employees e
  ON e.organization_id = mo.new_org_id
 AND e.employee_number = btrim(u.id)
ON CONFLICT (legacy_eval_year, legacy_user_id) DO UPDATE
SET new_employee_id = EXCLUDED.new_employee_id;

INSERT INTO migration_staging.stg_pilot_master_unresolved (domain_key, legacy_key, unresolved_reason, detail_json)
SELECT
    'employee',
    concat_ws(':', u.eval_year::text, u.id),
    'EMPLOYEE_MAPPING_MISSING',
    jsonb_build_object('name', u.name)
FROM migration_staging.stg_pilot_users_core u
LEFT JOIN migration_staging.map_employee me
  ON me.legacy_eval_year = u.eval_year
 AND me.legacy_user_id = btrim(u.id)
WHERE me.new_employee_id IS NULL;

DROP TABLE IF EXISTS migration_staging.stg_pilot_user_account_candidates;
CREATE TABLE migration_staging.stg_pilot_user_account_candidates AS
SELECT
    u.eval_year,
    btrim(u.id) AS legacy_user_id,
    me.new_employee_id,
    mo.new_org_id,
    btrim(u.id) AS login_id,
    btrim(u.pwd) AS password_hash,
    ua_emp.id AS existing_by_employee_account_id,
    ua_emp.organization_id AS existing_by_employee_org_id,
    ua_emp.login_id AS existing_by_employee_login_id,
    ua_login.id AS existing_by_login_account_id,
    ua_login.employee_id AS existing_by_login_employee_id
FROM migration_staging.stg_pilot_users_core u
JOIN migration_staging.map_org mo ON TRUE
JOIN migration_staging.map_employee me
  ON me.legacy_eval_year = u.eval_year
 AND me.legacy_user_id = btrim(u.id)
LEFT JOIN user_accounts ua_emp
  ON ua_emp.employee_id = me.new_employee_id
LEFT JOIN user_accounts ua_login
  ON ua_login.organization_id = mo.new_org_id
 AND ua_login.login_id = btrim(u.id)
WHERE btrim(COALESCE(u.pwd, '')) <> '';

INSERT INTO migration_staging.stg_pilot_master_unresolved (domain_key, legacy_key, unresolved_reason, detail_json)
SELECT
    'user_account',
    concat_ws(':', c.eval_year::text, c.legacy_user_id),
    'EMPLOYEE_ACCOUNT_ALREADY_LINKED_TO_OTHER_LOGIN',
    jsonb_build_object(
        'expected_org_id', c.new_org_id,
        'expected_login_id', c.login_id,
        'existing_account_id', c.existing_by_employee_account_id,
        'existing_org_id', c.existing_by_employee_org_id,
        'existing_login_id', c.existing_by_employee_login_id
    )
FROM migration_staging.stg_pilot_user_account_candidates c
WHERE c.existing_by_employee_account_id IS NOT NULL
  AND (
      c.existing_by_employee_org_id <> c.new_org_id
      OR c.existing_by_employee_login_id <> c.login_id
  );

INSERT INTO migration_staging.stg_pilot_master_unresolved (domain_key, legacy_key, unresolved_reason, detail_json)
SELECT
    'user_account',
    concat_ws(':', c.eval_year::text, c.legacy_user_id),
    'LOGIN_ID_CONFLICT_WITH_OTHER_EMPLOYEE',
    jsonb_build_object(
        'expected_employee_id', c.new_employee_id,
        'existing_employee_id', c.existing_by_login_employee_id,
        'login_id', c.login_id
    )
FROM migration_staging.stg_pilot_user_account_candidates c
WHERE c.existing_by_login_account_id IS NOT NULL
  AND c.existing_by_login_employee_id <> c.new_employee_id;

WITH eligible_accounts AS (
    SELECT *
    FROM migration_staging.stg_pilot_user_account_candidates c
    WHERE NOT (
              c.existing_by_employee_account_id IS NOT NULL
          AND (
              c.existing_by_employee_org_id <> c.new_org_id
              OR c.existing_by_employee_login_id <> c.login_id
          )
    )
      AND NOT (
              c.existing_by_login_account_id IS NOT NULL
          AND c.existing_by_login_employee_id <> c.new_employee_id
      )
)
UPDATE user_accounts ua
SET password_hash = ea.password_hash,
    role = 'ROLE_USER',
    updated_at = NOW()
FROM eligible_accounts ea
WHERE ua.organization_id = ea.new_org_id
  AND ua.login_id = ea.login_id
  AND ua.employee_id = ea.new_employee_id;

WITH eligible_accounts AS (
    SELECT *
    FROM migration_staging.stg_pilot_user_account_candidates c
    WHERE NOT (
              c.existing_by_employee_account_id IS NOT NULL
          AND (
              c.existing_by_employee_org_id <> c.new_org_id
              OR c.existing_by_employee_login_id <> c.login_id
          )
    )
      AND NOT (
              c.existing_by_login_account_id IS NOT NULL
          AND c.existing_by_login_employee_id <> c.new_employee_id
      )
)
INSERT INTO user_accounts (
    employee_id,
    organization_id,
    login_id,
    password_hash,
    role
)
SELECT
    ea.new_employee_id,
    ea.new_org_id,
    ea.login_id,
    ea.password_hash,
    'ROLE_USER'
FROM eligible_accounts ea
WHERE NOT EXISTS (
    SELECT 1
    FROM user_accounts ua
    WHERE ua.organization_id = ea.new_org_id
      AND ua.login_id = ea.login_id
)
  AND NOT EXISTS (
      SELECT 1
      FROM user_accounts ua
      WHERE ua.employee_id = ea.new_employee_id
  );

INSERT INTO migration_staging.stg_pilot_master_unresolved (domain_key, legacy_key, unresolved_reason, detail_json)
SELECT
    'user_account',
    concat_ws(':', u.eval_year::text, u.id),
    'PASSWORD_HASH_MISSING',
    jsonb_build_object('employee_name', u.name)
FROM migration_staging.stg_pilot_users_core u
WHERE btrim(COALESCE(u.pwd, '')) = '';

-- ----------------------------------------------------------------------
-- 4) template / questions (evaluation)
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS migration_staging.map_template;
CREATE TABLE migration_staging.map_template (
    legacy_eval_year INTEGER PRIMARY KEY,
    new_template_id BIGINT NOT NULL
);

INSERT INTO evaluation_templates (
    organization_id,
    name,
    description,
    is_active
)
SELECT
    mo.new_org_id,
    format('레거시 %s 파일럿 이관 템플릿', e.eval_year),
    format('효사랑가족요양병원 레거시 %s 문항 마스터', e.eval_year),
    TRUE
FROM (
    SELECT DISTINCT eval_year
    FROM migration_staging.stg_legacy_evaluation
) e
JOIN migration_staging.map_org mo ON TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM evaluation_templates t
    WHERE t.organization_id = mo.new_org_id
      AND t.name = format('레거시 %s 파일럿 이관 템플릿', e.eval_year)
);

INSERT INTO migration_staging.map_template (legacy_eval_year, new_template_id)
SELECT
    e.eval_year,
    t.id AS new_template_id
FROM (
    SELECT DISTINCT eval_year
    FROM migration_staging.stg_legacy_evaluation
) e
JOIN migration_staging.map_org mo ON TRUE
JOIN evaluation_templates t
  ON t.organization_id = mo.new_org_id
 AND t.name = format('레거시 %s 파일럿 이관 템플릿', e.eval_year)
ON CONFLICT (legacy_eval_year) DO UPDATE
SET new_template_id = EXCLUDED.new_template_id;

DO $$
DECLARE
    has_question_group_code BOOLEAN;
BEGIN
    SELECT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'evaluation_questions'
          AND column_name = 'question_group_code'
    ) INTO has_question_group_code;

    IF has_question_group_code THEN
        EXECUTE $sql$
            INSERT INTO evaluation_questions (
                template_id,
                organization_id,
                category,
                content,
                question_type,
                question_group_code,
                max_score,
                sort_order,
                is_active
            )
            SELECT
                mt.new_template_id,
                mo.new_org_id,
                NULLIF(btrim(e.d3), '') AS category,
                btrim(e.d1) AS content,
                CASE WHEN btrim(e.d3) = '주관식' THEN 'DESCRIPTIVE' ELSE 'SCALE' END AS question_type,
                UPPER(NULLIF(btrim(e.d2), '')) AS question_group_code,
                CASE WHEN btrim(e.d3) = '주관식' THEN NULL ELSE 5 END AS max_score,
                e.idx::INTEGER AS sort_order,
                TRUE
            FROM migration_staging.stg_legacy_evaluation e
            JOIN migration_staging.map_org mo ON TRUE
            JOIN migration_staging.map_template mt
              ON mt.legacy_eval_year = e.eval_year
            WHERE btrim(COALESCE(e.d1, '')) <> ''
              AND e.idx IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM evaluation_questions q
                  WHERE q.template_id = mt.new_template_id
                    AND q.sort_order = e.idx::INTEGER
                    AND q.content = btrim(e.d1)
              )
        $sql$;
    ELSE
        EXECUTE $sql$
            INSERT INTO evaluation_questions (
                template_id,
                organization_id,
                category,
                content,
                question_type,
                max_score,
                sort_order,
                is_active
            )
            SELECT
                mt.new_template_id,
                mo.new_org_id,
                NULLIF(btrim(e.d3), '') AS category,
                btrim(e.d1) AS content,
                CASE WHEN btrim(e.d3) = '주관식' THEN 'DESCRIPTIVE' ELSE 'SCALE' END AS question_type,
                CASE WHEN btrim(e.d3) = '주관식' THEN NULL ELSE 5 END AS max_score,
                e.idx::INTEGER AS sort_order,
                TRUE
            FROM migration_staging.stg_legacy_evaluation e
            JOIN migration_staging.map_org mo ON TRUE
            JOIN migration_staging.map_template mt
              ON mt.legacy_eval_year = e.eval_year
            WHERE btrim(COALESCE(e.d1, '')) <> ''
              AND e.idx IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM evaluation_questions q
                  WHERE q.template_id = mt.new_template_id
                    AND q.sort_order = e.idx::INTEGER
                    AND q.content = btrim(e.d1)
              )
        $sql$;
    END IF;
END $$;

DROP TABLE IF EXISTS migration_staging.map_question;
CREATE TABLE migration_staging.map_question (
    legacy_question_id TEXT PRIMARY KEY,
    new_question_id BIGINT NOT NULL
);

INSERT INTO migration_staging.map_question (legacy_question_id, new_question_id)
SELECT DISTINCT
    e.idx::TEXT AS legacy_question_id,
    q.id AS new_question_id
FROM migration_staging.stg_legacy_evaluation e
JOIN migration_staging.map_template mt
  ON mt.legacy_eval_year = e.eval_year
JOIN evaluation_questions q
  ON q.template_id = mt.new_template_id
 AND q.sort_order = e.idx::INTEGER
 AND q.content = btrim(e.d1)
ON CONFLICT (legacy_question_id) DO UPDATE
SET new_question_id = EXCLUDED.new_question_id;

INSERT INTO migration_staging.stg_pilot_master_unresolved (domain_key, legacy_key, unresolved_reason, detail_json)
SELECT
    'question',
    e.idx::TEXT,
    'QUESTION_MAPPING_MISSING',
    jsonb_build_object(
        'eval_year', e.eval_year,
        'content', e.d1,
        'data_type', e.d2,
        'category', e.d3
    )
FROM migration_staging.stg_legacy_evaluation e
LEFT JOIN migration_staging.map_question mq
  ON mq.legacy_question_id = e.idx::TEXT
WHERE mq.new_question_id IS NULL;

-- ----------------------------------------------------------------------
-- 5) summary
-- ----------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM migration_staging.map_org) AS mapped_org_count,
    (SELECT COUNT(*) FROM migration_staging.map_department) AS mapped_department_count,
    (SELECT COUNT(*) FROM migration_staging.map_employee) AS mapped_employee_count,
    (SELECT COUNT(*) FROM user_accounts ua JOIN migration_staging.map_org mo ON ua.organization_id = mo.new_org_id) AS mapped_user_account_count,
    (SELECT COUNT(*) FROM migration_staging.map_template) AS mapped_template_count,
    (SELECT COUNT(*) FROM migration_staging.map_question) AS mapped_question_count,
    (SELECT COUNT(*) FROM migration_staging.stg_pilot_master_unresolved) AS unresolved_count;

COMMIT;
