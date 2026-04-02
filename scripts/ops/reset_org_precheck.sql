-- Org Reset Precheck (DEV/STAGING 전용)
-- 목적:
--   - org archive reset 실행 전에 위험 요인을 사전 점검한다.
--   - 이 스크립트는 데이터 변경 DML(UPDATE/DELETE/INSERT to business tables)을 수행하지 않는다.
-- 사용 방법:
--   1) Section 0의 파라미터(target_env/target_org_code/dry_run/allowlist)를 수정
--   2) 전체 실행
--   3) P-08(can_execute_reset_precheck)이 true인지 확인

BEGIN;

-- =====================================================================
-- 0) Parameters (반드시 수정)
-- =====================================================================
CREATE TEMP TABLE ops_reset_params (
    target_env TEXT NOT NULL,
    target_org_code TEXT NOT NULL,
    dry_run BOOLEAN NOT NULL
) ON COMMIT DROP;

INSERT INTO ops_reset_params (target_env, target_org_code, dry_run)
VALUES
    -- target_env: dev | staging 만 허용
    -- target_org_code: reset 대상 org code
    -- dry_run: archive script 실행 시 true(미리보기) -> false(실행)
    ('dev', 'REPLACE_WITH_TARGET_ORG_CODE', TRUE);

CREATE TEMP TABLE ops_reset_allowlist (
    org_code TEXT PRIMARY KEY
) ON COMMIT DROP;

INSERT INTO ops_reset_allowlist (org_code)
VALUES
    -- allowlist org만 reset 가능
    ('REPLACE_WITH_TARGET_ORG_CODE');

-- =====================================================================
-- P-00) Runtime context
-- =====================================================================
SELECT
    NOW() AS checked_at,
    CURRENT_DATABASE() AS database_name,
    CURRENT_USER AS db_user,
    p.target_env,
    p.target_org_code,
    p.dry_run
FROM ops_reset_params p;

-- =====================================================================
-- P-01) Guard signal
-- =====================================================================
WITH p AS (
    SELECT * FROM ops_reset_params
), org_match AS (
    SELECT o.*
    FROM organizations o
    JOIN p ON o.code = p.target_org_code
), stamp AS (
    SELECT TO_CHAR(CLOCK_TIMESTAMP(), 'YYYYMMDDHH24MISS') AS ts
), archive_candidate AS (
    SELECT
        o.id AS organization_id,
        o.code AS original_org_code,
        '__ARCHIVE_' || s.ts AS archive_suffix,
        LEFT(
            o.code,
            GREATEST(1, 50 - CHAR_LENGTH('__ARCHIVE_' || s.ts))
        ) || '__ARCHIVE_' || s.ts AS archive_org_code
    FROM org_match o
    CROSS JOIN stamp s
), archive_collision AS (
    SELECT COUNT(*) AS collision_count
    FROM archive_candidate c
    JOIN organizations o ON o.code = c.archive_org_code
    WHERE o.id <> c.organization_id
)
SELECT
    p.target_env,
    p.target_org_code,
    (p.target_env IN ('dev', 'staging')) AS env_allowed,
    (CURRENT_DATABASE() !~* '(prod|production)') AS db_name_not_prod,
    EXISTS (SELECT 1 FROM ops_reset_allowlist a WHERE a.org_code = p.target_org_code) AS allowlisted,
    (SELECT COUNT(*) FROM org_match) AS target_org_count,
    COALESCE((SELECT collision_count FROM archive_collision), 0) AS archive_code_collision_count
FROM p;

-- =====================================================================
-- P-02) 대상 org 상세
-- =====================================================================
WITH p AS (
    SELECT * FROM ops_reset_params
)
SELECT
    o.id,
    o.code,
    o.name,
    o.status,
    o.organization_type,
    o.organization_profile,
    o.created_at,
    o.updated_at
FROM organizations o
JOIN p ON o.code = p.target_org_code;

