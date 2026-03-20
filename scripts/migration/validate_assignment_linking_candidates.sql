-- Validate: assignment linking candidate quality (staging CSV 기준)
-- 기준 키:
--   (eval_year, evaluator_id, target_id, data_ev, data_type)
-- 대상 범위:
--   migration_staging.stg_legacy_evaluation_submissions (효사랑가족요양병원 필터 반영본)
-- 확정 data_ev 코드표:
--   A=진료팀장->진료부, B=진료부->경혁팀, C=경혁팀->진료부, D=경혁팀->경혁팀,
--   E=부서장->부서원, F=부서원->부서장, G=부서원->부서원
-- 확정 eval_type_code 코드표:
--   GH_TO_GH, GH_TO_MEDICAL, SUB_HEAD_TO_MEMBER, SUB_MEMBER_TO_HEAD, SUB_MEMBER_TO_MEMBER, GH, MEDICAL

-- ----------------------------------------------------------------------
-- 1) 제출 복합키 품질 + data_ev 코드 유효성
-- ----------------------------------------------------------------------
WITH allowed_data_ev AS (
    SELECT *
    FROM (VALUES
        ('A', '진료팀장 -> 진료부'),
        ('B', '진료부 -> 경혁팀'),
        ('C', '경혁팀 -> 진료부'),
        ('D', '경혁팀 -> 경혁팀'),
        ('E', '부서장 -> 부서원'),
        ('F', '부서원 -> 부서장'),
        ('G', '부서원 -> 부서원')
    ) t(code, meaning)
), grouped AS (
    SELECT
        s.eval_year,
        s.evaluator_id,
        s.target_id,
        s.data_ev,
        s.data_type,
        COUNT(*) AS group_row_count,
        COUNT(*) FILTER (WHERE UPPER(COALESCE(btrim(s.del_yn), 'N')) = 'N') AS non_deleted_count,
        COUNT(*) FILTER (
            WHERE UPPER(COALESCE(btrim(s.del_yn), 'N')) = 'N'
              AND COALESCE(s.is_active, 0) = 1
        ) AS active_non_deleted_count,
        COUNT(DISTINCT COALESCE(s.version, 0)) AS version_count,
        MAX(COALESCE(s.version, 0)) AS max_version
    FROM migration_staging.stg_legacy_evaluation_submissions s
    GROUP BY s.eval_year, s.evaluator_id, s.target_id, s.data_ev, s.data_type
), grouped_with_code AS (
    SELECT
        g.*,
        ade.meaning AS data_ev_meaning,
        CASE WHEN ade.code IS NULL THEN 1 ELSE 0 END AS unknown_data_ev_flag
    FROM grouped g
    LEFT JOIN allowed_data_ev ade
      ON ade.code = g.data_ev
)
SELECT
    COUNT(*) AS total_groups,
    COUNT(*) FILTER (WHERE group_row_count = 1) AS unique_groups,
    COUNT(*) FILTER (WHERE group_row_count > 1) AS duplicate_groups,
    COUNT(*) FILTER (WHERE active_non_deleted_count > 1) AS active_conflict_groups,
    COUNT(*) FILTER (WHERE non_deleted_count = 0) AS deleted_only_groups,
    COUNT(*) FILTER (WHERE unknown_data_ev_flag = 1) AS unknown_data_ev_groups,
    SUM(group_row_count) AS total_rows_in_groups,
    SUM(CASE WHEN group_row_count = 1 THEN group_row_count ELSE 0 END) AS rows_in_unique_groups,
    SUM(CASE WHEN group_row_count > 1 THEN group_row_count ELSE 0 END) AS rows_in_duplicate_groups
FROM grouped_with_code;

