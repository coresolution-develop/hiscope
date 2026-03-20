-- Pilot execution SQL (roles -> employee attributes)
-- 대상 기관: 효사랑가족요양병원 (1기관)
-- 전제:
--   - scripts/migration/load_pilot_csv_to_staging.sql 실행 완료
--   - scripts/migration/migrate_pilot_org_master.sql 실행 완료
--   - migration_staging.map_org / map_employee 존재

BEGIN;

CREATE SCHEMA IF NOT EXISTS migration_staging;

-- ----------------------------------------------------------------------
-- 0) 호환 부트스트랩 (V8 미적용 DB 대응)
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS employee_attributes (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT       NOT NULL REFERENCES organizations (id),
    attribute_key   VARCHAR(100) NOT NULL,
    attribute_name  VARCHAR(200) NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS employee_attribute_values (
    id              BIGSERIAL PRIMARY KEY,
    employee_id     BIGINT       NOT NULL REFERENCES employees (id),
    attribute_id    BIGINT       NOT NULL REFERENCES employee_attributes (id),
    value_text      VARCHAR(300) NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

ALTER TABLE employee_attributes
    ADD COLUMN IF NOT EXISTS organization_id BIGINT;
ALTER TABLE employee_attributes
    ADD COLUMN IF NOT EXISTS attribute_key VARCHAR(100);
ALTER TABLE employee_attributes
    ADD COLUMN IF NOT EXISTS attribute_name VARCHAR(200);
ALTER TABLE employee_attributes
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN;
ALTER TABLE employee_attributes
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
ALTER TABLE employee_attributes
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

ALTER TABLE employee_attribute_values
    ADD COLUMN IF NOT EXISTS employee_id BIGINT;
ALTER TABLE employee_attribute_values
    ADD COLUMN IF NOT EXISTS attribute_id BIGINT;
ALTER TABLE employee_attribute_values
    ADD COLUMN IF NOT EXISTS value_text VARCHAR(300);
ALTER TABLE employee_attribute_values
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
ALTER TABLE employee_attribute_values
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE employee_attributes
SET is_active = COALESCE(is_active, TRUE),
    created_at = COALESCE(created_at, NOW()),
    updated_at = COALESCE(updated_at, NOW());

UPDATE employee_attribute_values
SET created_at = COALESCE(created_at, NOW()),
    updated_at = COALESCE(updated_at, NOW());

CREATE UNIQUE INDEX IF NOT EXISTS uq_employee_attributes_org_key_compat
    ON employee_attributes (organization_id, attribute_key);

CREATE UNIQUE INDEX IF NOT EXISTS uq_employee_attribute_values_emp_attr_val_compat
    ON employee_attribute_values (employee_id, attribute_id, value_text);

-- ----------------------------------------------------------------------
-- 1) 역할 매핑 정책 테이블 (코드 기준 제안)
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS migration_staging.map_legacy_role_attribute_policy (
    legacy_role TEXT NOT NULL,
    attribute_key TEXT NOT NULL,
    attribute_value TEXT NOT NULL,
    mapping_status TEXT NOT NULL,
    apply_flag BOOLEAN NOT NULL DEFAULT TRUE,
    note TEXT,
    PRIMARY KEY (legacy_role, attribute_key, attribute_value)
);

INSERT INTO migration_staging.map_legacy_role_attribute_policy (
    legacy_role,
    attribute_key,
    attribute_value,
    mapping_status,
    apply_flag,
    note
)
VALUES
    ('team_head', 'change_innovation_team', 'Y', 'CONFIRMED', TRUE, '경혁팀장 -> 경혁팀 속성'),
    ('team_head', 'change_innovation_team_leader', 'Y', 'CONFIRMED', TRUE, '경혁팀장 -> 경혁팀장 속성'),
    ('team_member', 'change_innovation_team', 'Y', 'CONFIRMED', TRUE, '경혁팀원 -> 경혁팀 속성'),
    ('sub_head', 'department_head', 'Y', 'CONFIRMED', TRUE, '부서장 -> 부서장 속성'),
    ('one_person_sub', 'single_member_department', 'Y', 'CONFIRMED', TRUE, '1인부서 표시'),
    ('medical_leader', 'medical_leader', 'Y', 'CONFIRMED', TRUE, '의료리더 표시')
ON CONFLICT (legacy_role, attribute_key, attribute_value) DO UPDATE
SET mapping_status = EXCLUDED.mapping_status,
    apply_flag = EXCLUDED.apply_flag,
    note = EXCLUDED.note;

DROP TABLE IF EXISTS migration_staging.stg_pilot_role_mapping_todo;
CREATE TABLE migration_staging.stg_pilot_role_mapping_todo (
    todo_key TEXT NOT NULL,
    reason TEXT NOT NULL,
    action_required TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- TODO (사람 결정 필요): role -> attribute_key 최종 보강 매핑
INSERT INTO migration_staging.stg_pilot_role_mapping_todo (todo_key, reason, action_required)
VALUES
    ('sub_head->unit_head/institution_head', 'user_roles_2025는 부서장 여부만 제공, 소속장/기관장 구분 원천 부재', '추가 원천 컬럼 또는 운영 규칙 확정 후 별도 반영'),
    ('clinical_team_leader', '확정된 레거시 role 집합에 clinical_team_leader 직접 소스 없음', '별도 소스(직책/부서/운영코드) 확인 후 반영');

DROP TABLE IF EXISTS migration_staging.stg_pilot_roles_unresolved;
CREATE TABLE migration_staging.stg_pilot_roles_unresolved (
    legacy_key TEXT,
    unresolved_reason TEXT NOT NULL,
    detail_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------------------------
-- 2) 속성키 보장 (bootstrap 의존 제거)
-- ----------------------------------------------------------------------
WITH required_attributes AS (
    SELECT DISTINCT attribute_key
    FROM migration_staging.map_legacy_role_attribute_policy
    WHERE apply_flag = TRUE
    UNION
    SELECT 'evaluation_excluded'
)
INSERT INTO employee_attributes (organization_id, attribute_key, attribute_name, is_active)
SELECT
    mo.new_org_id,
    ra.attribute_key,
    CASE ra.attribute_key
        WHEN 'change_innovation_team' THEN '경혁팀'
        WHEN 'change_innovation_team_leader' THEN '경혁팀장'
        WHEN 'department_head' THEN '부서장'
        WHEN 'single_member_department' THEN '1인부서'
        WHEN 'medical_leader' THEN '의료리더'
        WHEN 'evaluation_excluded' THEN '평가제외'
        ELSE ra.attribute_key
    END AS attribute_name,
    TRUE
FROM required_attributes ra
JOIN migration_staging.map_org mo ON TRUE
ON CONFLICT (organization_id, attribute_key) DO UPDATE
SET attribute_name = EXCLUDED.attribute_name,
    is_active = TRUE,
    updated_at = NOW();

-- ----------------------------------------------------------------------
-- 3) role 기반 attribute value 후보 생성
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS migration_staging.stg_pilot_role_attribute_candidates;
CREATE TABLE migration_staging.stg_pilot_role_attribute_candidates AS
SELECT
    concat_ws(':', r.eval_year::text, r.user_id, r.role::text, p.attribute_key) AS candidate_key,
    r.eval_year,
    btrim(r.user_id) AS legacy_user_id,
    r.role::text AS legacy_role,
    p.attribute_key,
    p.attribute_value,
    me.new_employee_id
FROM migration_staging.stg_legacy_user_roles_2025 r
JOIN migration_staging.map_legacy_role_attribute_policy p
  ON p.legacy_role = r.role::text
 AND p.apply_flag = TRUE
LEFT JOIN migration_staging.map_employee me
  ON me.legacy_eval_year = r.eval_year
 AND me.legacy_user_id = btrim(r.user_id);

INSERT INTO migration_staging.stg_pilot_roles_unresolved (legacy_key, unresolved_reason, detail_json)
SELECT
    c.candidate_key,
    'EMPLOYEE_MAPPING_MISSING',
    jsonb_build_object(
        'eval_year', c.eval_year,
        'legacy_user_id', c.legacy_user_id,
        'legacy_role', c.legacy_role,
        'attribute_key', c.attribute_key
    )
FROM migration_staging.stg_pilot_role_attribute_candidates c
WHERE c.new_employee_id IS NULL;

INSERT INTO employee_attribute_values (employee_id, attribute_id, value_text)
SELECT
    c.new_employee_id,
    ea.id,
    c.attribute_value
FROM migration_staging.stg_pilot_role_attribute_candidates c
JOIN migration_staging.map_org mo ON TRUE
JOIN employee_attributes ea
  ON ea.organization_id = mo.new_org_id
 AND ea.attribute_key = c.attribute_key
WHERE c.new_employee_id IS NOT NULL
ON CONFLICT (employee_id, attribute_id, value_text) DO NOTHING;

-- ----------------------------------------------------------------------
-- 4) team(GH_TEAM) 기반 보강: 경혁팀 속성
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS migration_staging.stg_pilot_team_attribute_candidates;
CREATE TABLE migration_staging.stg_pilot_team_attribute_candidates AS
SELECT DISTINCT
    concat_ws(':', u.eval_year::text, u.id, 'team_code', 'change_innovation_team') AS candidate_key,
    u.eval_year,
    btrim(u.id) AS legacy_user_id,
    'change_innovation_team'::text AS attribute_key,
    'Y'::text AS attribute_value,
    me.new_employee_id