-- =====================================================================
-- P-03) 데이터 볼륨 요약(org scope)
-- =====================================================================
WITH p AS (
    SELECT * FROM ops_reset_params
), target_org AS (
    SELECT o.id
    FROM organizations o
    JOIN p ON o.code = p.target_org_code
)
SELECT *
FROM (
    SELECT 'organizations' AS table_name, COUNT(*)::BIGINT AS row_count
    FROM target_org

    UNION ALL

    SELECT 'accounts', COUNT(*)::BIGINT
    FROM accounts a
    JOIN target_org t ON a.organization_id = t.id

    UNION ALL

    SELECT 'departments', COUNT(*)::BIGINT
    FROM departments d
    JOIN target_org t ON d.organization_id = t.id

    UNION ALL

    SELECT 'employees', COUNT(*)::BIGINT
    FROM employees e
    JOIN target_org t ON e.organization_id = t.id

    UNION ALL

    SELECT 'user_accounts', COUNT(*)::BIGINT
    FROM user_accounts ua
    JOIN target_org t ON ua.organization_id = t.id

    UNION ALL

    SELECT 'evaluation_templates', COUNT(*)::BIGINT
    FROM evaluation_templates et
    JOIN target_org t ON et.organization_id = t.id

    UNION ALL

    SELECT 'evaluation_questions', COUNT(*)::BIGINT
    FROM evaluation_questions eq
    JOIN evaluation_templates et ON et.id = eq.template_id
    JOIN target_org t ON et.organization_id = t.id

    UNION ALL

    SELECT 'evaluation_sessions', COUNT(*)::BIGINT
    FROM evaluation_sessions es
    JOIN target_org t ON es.organization_id = t.id

    UNION ALL

    SELECT 'evaluation_relationships', COUNT(*)::BIGINT
    FROM evaluation_relationships er
    JOIN target_org t ON er.organization_id = t.id

    UNION ALL

    SELECT 'evaluation_assignments', COUNT(*)::BIGINT
    FROM evaluation_assignments ea
    JOIN target_org t ON ea.organization_id = t.id

    UNION ALL

    SELECT 'evaluation_responses', COUNT(*)::BIGINT
    FROM evaluation_responses er
    JOIN target_org t ON er.organization_id = t.id

    UNION ALL

    SELECT 'evaluation_response_items', COUNT(*)::BIGINT
    FROM evaluation_response_items eri
    JOIN evaluation_responses er ON er.id = eri.response_id
    JOIN evaluation_assignments ea ON ea.id = er.assignment_id
    JOIN target_org t ON ea.organization_id = t.id

    UNION ALL

    SELECT 'employee_attributes', COUNT(*)::BIGINT
    FROM employee_attributes ea
    JOIN target_org t ON ea.organization_id = t.id

    UNION ALL

    SELECT 'employee_attribute_values', COUNT(*)::BIGINT
    FROM employee_attribute_values eav
    JOIN employees e ON e.id = eav.employee_id
    JOIN target_org t ON e.organization_id = t.id

    UNION ALL

    SELECT 'session_generated_relationships', COUNT(*)::BIGINT
    FROM session_generated_relationships sgr
    JOIN target_org t ON sgr.organization_id = t.id

    UNION ALL

    SELECT 'session_relationship_overrides', COUNT(*)::BIGINT
    FROM session_relationship_overrides sro
    JOIN target_org t ON sro.organization_id = t.id

    UNION ALL

    SELECT 'session_relationship_generation_runs', COUNT(*)::BIGINT
    FROM session_relationship_generation_runs srgr
    JOIN target_org t ON srgr.organization_id = t.id

    UNION ALL

    SELECT 'session_employee_snapshots', COUNT(*)::BIGINT
    FROM session_employee_snapshots ses
    JOIN evaluation_sessions es ON es.id = ses.session_id
    JOIN target_org t ON es.organization_id = t.id

    UNION ALL

    SELECT 'session_participant_overrides', COUNT(*)::BIGINT
    FROM session_participant_overrides spo
    JOIN evaluation_sessions es ON es.id = spo.session_id
    JOIN target_org t ON es.organization_id = t.id

    UNION ALL

    SELECT 'organization_settings', COUNT(*)::BIGINT
    FROM organization_settings os
    JOIN target_org t ON os.organization_id = t.id

    UNION ALL

    SELECT 'upload_histories', COUNT(*)::BIGINT
    FROM upload_histories uh
    JOIN target_org t ON uh.organization_id = t.id

    UNION ALL

    SELECT 'relationship_definition_sets', COUNT(*)::BIGINT
    FROM relationship_definition_sets rds
    JOIN target_org t ON rds.organization_id = t.id

    UNION ALL

    SELECT 'relationship_definition_rules', COUNT(*)::BIGINT
    FROM relationship_definition_rules rdr
    JOIN relationship_definition_sets rds ON rds.id = rdr.set_id
    JOIN target_org t ON rds.organization_id = t.id

    UNION ALL

    SELECT 'relationship_rule_matchers', COUNT(*)::BIGINT
    FROM relationship_rule_matchers rrm
    JOIN relationship_definition_rules rdr ON rdr.id = rrm.rule_id
    JOIN relationship_definition_sets rds ON rds.id = rdr.set_id
    JOIN target_org t ON rds.organization_id = t.id
) q
ORDER BY q.table_name;