WITH allowed_data_ev AS (
    SELECT *
    FROM (VALUES
        ('A', '진료팀장 -> 진료부'),
        ('B', '진료부 -> 경혁팀'),
        ('C', '경혁팀 -> 진료부'),
        ('D', '경혁팀 -> 경혁팀'),
        ('E', '부서장 -> 부서원'),
        ('F', '부서원 -> 부서장'),
        ('G', '부서원 -> 부서원')
    ) t(code, meaning)
), grouped AS (
    SELECT
        s.eval_year,
        s.evaluator_id,
        s.target_id,
        s.data_ev,
        s.data_type,
        COUNT(*) AS group_row_count,
        COUNT(*) FILTER (WHERE UPPER(COALESCE(btrim(s.del_yn), 'N')) = 'N') AS non_deleted_count,
        COUNT(*) FILTER (
            WHERE UPPER(COALESCE(btrim(s.del_yn), 'N')) = 'N'
              AND COALESCE(s.is_active, 0) = 1
        ) AS active_non_deleted_count,
        COUNT(DISTINCT COALESCE(s.version, 0)) AS version_count,
        MAX(COALESCE(s.version, 0)) AS max_version,
        MAX(COALESCE(s.updated_at, s.created_at)) AS latest_change_at
    FROM migration_staging.stg_legacy_evaluation_submissions s
    GROUP BY s.eval_year, s.evaluator_id, s.target_id, s.data_ev, s.data_type
)
SELECT
    g.eval_year,
    g.evaluator_id,
    g.target_id,
    g.data_ev,
    COALESCE(ade.meaning, 'UNKNOWN_DATA_EV') AS data_ev_meaning,
    g.data_type,
    g.group_row_count,
    g.non_deleted_count,
    g.active_non_deleted_count,
    g.version_count,
    g.max_version,
    g.latest_change_at,
    CASE
        WHEN ade.code IS NULL THEN 'UNKNOWN_DATA_EV'
        WHEN g.active_non_deleted_count > 1 THEN 'ACTIVE_CONFLICT'
        WHEN g.group_row_count > 1 THEN 'DUPLICATE'
        ELSE 'UNIQUE'
    END AS group_status
FROM grouped g
LEFT JOIN allowed_data_ev ade
  ON ade.code = g.data_ev
WHERE g.group_row_count > 1
   OR g.active_non_deleted_count > 1
   OR ade.code IS NULL
ORDER BY
    CASE WHEN ade.code IS NULL THEN 0 ELSE 1 END,
    g.active_non_deleted_count DESC,
    g.group_row_count DESC,
    g.latest_change_at DESC,
    g.eval_year,
    g.evaluator_id,
    g.target_id,
    g.data_ev,
    g.data_type;

-- ----------------------------------------------------------------------
-- 2) eval_type_code 유효성 검증 (default/custom 원천)
-- ----------------------------------------------------------------------
WITH allowed_eval_type_code AS (
    SELECT *
    FROM (VALUES
        ('GH_TO_GH', '경혁팀 -> 경혁팀'),
        ('GH_TO_MEDICAL', '경혁팀 -> 진료부'),
        ('SUB_HEAD_TO_MEMBER', '부서장 -> 부서원'),
        ('SUB_MEMBER_TO_HEAD', '부서원 -> 부서장'),
        ('SUB_MEMBER_TO_MEMBER', '부서원 -> 부서원'),
        ('GH', '평가대상: 경혁팀'),
        ('MEDICAL', '평가대상: 진료부')
    ) t(code, meaning)
), candidate_default AS (
    SELECT
        'stg_legacy_admin_default_targets'::text AS source_table,
        NULL::INTEGER AS eval_year,
        NULL::TEXT AS user_id,
        NULL::TEXT AS target_id,
        d.eval_type_code
    FROM migration_staging.stg_legacy_admin_default_targets d
), candidate_custom AS (
    SELECT
        'stg_legacy_admin_custom_targets'::text AS source_table,
        c.eval_year,
        c.user_id,
        c.target_id,
        c.eval_type_code
    FROM migration_staging.stg_legacy_admin_custom_targets c
), union_targets AS (
    SELECT * FROM candidate_default
    UNION ALL
    SELECT * FROM candidate_custom
)
SELECT
    u.source_table,
    u.eval_type_code,
    COALESCE(a.meaning, 'UNKNOWN_EVAL_TYPE_CODE') AS eval_type_meaning,
    COUNT(*) AS row_count,
    CASE WHEN a.code IS NULL THEN 'UNKNOWN' ELSE 'KNOWN' END AS code_status
FROM union_targets u
LEFT JOIN allowed_eval_type_code a
  ON a.code = u.eval_type_code
GROUP BY u.source_table, u.eval_type_code, COALESCE(a.meaning, 'UNKNOWN_EVAL_TYPE_CODE'), CASE WHEN a.code IS NULL THEN 'UNKNOWN' ELSE 'KNOWN' END
ORDER BY u.source_table, code_status DESC, u.eval_type_code;