FROM migration_staging.stg_legacy_users_2025 u
JOIN migration_staging.stg_legacy_team t
  ON t.eval_year = u.eval_year
 AND btrim(t.team_code) = btrim(u.team_code)
LEFT JOIN migration_staging.map_employee me
  ON me.legacy_eval_year = u.eval_year
 AND me.legacy_user_id = btrim(u.id)
WHERE btrim(COALESCE(u.team_code, '')) = 'GH_TEAM'
  AND btrim(COALESCE(u.id, '')) <> '';

INSERT INTO migration_staging.stg_pilot_roles_unresolved (legacy_key, unresolved_reason, detail_json)
SELECT
    c.candidate_key,
    'TEAM_ATTRIBUTE_EMPLOYEE_MAPPING_MISSING',
    jsonb_build_object(
        'eval_year', c.eval_year,
        'legacy_user_id', c.legacy_user_id,
        'attribute_key', c.attribute_key
    )
FROM migration_staging.stg_pilot_team_attribute_candidates c
WHERE c.new_employee_id IS NULL;

INSERT INTO employee_attribute_values (employee_id, attribute_id, value_text)
SELECT
    c.new_employee_id,
    ea.id,
    c.attribute_value
FROM migration_staging.stg_pilot_team_attribute_candidates c
JOIN migration_staging.map_org mo ON TRUE
JOIN employee_attributes ea
  ON ea.organization_id = mo.new_org_id
 AND ea.attribute_key = c.attribute_key