-- =====================================================================
-- P-04) 세션 상태 분포(열려 있는 세션 존재 여부)
-- =====================================================================
WITH p AS (
    SELECT * FROM ops_reset_params
), target_org AS (
    SELECT o.id
    FROM organizations o
    JOIN p ON o.code = p.target_org_code
)
SELECT
    es.status,
    COUNT(*) AS session_count
FROM evaluation_sessions es
JOIN target_org t ON es.organization_id = t.id
GROUP BY es.status
ORDER BY es.status;

-- =====================================================================
-- P-05) 로그인 영향 요약
-- =====================================================================
WITH p AS (
    SELECT * FROM ops_reset_params
), target_org AS (
    SELECT o.id
    FROM organizations o
    JOIN p ON o.code = p.target_org_code
)
SELECT
    (SELECT COUNT(*) FROM accounts a JOIN target_org t ON a.organization_id = t.id AND a.status = 'ACTIVE') AS active_admin_accounts,
    (SELECT COUNT(*) FROM employees e JOIN target_org t ON e.organization_id = t.id AND e.status = 'ACTIVE') AS active_employees,
    (SELECT COUNT(*)
     FROM user_accounts ua
     JOIN employees e ON e.id = ua.employee_id
     JOIN target_org t ON ua.organization_id = t.id
     WHERE e.status = 'ACTIVE') AS login_eligible_user_accounts;

-- =====================================================================
-- P-06) 이관 데이터 시그널(정보성)
-- =====================================================================
WITH p AS (
    SELECT * FROM ops_reset_params
), target_org AS (
    SELECT o.id
    FROM organizations o
    JOIN p ON o.code = p.target_org_code
)
SELECT
    COUNT(*) FILTER (WHERE es.name ILIKE '%레거시%' OR es.name ILIKE '%이관%') AS legacy_named_sessions,
    COUNT(*) AS total_sessions
FROM evaluation_sessions es
JOIN target_org t ON es.organization_id = t.id;

-- =====================================================================
-- P-07) archive code 미리보기
-- =====================================================================
WITH p AS (
    SELECT * FROM ops_reset_params
), target_org AS (
    SELECT o.id, o.code
    FROM organizations o
    JOIN p ON o.code = p.target_org_code
), stamp AS (
    SELECT TO_CHAR(CLOCK_TIMESTAMP(), 'YYYYMMDDHH24MISS') AS ts
), candidate AS (
    SELECT
        o.id AS organization_id,
        o.code AS original_org_code,
        LEFT(
            o.code,
            GREATEST(1, 50 - CHAR_LENGTH('__ARCHIVE_' || s.ts))
        ) || '__ARCHIVE_' || s.ts AS archive_org_code
    FROM target_org o
    CROSS JOIN stamp s
)
SELECT
    c.organization_id,
    c.original_org_code,
    c.archive_org_code,
    CHAR_LENGTH(c.archive_org_code) AS archive_org_code_length,
    EXISTS (
        SELECT 1
        FROM organizations ox
        WHERE ox.code = c.archive_org_code
          AND ox.id <> c.organization_id
    ) AS archive_code_collision
FROM candidate c;

-- =====================================================================
-- P-08) 최종 사전판단(자동 판단값)
-- =====================================================================
WITH p AS (
    SELECT * FROM ops_reset_params
), org_count AS (
    SELECT COUNT(*) AS cnt
    FROM organizations o
    JOIN p ON o.code = p.target_org_code
), allowlist_check AS (
    SELECT EXISTS (
        SELECT 1 FROM ops_reset_allowlist a JOIN p ON a.org_code = p.target_org_code
    ) AS ok
), db_guard AS (
    SELECT (CURRENT_DATABASE() !~* '(prod|production)') AS ok
), env_guard AS (
    SELECT (p.target_env IN ('dev', 'staging')) AS ok
    FROM p
)
SELECT
    p.target_env,
    p.target_org_code,
    p.dry_run,
    (SELECT ok FROM env_guard) AS env_allowed,
    (SELECT ok FROM db_guard) AS db_name_not_prod,
    (SELECT ok FROM allowlist_check) AS allowlisted,
    (SELECT cnt FROM org_count) AS target_org_count,
    (
        (SELECT ok FROM env_guard)
        AND (SELECT ok FROM db_guard)
        AND (SELECT ok FROM allowlist_check)
        AND (SELECT cnt FROM org_count) = 1
    ) AS can_execute_reset_precheck
FROM p;

ROLLBACK;
