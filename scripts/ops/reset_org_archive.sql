-- Org Reset Archive (DEV/STAGING 전용)
-- 목적:
--   - 대상 org를 삭제하지 않고 archive 처리한다.
--   - org code를 OLD_CODE__ARCHIVE_yyyymmddhh24miss 형태로 변경하여
--     기존 org code를 신규 org 생성 시 재사용 가능하게 만든다.
-- 주요 정책:
--   - 물리 삭제 금지
--   - allowlist org만 대상
--   - target_env + DB 이름(prod 문자열) 가드
--   - dry_run=true면 변경 없이 영향도만 확인

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
    -- target_org_code: archive 처리할 org code
    -- dry_run: TRUE(미리보기), FALSE(실행)
    ('dev', 'REPLACE_WITH_TARGET_ORG_CODE', TRUE);

CREATE TEMP TABLE ops_reset_allowlist (
    org_code TEXT PRIMARY KEY
) ON COMMIT DROP;

INSERT INTO ops_reset_allowlist (org_code)
VALUES
    -- allowlist org만 reset 허용
    ('REPLACE_WITH_TARGET_ORG_CODE');

-- =====================================================================
-- 1) Hard Guard
-- =====================================================================
DO $$
DECLARE
    v_target_env TEXT;
    v_target_org_code TEXT;
    v_org_count BIGINT;
    v_allowlisted BOOLEAN;
BEGIN
    SELECT p.target_env, p.target_org_code
    INTO v_target_env, v_target_org_code
    FROM ops_reset_params p
    LIMIT 1;

    IF v_target_env NOT IN ('dev', 'staging') THEN
        RAISE EXCEPTION 'target_env must be dev/staging. current=%', v_target_env;
    END IF;

    IF CURRENT_DATABASE() ~* '(prod|production)' THEN
        RAISE EXCEPTION 'production-like database detected: %', CURRENT_DATABASE();
    END IF;

    SELECT EXISTS (
        SELECT 1
        FROM ops_reset_allowlist a
        WHERE a.org_code = v_target_org_code
    ) INTO v_allowlisted;

    IF NOT v_allowlisted THEN
        RAISE EXCEPTION 'target org is not in allowlist. org_code=%', v_target_org_code;
    END IF;

    SELECT COUNT(*)
    INTO v_org_count
    FROM organizations o
    WHERE o.code = v_target_org_code;

    IF v_org_count <> 1 THEN
        RAISE EXCEPTION 'target org count must be exactly 1. org_code=%, count=%', v_target_org_code, v_org_count;
    END IF;
END $$;

-- =====================================================================
-- 2) Target context 생성
-- =====================================================================
CREATE TEMP TABLE ops_reset_target ON COMMIT DROP AS
WITH p AS (
    SELECT * FROM ops_reset_params
), stamp AS (
    SELECT TO_CHAR(CLOCK_TIMESTAMP(), 'YYYYMMDDHH24MISS') AS ts
)
SELECT
    o.id AS organization_id,
    o.name AS organization_name,
    o.code AS original_org_code,
    o.status AS original_org_status,
    p.target_env,
    p.dry_run,
    s.ts AS archive_ts,
    '__ARCHIVE_' || s.ts AS archive_suffix,
    LEFT(
        o.code,
        GREATEST(1, 50 - CHAR_LENGTH('__ARCHIVE_' || s.ts))
    ) || '__ARCHIVE_' || s.ts AS archive_org_code
FROM organizations o
JOIN p ON o.code = p.target_org_code
CROSS JOIN stamp s;

-- archive code 충돌 방지
DO $$
DECLARE
    v_collision BIGINT;
BEGIN
    SELECT COUNT(*)
    INTO v_collision
    FROM organizations o
    JOIN ops_reset_target t ON t.archive_org_code = o.code
    WHERE o.id <> t.organization_id;

    IF v_collision > 0 THEN
        RAISE EXCEPTION 'archive org_code collision detected. archive_org_code=%',
            (SELECT archive_org_code FROM ops_reset_target LIMIT 1);
    END IF;
END $$;

-- =====================================================================
-- 3) 실행 컨텍스트/영향도 출력
-- =====================================================================
SELECT
    NOW() AS started_at,
    CURRENT_DATABASE() AS database_name,
    CURRENT_USER AS db_user,
    t.target_env,
    t.dry_run,
    t.organization_id,
    t.organization_name,
    t.original_org_code,
    t.archive_org_code