WHERE c.new_employee_id IS NOT NULL
ON CONFLICT (employee_id, attribute_id, value_text) DO NOTHING;

-- ----------------------------------------------------------------------
-- 5) users_2025.del_yn 기반 평가제외 속성
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS migration_staging.stg_pilot_excluded_attribute_candidates;
CREATE TABLE migration_staging.stg_pilot_excluded_attribute_candidates AS
SELECT
    concat_ws(':', u.eval_year::text, u.id, 'evaluation_excluded') AS candidate_key,
    u.eval_year,
    btrim(u.id) AS legacy_user_id,
    'evaluation_excluded'::text AS attribute_key,
    'Y'::text AS attribute_value,
    me.new_employee_id
FROM migration_staging.stg_legacy_users_2025 u
LEFT JOIN migration_staging.map_employee me
  ON me.legacy_eval_year = u.eval_year
 AND me.legacy_user_id = btrim(u.id)
WHERE UPPER(COALESCE(btrim(u.del_yn), 'N')) = 'Y'
  AND btrim(COALESCE(u.id, '')) <> '';

INSERT INTO migration_staging.stg_pilot_roles_unresolved (legacy_key, unresolved_reason, detail_json)
SELECT
    c.candidate_key,
    'EVALUATION_EXCLUDED_EMPLOYEE_MAPPING_MISSING',
    jsonb_build_object(
        'eval_year', c.eval_year,
        'legacy_user_id', c.legacy_user_id
    )
FROM migration_staging.stg_pilot_excluded_attribute_candidates c
WHERE c.new_employee_id IS NULL;

INSERT INTO employee_attribute_values (employee_id, attribute_id, value_text)
SELECT
    c.new_employee_id,
    ea.id,
    c.attribute_value
FROM migration_staging.stg_pilot_excluded_attribute_candidates c
JOIN migration_staging.map_org mo ON TRUE
JOIN employee_attributes ea
  ON ea.organization_id = mo.new_org_id
 AND ea.attribute_key = c.attribute_key
WHERE c.new_employee_id IS NOT NULL
ON CONFLICT (employee_id, attribute_id, value_text) DO NOTHING;

-- ----------------------------------------------------------------------
-- 6) 역할 미매핑 감시
-- ----------------------------------------------------------------------
INSERT INTO migration_staging.stg_pilot_roles_unresolved (legacy_key, unresolved_reason, detail_json)
SELECT
    concat_ws(':', r.eval_year::text, r.user_id, r.role::text),
    'ROLE_ATTRIBUTE_POLICY_MISSING',
    jsonb_build_object(
        'eval_year', r.eval_year,
        'legacy_user_id', r.user_id,
        'legacy_role', r.role::text
    )
FROM migration_staging.stg_legacy_user_roles_2025 r
LEFT JOIN (
    SELECT DISTINCT legacy_role
    FROM migration_staging.map_legacy_role_attribute_policy
    WHERE apply_flag = TRUE
) p
  ON p.legacy_role = r.role::text
WHERE p.legacy_role IS NULL;

-- ----------------------------------------------------------------------
-- 7) summary
-- ----------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM migration_staging.map_legacy_role_attribute_policy WHERE apply_flag = TRUE) AS applied_role_policy_count,
    (SELECT COUNT(*) FROM employee_attributes ea JOIN migration_staging.map_org mo ON ea.organization_id = mo.new_org_id) AS attribute_count,
    (SELECT COUNT(*)
       FROM employee_attribute_values eav
       JOIN employees e ON e.id = eav.employee_id
       JOIN migration_staging.map_org mo ON e.organization_id = mo.new_org_id) AS attribute_value_count,
    (SELECT COUNT(*) FROM migration_staging.stg_pilot_role_mapping_todo) AS todo_count,
    (SELECT COUNT(*) FROM migration_staging.stg_pilot_roles_unresolved) AS unresolved_count;

COMMIT;