FROM ops_reset_target t;

WITH t AS (
    SELECT * FROM ops_reset_target
)
SELECT *
FROM (
    SELECT 'accounts_active_before' AS metric, COUNT(*)::BIGINT AS metric_count
    FROM accounts a
    JOIN t ON a.organization_id = t.organization_id
    WHERE a.status = 'ACTIVE'

    UNION ALL

    SELECT 'employees_active_before', COUNT(*)::BIGINT
    FROM employees e
    JOIN t ON e.organization_id = t.organization_id
    WHERE e.status = 'ACTIVE'

    UNION ALL

    SELECT 'sessions_total_before', COUNT(*)::BIGINT
    FROM evaluation_sessions s
    JOIN t ON s.organization_id = t.organization_id

    UNION ALL

    SELECT 'assignments_total_before', COUNT(*)::BIGINT
    FROM evaluation_assignments a
    JOIN t ON a.organization_id = t.organization_id

    UNION ALL

    SELECT 'responses_total_before', COUNT(*)::BIGINT
    FROM evaluation_responses r
    JOIN t ON r.organization_id = t.organization_id
) q
ORDER BY q.metric;

-- =====================================================================
-- 4) Archive 처리
--    dry_run=true면 WHERE NOT t.dry_run 조건으로 실제 변경 없이 통과
-- =====================================================================
WITH updated_org AS (
    UPDATE organizations o
    SET
        code = t.archive_org_code,
        status = 'INACTIVE',
        updated_at = NOW()
    FROM ops_reset_target t
    WHERE o.id = t.organization_id
      AND NOT t.dry_run
    RETURNING o.id
)
SELECT
    'organizations_archive_update' AS step,
    (SELECT dry_run FROM ops_reset_target LIMIT 1) AS dry_run,
    COUNT(*)::BIGINT AS affected_rows
FROM updated_org;

WITH updated_accounts AS (
    UPDATE accounts a
    SET
        status = 'INACTIVE',
        updated_at = NOW()
    FROM ops_reset_target t
    WHERE a.organization_id = t.organization_id
      AND a.status <> 'INACTIVE'
      AND NOT t.dry_run
    RETURNING a.id
)
SELECT
    'accounts_inactivate' AS step,
    (SELECT dry_run FROM ops_reset_target LIMIT 1) AS dry_run,
    COUNT(*)::BIGINT AS affected_rows
FROM updated_accounts;

WITH updated_employees AS (
    UPDATE employees e
    SET
        status = 'INACTIVE',
        updated_at = NOW()
    FROM ops_reset_target t
    WHERE e.organization_id = t.organization_id
      AND e.status <> 'INACTIVE'
      AND NOT t.dry_run
    RETURNING e.id
)
SELECT
    'employees_inactivate' AS step,
    (SELECT dry_run FROM ops_reset_target LIMIT 1) AS dry_run,
    COUNT(*)::BIGINT AS affected_rows
FROM updated_employees;

-- 참고:
-- user_accounts에는 status 컬럼이 없으므로,
-- employees.status = INACTIVE로 로그인 가능 상태를 차단한다.

-- =====================================================================
-- 5) Post-check
-- =====================================================================
SELECT
    t.organization_id,
    t.original_org_code,
    t.archive_org_code,
    o.code AS current_org_code,
    o.status AS current_org_status,
    t.dry_run
FROM ops_reset_target t
JOIN organizations o ON o.id = t.organization_id;

WITH t AS (
    SELECT * FROM ops_reset_target
)
SELECT *
FROM (
    SELECT 'accounts_active_after' AS metric, COUNT(*)::BIGINT AS metric_count
    FROM accounts a
    JOIN t ON a.organization_id = t.organization_id
    WHERE a.status = 'ACTIVE'

    UNION ALL

    SELECT 'employees_active_after', COUNT(*)::BIGINT
    FROM employees e
    JOIN t ON e.organization_id = t.organization_id
    WHERE e.status = 'ACTIVE'

    UNION ALL

    SELECT 'original_org_code_reusable',
           CASE WHEN EXISTS (
               SELECT 1
               FROM organizations ox
               JOIN t ON ox.code = t.original_org_code
               WHERE ox.id <> t.organization_id
           ) THEN 0 ELSE 1 END::BIGINT
) q
ORDER BY q.metric;

COMMIT;
