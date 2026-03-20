-- Pilot execution SQL (legacy targets -> relationships/assignments)
-- 대상 기관: 효사랑가족요양병원 (1기관)
-- 전제:
--   - scripts/migration/load_pilot_csv_to_staging.sql 실행 완료
--   - scripts/migration/migrate_pilot_org_master.sql 실행 완료
--   - migration_staging.map_org / map_employee / map_template 존재

BEGIN;

CREATE SCHEMA IF NOT EXISTS migration_staging;

-- ----------------------------------------------------------------------
-- 0) 호환 부트스트랩 (V8/V15 미적용 DB 대응)
-- ----------------------------------------------------------------------
ALTER TABLE evaluation_sessions
    ADD COLUMN IF NOT EXISTS relationship_generation_mode VARCHAR(20);
ALTER TABLE evaluation_sessions
    ADD COLUMN IF NOT EXISTS relationship_definition_set_id BIGINT;

ALTER TABLE evaluation_relationships
    ADD COLUMN IF NOT EXISTS resolved_question_group_code VARCHAR(30);

ALTER TABLE evaluation_assignments
    ADD COLUMN IF NOT EXISTS resolved_question_group_code VARCHAR(30);

-- ----------------------------------------------------------------------
-- 1) 확정 코드표 (data_ev / eval_type_code)
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS migration_staging.map_legacy_data_ev (
    data_ev TEXT PRIMARY KEY,
    meaning TEXT NOT NULL,
    relation_type TEXT NOT NULL,
    relation_type_status TEXT NOT NULL,
    note TEXT
);

INSERT INTO migration_staging.map_legacy_data_ev (data_ev, meaning, relation_type, relation_type_status, note)
VALUES
    ('A', '진료팀장 -> 진료부', 'CUSTOM', 'TODO', 'relation_type 세분화는 운영 확정 필요'),
    ('B', '진료부 -> 경혁팀', 'CUSTOM', 'TODO', 'relation_type 세분화는 운영 확정 필요'),
    ('C', '경혁팀 -> 진료부', 'CUSTOM', 'TODO', 'relation_type 세분화는 운영 확정 필요'),
    ('D', '경혁팀 -> 경혁팀', 'CUSTOM', 'TODO', 'relation_type 세분화는 운영 확정 필요'),
    ('E', '부서장 -> 부서원', 'DOWNWARD', 'CONFIRMED', '부서장 하향 평가'),
    ('F', '부서원 -> 부서장', 'UPWARD', 'CONFIRMED', '부서원 상향 평가'),
    ('G', '부서원 -> 부서원', 'PEER', 'CONFIRMED', '부서원 동료 평가')
ON CONFLICT (data_ev) DO UPDATE
SET meaning = EXCLUDED.meaning,
    relation_type = EXCLUDED.relation_type,
    relation_type_status = EXCLUDED.relation_type_status,
    note = EXCLUDED.note;

CREATE TABLE IF NOT EXISTS migration_staging.map_legacy_eval_type_code (
    eval_type_code TEXT PRIMARY KEY,
    meaning TEXT NOT NULL
);

INSERT INTO migration_staging.map_legacy_eval_type_code (eval_type_code, meaning)
VALUES
    ('GH_TO_GH', '경혁팀 -> 경혁팀'),
    ('GH_TO_MEDICAL', '경혁팀 -> 진료부'),
    ('SUB_HEAD_TO_MEMBER', '부서장 -> 부서원'),
    ('SUB_MEMBER_TO_HEAD', '부서원 -> 부서장'),
    ('SUB_MEMBER_TO_MEMBER', '부서원 -> 부서원'),
    ('GH', '평가대상: 경혁팀'),
    ('MEDICAL', '평가대상: 진료부')
ON CONFLICT (eval_type_code) DO UPDATE
SET meaning = EXCLUDED.meaning;

DROP TABLE IF EXISTS migration_staging.stg_pilot_relationships_unresolved;
CREATE TABLE migration_staging.stg_pilot_relationships_unresolved (
    legacy_key TEXT,
    unresolved_reason TEXT NOT NULL,
    detail_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

DROP TABLE IF EXISTS migration_staging.stg_pilot_relation_type_todo;
CREATE TABLE migration_staging.stg_pilot_relation_type_todo (
    data_ev TEXT NOT NULL,
    meaning TEXT NOT NULL,
    current_relation_type TEXT NOT NULL,
    todo_reason TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO migration_staging.stg_pilot_relation_type_todo (data_ev, meaning, current_relation_type, todo_reason)
SELECT
    m.data_ev,
    m.meaning,
    m.relation_type,
    COALESCE(m.note, 'relation_type 추가 확정 필요')
FROM migration_staging.map_legacy_data_ev m
WHERE m.relation_type_status = 'TODO';

-- admin_default_targets CSV 실헤더 기준 보정:
--   현재 파일은 eval_type_code 단일 컬럼만 존재하여
--   관계 생성에 필요한 (eval_year, user_id, target_id, data_ev, data_type) 키가 없음.
--   따라서 기본 자동매핑 관계 생성은 불가하며, 코드 검증 및 unresolved 적재만 수행한다.
INSERT INTO migration_staging.stg_pilot_relationships_unresolved (legacy_key, unresolved_reason, detail_json)
SELECT
    'admin_default_targets',
    'ADMIN_DEFAULT_TARGETS_KEY_COLUMNS_MISSING',
    jsonb_build_object(
        'available_columns', jsonb_build_array('eval_type_code'),
        'required_columns', jsonb_build_array('eval_year', 'user_id', 'target_id', 'data_ev', 'data_type'),
        'row_count', (SELECT COUNT(*) FROM migration_staging.stg_legacy_admin_default_targets)
    );

INSERT INTO migration_staging.stg_pilot_relationships_unresolved (legacy_key, unresolved_reason, detail_json)
SELECT
    concat_ws(':', 'admin_default_targets', btrim(d.eval_type_code)),
    'EVAL_TYPE_CODE_UNKNOWN_IN_DEFAULT_CSV',
    jsonb_build_object('eval_type_code', d.eval_type_code)
FROM migration_staging.stg_legacy_admin_default_targets d
LEFT JOIN migration_staging.map_legacy_eval_type_code met
  ON met.eval_type_code = btrim(d.eval_type_code)
WHERE btrim(COALESCE(d.eval_type_code, '')) <> ''
  AND met.eval_type_code IS NULL;

-- ----------------------------------------------------------------------
-- 1) 세션 키 생성 (eval_year + data_ev + data_type)
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS migration_staging.stg_pilot_session_keys;
CREATE TABLE migration_staging.stg_pilot_session_keys AS
WITH from_submissions AS (
    SELECT DISTINCT
        s.eval_year,
        UPPER(btrim(s.data_ev)) AS data_ev,
        UPPER(btrim(s.data_type)) AS data_type
FROM migration_staging.stg_legacy_evaluation_submissions s
    WHERE s.eval_year IS NOT NULL
      AND btrim(COALESCE(s.data_ev, '')) <> ''
      AND btrim(COALESCE(s.data_type, '')) <> ''
), from_custom AS (
    SELECT DISTINCT
        c.eval_year,
        UPPER(btrim(c.data_ev)) AS data_ev,
        UPPER(btrim(c.data_type)) AS data_type
FROM migration_staging.stg_legacy_admin_custom_targets c
    WHERE c.eval_year IS NOT NULL
      AND btrim(COALESCE(c.data_ev, '')) <> ''
      AND btrim(COALESCE(c.data_type, '')) <> ''
), unioned AS (
    SELECT * FROM from_submissions
    UNION
    SELECT * FROM from_custom
)
SELECT
    u.*,
    mdev.meaning AS data_ev_meaning,
    mdev.relation_type,
    mdev.relation_type_status,
    CASE WHEN mdev.data_ev IS NULL THEN 1 ELSE 0 END AS unknown_data_ev_flag,
    CASE WHEN UPPER(u.data_type) IN ('AA', 'AB') THEN 0 ELSE 1 END AS unknown_data_type_flag
FROM unioned u
LEFT JOIN migration_staging.map_legacy_data_ev mdev
  ON mdev.data_ev = u.data_ev;

INSERT INTO migration_staging.stg_pilot_relationships_unresolved (legacy_key, unresolved_reason, detail_json)
SELECT
    concat_ws(':', s.eval_year::text, s.data_ev, s.data_type),
    CASE
        WHEN s.unknown_data_ev_flag = 1 THEN 'UNKNOWN_DATA_EV'
        WHEN s.unknown_data_type_flag = 1 THEN 'UNKNOWN_DATA_TYPE'
        ELSE 'UNKNOWN'
    END,
    jsonb_build_object(
        'eval_year', s.eval_year,
        'data_ev', s.data_ev,
        'data_type', s.data_type
    )
FROM migration_staging.stg_pilot_session_keys s
WHERE s.unknown_data_ev_flag = 1
   OR s.unknown_data_type_flag = 1;

INSERT INTO evaluation_sessions (
    organization_id,
    name,
    description,
    status,
    start_date,
    end_date,
    allow_resubmit,
    template_id,
    created_by,
    relationship_generation_mode,
    relationship_definition_set_id
)
SELECT
    mo.new_org_id,
    format('레거시 %s %s/%s 세션', s.eval_year, s.data_ev, s.data_type),
    format('효사랑가족요양병원 파일럿 이관: %s (%s)', s.data_ev_meaning, s.data_type),
    'CLOSED',
    NULL,
    NULL,
    FALSE,
    mt.new_template_id,
    NULL,
    'LEGACY',
    NULL
FROM migration_staging.stg_pilot_session_keys s
JOIN migration_staging.map_org mo ON TRUE
LEFT JOIN migration_staging.map_template mt
  ON mt.legacy_eval_year = s.eval_year
WHERE s.unknown_data_ev_flag = 0
  AND s.unknown_data_type_flag = 0
  AND mt.new_template_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM evaluation_sessions es
      WHERE es.organization_id = mo.new_org_id
        AND es.name = format('레거시 %s %s/%s 세션', s.eval_year, s.data_ev, s.data_type)
  );

INSERT INTO migration_staging.stg_pilot_relationships_unresolved (legacy_key, unresolved_reason, detail_json)
SELECT
    concat_ws(':', s.eval_year::text, s.data_ev, s.data_type),
    'TEMPLATE_MAPPING_MISSING_FOR_SESSION',
    jsonb_build_object(
        'eval_year', s.eval_year,
        'data_ev', s.data_ev,
        'data_type', s.data_type
    )
FROM migration_staging.stg_pilot_session_keys s
LEFT JOIN migration_staging.map_template mt
  ON mt.legacy_eval_year = s.eval_year
WHERE s.unknown_data_ev_flag = 0
  AND s.unknown_data_type_flag = 0
  AND mt.new_template_id IS NULL;

DROP TABLE IF EXISTS migration_staging.map_session;
CREATE TABLE migration_staging.map_session (
    legacy_eval_year INTEGER NOT NULL,
    legacy_data_ev TEXT NOT NULL,
    legacy_data_type TEXT NOT NULL,
    new_session_id BIGINT NOT NULL,
    PRIMARY KEY (legacy_eval_year, legacy_data_ev, legacy_data_type)
);

INSERT INTO migration_staging.map_session (legacy_eval_year, legacy_data_ev, legacy_data_type, new_session_id)
SELECT
    s.eval_year,
    s.data_ev,
    s.data_type,
    es.id
FROM migration_staging.stg_pilot_session_keys s
JOIN migration_staging.map_org mo ON TRUE
JOIN evaluation_sessions es
  ON es.organization_id = mo.new_org_id
 AND es.name = format('레거시 %s %s/%s 세션', s.eval_year, s.data_ev, s.data_type)
WHERE s.unknown_data_ev_flag = 0
  AND s.unknown_data_type_flag = 0
ON CONFLICT (legacy_eval_year, legacy_data_ev, legacy_data_type) DO UPDATE
SET new_session_id = EXCLUDED.new_session_id;

-- ----------------------------------------------------------------------
-- 2) 관계 원천 정리 (submission fallback + custom override)
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS migration_staging.stg_pilot_custom_targets_latest;
CREATE TABLE migration_staging.stg_pilot_custom_targets_latest AS
WITH ranked AS (
    SELECT
        c.*,
        ROW_NUMBER() OVER (
            PARTITION BY c.eval_year, c.user_id, c.target_id, c.data_ev, c.data_type
            ORDER BY
                COALESCE(c.updated_at, c.created_at) DESC,
                c.created_at DESC,
                c.id DESC
        ) AS rn
    FROM migration_staging.stg_legacy_admin_custom_targets c
)
SELECT *
FROM ranked
WHERE rn = 1;

DROP TABLE IF EXISTS migration_staging.stg_pilot_submission_relationship_latest;
CREATE TABLE migration_staging.stg_pilot_submission_relationship_latest AS
WITH ranked AS (
    SELECT
        s.*,
        ROW_NUMBER() OVER (
            PARTITION BY s.eval_year, s.evaluator_id, s.target_id, s.data_ev, s.data_type
            ORDER BY
                CASE WHEN COALESCE(s.is_active, 0) = 1 THEN 0 ELSE 1 END,
                COALESCE(s.version, 0) DESC,
                COALESCE(s.updated_at, s.created_at) DESC,
                s.id DESC
        ) AS rn
    FROM migration_staging.stg_legacy_evaluation_submissions s
    WHERE UPPER(COALESCE(btrim(s.del_yn), 'N')) = 'N'
)
SELECT *
FROM ranked
WHERE rn = 1;

INSERT INTO migration_staging.stg_pilot_relationships_unresolved (legacy_key, unresolved_reason, detail_json)
SELECT
    COALESCE(
        concat_ws(':',
            s.eval_year::text,
            btrim(COALESCE(s.evaluator_id, '')),
            btrim(COALESCE(s.target_id, '')),
            UPPER(btrim(COALESCE(s.data_ev, ''))),
            UPPER(btrim(COALESCE(s.data_type, '')))
        ),
        '(null)'
    ) AS legacy_key,
    'RELATIONSHIP_SOURCE_KEY_INCOMPLETE_FROM_SUBMISSIONS',
    jsonb_build_object(
        'submission_id', s.id,
        'eval_year', s.eval_year,
        'evaluator_id', s.evaluator_id,
        'target_id', s.target_id,
        'data_ev', s.data_ev,
        'data_type', s.data_type
    )
FROM migration_staging.stg_pilot_submission_relationship_latest s
WHERE s.eval_year IS NULL
   OR btrim(COALESCE(s.evaluator_id, '')) = ''
   OR btrim(COALESCE(s.target_id, '')) = ''
   OR btrim(COALESCE(s.data_ev, '')) = ''
   OR btrim(COALESCE(s.data_type, '')) = '';

INSERT INTO migration_staging.stg_pilot_relationships_unresolved (legacy_key, unresolved_reason, detail_json)
SELECT
    COALESCE(
        concat_ws(':',
            c.eval_year::text,
            btrim(COALESCE(c.user_id, '')),
            btrim(COALESCE(c.target_id, '')),
            UPPER(btrim(COALESCE(c.data_ev, ''))),
            UPPER(btrim(COALESCE(c.data_type, '')))
        ),
        '(null)'
    ) AS legacy_key,
    'RELATIONSHIP_SOURCE_KEY_INCOMPLETE_FROM_CUSTOM',
    jsonb_build_object(
        'custom_id', c.id,
        'eval_year', c.eval_year,
        'user_id', c.user_id,
        'target_id', c.target_id,
        'data_ev', c.data_ev,
        'data_type', c.data_type
    )
FROM migration_staging.stg_pilot_custom_targets_latest c
WHERE c.eval_year IS NULL
   OR btrim(COALESCE(c.user_id, '')) = ''
   OR btrim(COALESCE(c.target_id, '')) = ''
   OR btrim(COALESCE(c.data_ev, '')) = ''
   OR btrim(COALESCE(c.data_type, '')) = '';

DROP TABLE IF EXISTS migration_staging.stg_pilot_relationship_source;
CREATE TABLE migration_staging.stg_pilot_relationship_source AS
WITH submissions_without_custom AS (
    SELECT
        s.eval_year,
        btrim(s.evaluator_id) AS evaluator_legacy_user_id,
        btrim(s.target_id) AS evaluatee_legacy_user_id,
        UPPER(btrim(s.data_ev)) AS data_ev,
        UPPER(btrim(s.data_type)) AS data_type,
        NULL::TEXT AS eval_type_code,
        'AUTO_GENERATED'::TEXT AS source,
        TRUE AS is_active,
        NULL::TEXT AS reason,
        'SUBMISSION_FALLBACK'::TEXT AS relationship_origin
    FROM migration_staging.stg_pilot_submission_relationship_latest s
    WHERE s.eval_year IS NOT NULL
      AND btrim(COALESCE(s.evaluator_id, '')) <> ''
      AND btrim(COALESCE(s.target_id, '')) <> ''
      AND btrim(COALESCE(s.data_ev, '')) <> ''
      AND btrim(COALESCE(s.data_type, '')) <> ''
      AND NOT EXISTS (
        SELECT 1
        FROM migration_staging.stg_pilot_custom_targets_latest c
        WHERE c.eval_year = s.eval_year
          AND btrim(c.user_id) = btrim(s.evaluator_id)
          AND btrim(c.target_id) = btrim(s.target_id)
          AND UPPER(btrim(c.data_ev)) = UPPER(btrim(s.data_ev))
          AND UPPER(btrim(c.data_type)) = UPPER(btrim(s.data_type))
    )
), customs AS (
    SELECT
        c.eval_year,
        btrim(c.user_id) AS evaluator_legacy_user_id,
        btrim(c.target_id) AS evaluatee_legacy_user_id,
        UPPER(btrim(c.data_ev)) AS data_ev,
        UPPER(btrim(c.data_type)) AS data_type,
        NULLIF(btrim(c.eval_type_code), '') AS eval_type_code,
        'ADMIN_ADDED'::TEXT AS source,
        (COALESCE(c.is_active, 0) = 1) AS is_active,
        NULLIF(btrim(c.reason), '') AS reason,
        'ADMIN_CUSTOM'::TEXT AS relationship_origin
    FROM migration_staging.stg_pilot_custom_targets_latest c
    WHERE c.eval_year IS NOT NULL
      AND btrim(COALESCE(c.user_id, '')) <> ''
      AND btrim(COALESCE(c.target_id, '')) <> ''
      AND btrim(COALESCE(c.data_ev, '')) <> ''
      AND btrim(COALESCE(c.data_type, '')) <> ''
)
SELECT * FROM submissions_without_custom
UNION ALL
SELECT * FROM customs;

INSERT INTO migration_staging.stg_pilot_relationships_unresolved (legacy_key, unresolved_reason, detail_json)
SELECT
    concat_ws(':', s.eval_year::text, s.evaluator_legacy_user_id, s.evaluatee_legacy_user_id, s.data_ev, s.data_type),
    'RELATIONSHIP_SOURCE_INACTIVE_CUSTOM',
    jsonb_build_object(
        'source', s.source,
        'relationship_origin', s.relationship_origin,
        'reason', s.reason
    )
FROM migration_staging.stg_pilot_relationship_source s
WHERE s.source = 'ADMIN_ADDED'
  AND s.is_active = FALSE;

INSERT INTO migration_staging.stg_pilot_relationships_unresolved (legacy_key, unresolved_reason, detail_json)
SELECT
    concat_ws(':', s.eval_year::text, s.evaluator_legacy_user_id, s.evaluatee_legacy_user_id, s.data_ev, s.data_type),
    'EVAL_TYPE_CODE_UNKNOWN',
    jsonb_build_object(
        'eval_type_code', s.eval_type_code,
        'source', s.source
    )
FROM migration_staging.stg_pilot_relationship_source s
LEFT JOIN migration_staging.map_legacy_eval_type_code met
  ON met.eval_type_code = s.eval_type_code
WHERE s.eval_type_code IS NOT NULL
  AND met.eval_type_code IS NULL;

DROP TABLE IF EXISTS migration_staging.stg_pilot_relationship_resolved;
CREATE TABLE migration_staging.stg_pilot_relationship_resolved AS
SELECT
    s.*,
    mdev.meaning AS data_ev_meaning,
    mdev.relation_type,
    mdev.relation_type_status,
    ms.new_session_id,
    mev.new_employee_id AS new_evaluator_id,
    met.new_employee_id AS new_evaluatee_id,
    CASE WHEN mdev.data_ev IS NULL THEN 1 ELSE 0 END AS unknown_data_ev_flag,
    CASE WHEN UPPER(COALESCE(s.data_type, '')) IN ('AA', 'AB') THEN 0 ELSE 1 END AS unknown_data_type_flag
FROM migration_staging.stg_pilot_relationship_source s
LEFT JOIN migration_staging.map_legacy_data_ev mdev
  ON mdev.data_ev = s.data_ev
LEFT JOIN migration_staging.map_session ms
  ON ms.legacy_eval_year = s.eval_year
 AND ms.legacy_data_ev = s.data_ev
 AND ms.legacy_data_type = s.data_type
LEFT JOIN migration_staging.map_employee mev
  ON mev.legacy_eval_year = s.eval_year
 AND mev.legacy_user_id = s.evaluator_legacy_user_id
LEFT JOIN migration_staging.map_employee met
  ON met.legacy_eval_year = s.eval_year
 AND met.legacy_user_id = s.evaluatee_legacy_user_id;

INSERT INTO migration_staging.stg_pilot_relationships_unresolved (legacy_key, unresolved_reason, detail_json)
SELECT
    concat_ws(':', r.eval_year::text, r.evaluator_legacy_user_id, r.evaluatee_legacy_user_id, r.data_ev, r.data_type),
    CASE
        WHEN r.unknown_data_ev_flag = 1 THEN 'UNKNOWN_DATA_EV'
        WHEN r.unknown_data_type_flag = 1 THEN 'UNKNOWN_DATA_TYPE'
        WHEN r.new_session_id IS NULL THEN 'SESSION_MAPPING_MISSING'
        WHEN r.new_evaluator_id IS NULL THEN 'EVALUATOR_MAPPING_MISSING'
        WHEN r.new_evaluatee_id IS NULL THEN 'EVALUATEE_MAPPING_MISSING'
        WHEN r.new_evaluator_id = r.new_evaluatee_id THEN 'SELF_EVALUATION_NOT_ALLOWED'
        ELSE 'UNKNOWN'
    END AS unresolved_reason,
    jsonb_build_object(
        'source', r.source,
        'relationship_origin', r.relationship_origin,
        'is_active', r.is_active,
        'eval_type_code', r.eval_type_code,
        'reason', r.reason,
        'data_ev_meaning', r.data_ev_meaning
    )
FROM migration_staging.stg_pilot_relationship_resolved r
WHERE r.unknown_data_ev_flag = 1
   OR r.unknown_data_type_flag = 1
   OR r.new_session_id IS NULL
   OR r.new_evaluator_id IS NULL
   OR r.new_evaluatee_id IS NULL
   OR r.new_evaluator_id = r.new_evaluatee_id;

INSERT INTO evaluation_relationships (
    session_id,
    organization_id,
    evaluator_id,
    evaluatee_id,
    relation_type,
    source,
    is_active,
    resolved_question_group_code
)
SELECT
    r.new_session_id,
    mo.new_org_id,
    r.new_evaluator_id,
    r.new_evaluatee_id,
    r.relation_type,
    r.source,
    r.is_active,
    NULL
FROM migration_staging.stg_pilot_relationship_resolved r
JOIN migration_staging.map_org mo ON TRUE
WHERE r.unknown_data_ev_flag = 0
  AND r.unknown_data_type_flag = 0
  AND r.new_session_id IS NOT NULL
  AND r.new_evaluator_id IS NOT NULL
  AND r.new_evaluatee_id IS NOT NULL
  AND r.new_evaluator_id <> r.new_evaluatee_id
ON CONFLICT (session_id, evaluator_id, evaluatee_id) DO UPDATE
SET relation_type = EXCLUDED.relation_type,
    source = EXCLUDED.source,
    is_active = EXCLUDED.is_active,
    resolved_question_group_code = EXCLUDED.resolved_question_group_code,
    created_at = evaluation_relationships.created_at;

DROP TABLE IF EXISTS migration_staging.map_relationship;
CREATE TABLE migration_staging.map_relationship (
    legacy_relationship_id TEXT PRIMARY KEY,
    new_relationship_id BIGINT NOT NULL
);

INSERT INTO migration_staging.map_relationship (legacy_relationship_id, new_relationship_id)
SELECT
    concat_ws(':', r.eval_year::text, r.evaluator_legacy_user_id, r.evaluatee_legacy_user_id, r.data_ev, r.data_type) AS legacy_relationship_id,
    er.id AS new_relationship_id
FROM migration_staging.stg_pilot_relationship_resolved r
JOIN evaluation_relationships er
  ON er.session_id = r.new_session_id
 AND er.evaluator_id = r.new_evaluator_id
 AND er.evaluatee_id = r.new_evaluatee_id
WHERE r.new_session_id IS NOT NULL
  AND r.new_evaluator_id IS NOT NULL
  AND r.new_evaluatee_id IS NOT NULL
ON CONFLICT (legacy_relationship_id) DO UPDATE
SET new_relationship_id = EXCLUDED.new_relationship_id;

-- ----------------------------------------------------------------------
-- 3) assignment 생성 (submission 최소 연결키 기준)
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS migration_staging.stg_pilot_assignment_source;
CREATE TABLE migration_staging.stg_pilot_assignment_source AS
WITH ranked AS (
    SELECT
        s.*,
        ROW_NUMBER() OVER (
            PARTITION BY s.eval_year, s.evaluator_id, s.target_id, s.data_ev, s.data_type
            ORDER BY
                CASE WHEN COALESCE(s.is_active, 0) = 1 THEN 0 ELSE 1 END,
                COALESCE(s.version, 0) DESC,
                COALESCE(s.updated_at, s.created_at) DESC,
                s.id DESC
        ) AS rn
    FROM migration_staging.stg_legacy_evaluation_submissions s
    WHERE UPPER(COALESCE(btrim(s.del_yn), 'N')) = 'N'
)
SELECT
    r.eval_year,
    btrim(r.evaluator_id) AS evaluator_legacy_user_id,
    btrim(r.target_id) AS evaluatee_legacy_user_id,
    UPPER(btrim(r.data_ev)) AS data_ev,
    UPPER(btrim(r.data_type)) AS data_type,
    concat_ws(':', r.eval_year::text, btrim(r.evaluator_id), btrim(r.target_id), UPPER(btrim(r.data_ev)), UPPER(btrim(r.data_type))) AS legacy_assignment_id,
    COALESCE(r.updated_at, r.created_at) AS submitted_at
FROM ranked r
WHERE r.rn = 1
  AND r.eval_year IS NOT NULL
  AND btrim(COALESCE(r.evaluator_id, '')) <> ''
  AND btrim(COALESCE(r.target_id, '')) <> ''
  AND btrim(COALESCE(r.data_ev, '')) <> ''
  AND btrim(COALESCE(r.data_type, '')) <> '';

INSERT INTO migration_staging.stg_pilot_relationships_unresolved (legacy_key, unresolved_reason, detail_json)
WITH ranked AS (
    SELECT
        s.*,
        ROW_NUMBER() OVER (
            PARTITION BY s.eval_year, s.evaluator_id, s.target_id, s.data_ev, s.data_type
            ORDER BY
                CASE WHEN COALESCE(s.is_active, 0) = 1 THEN 0 ELSE 1 END,
                COALESCE(s.version, 0) DESC,
                COALESCE(s.updated_at, s.created_at) DESC,
                s.id DESC
        ) AS rn
    FROM migration_staging.stg_legacy_evaluation_submissions s
    WHERE UPPER(COALESCE(btrim(s.del_yn), 'N')) = 'N'
)
SELECT
    COALESCE(
        concat_ws(':',
            r.eval_year::text,
            btrim(COALESCE(r.evaluator_id, '')),
            btrim(COALESCE(r.target_id, '')),
            UPPER(btrim(COALESCE(r.data_ev, ''))),
            UPPER(btrim(COALESCE(r.data_type, '')))
        ),
        '(null)'
    ) AS legacy_key,
    'ASSIGNMENT_KEY_INCOMPLETE_FROM_SUBMISSIONS',
    jsonb_build_object(
        'submission_id', r.id,
        'eval_year', r.eval_year,
        'evaluator_id', r.evaluator_id,
        'target_id', r.target_id,
        'data_ev', r.data_ev,
        'data_type', r.data_type
    )
FROM ranked r
WHERE r.rn = 1
  AND (
      r.eval_year IS NULL
      OR btrim(COALESCE(r.evaluator_id, '')) = ''
      OR btrim(COALESCE(r.target_id, '')) = ''
      OR btrim(COALESCE(r.data_ev, '')) = ''
      OR btrim(COALESCE(r.data_type, '')) = ''
  );

DROP TABLE IF EXISTS migration_staging.stg_pilot_assignment_resolved;
CREATE TABLE migration_staging.stg_pilot_assignment_resolved AS
SELECT
    s.*,
    ms.new_session_id,
    mev.new_employee_id AS new_evaluator_id,
    met.new_employee_id AS new_evaluatee_id,
    mr.new_relationship_id
FROM migration_staging.stg_pilot_assignment_source s
LEFT JOIN migration_staging.map_session ms
  ON ms.legacy_eval_year = s.eval_year
 AND ms.legacy_data_ev = s.data_ev
 AND ms.legacy_data_type = s.data_type
LEFT JOIN migration_staging.map_employee mev
  ON mev.legacy_eval_year = s.eval_year
 AND mev.legacy_user_id = s.evaluator_legacy_user_id
LEFT JOIN migration_staging.map_employee met
  ON met.legacy_eval_year = s.eval_year
 AND met.legacy_user_id = s.evaluatee_legacy_user_id
LEFT JOIN migration_staging.map_relationship mr
  ON mr.legacy_relationship_id = s.legacy_assignment_id;

INSERT INTO migration_staging.stg_pilot_relationships_unresolved (legacy_key, unresolved_reason, detail_json)
SELECT
    a.legacy_assignment_id,
    CASE
        WHEN a.new_session_id IS NULL THEN 'ASSIGNMENT_SESSION_MAPPING_MISSING'
        WHEN a.new_evaluator_id IS NULL THEN 'ASSIGNMENT_EVALUATOR_MAPPING_MISSING'
        WHEN a.new_evaluatee_id IS NULL THEN 'ASSIGNMENT_EVALUATEE_MAPPING_MISSING'
        WHEN a.new_evaluator_id = a.new_evaluatee_id THEN 'ASSIGNMENT_SELF_EVALUATION_NOT_ALLOWED'
        ELSE 'UNKNOWN'
    END,
    jsonb_build_object(
        'eval_year', a.eval_year,
        'data_ev', a.data_ev,
        'data_type', a.data_type
    )
FROM migration_staging.stg_pilot_assignment_resolved a
WHERE a.new_session_id IS NULL
   OR a.new_evaluator_id IS NULL
   OR a.new_evaluatee_id IS NULL
   OR a.new_evaluator_id = a.new_evaluatee_id;

INSERT INTO evaluation_assignments (
    session_id,
    organization_id,
    relationship_id,
    evaluator_id,
    evaluatee_id,
    status,
    submitted_at,
    resolved_question_group_code
)
SELECT
    a.new_session_id,
    mo.new_org_id,
    a.new_relationship_id,
    a.new_evaluator_id,
    a.new_evaluatee_id,
    'SUBMITTED',
    a.submitted_at,
    NULL
FROM migration_staging.stg_pilot_assignment_resolved a
JOIN migration_staging.map_org mo ON TRUE
WHERE a.new_session_id IS NOT NULL
  AND a.new_evaluator_id IS NOT NULL
  AND a.new_evaluatee_id IS NOT NULL
  AND a.new_evaluator_id <> a.new_evaluatee_id
  AND NOT EXISTS (
      SELECT 1
      FROM evaluation_assignments ea
      WHERE ea.session_id = a.new_session_id
        AND ea.evaluator_id = a.new_evaluator_id
        AND ea.evaluatee_id = a.new_evaluatee_id
  );

UPDATE evaluation_assignments ea
SET
    relationship_id = COALESCE(ea.relationship_id, a.new_relationship_id),
    status = 'SUBMITTED',
    submitted_at = COALESCE(a.submitted_at, ea.submitted_at),
    resolved_question_group_code = NULL,
    updated_at = NOW()
FROM migration_staging.stg_pilot_assignment_resolved a
WHERE ea.session_id = a.new_session_id
  AND ea.evaluator_id = a.new_evaluator_id
  AND ea.evaluatee_id = a.new_evaluatee_id;

DROP TABLE IF EXISTS migration_staging.map_assignment;
CREATE TABLE migration_staging.map_assignment (
    legacy_assignment_id TEXT PRIMARY KEY,
    new_assignment_id BIGINT NOT NULL
);

INSERT INTO migration_staging.map_assignment (legacy_assignment_id, new_assignment_id)
WITH assignment_pick AS (
    SELECT
        ea.session_id,
        ea.evaluator_id,
        ea.evaluatee_id,
        MAX(ea.id) AS picked_assignment_id
    FROM evaluation_assignments ea
    GROUP BY ea.session_id, ea.evaluator_id, ea.evaluatee_id
)
SELECT
    a.legacy_assignment_id,
    ap.picked_assignment_id AS new_assignment_id
FROM migration_staging.stg_pilot_assignment_resolved a
JOIN assignment_pick ap
  ON ap.session_id = a.new_session_id
 AND ap.evaluator_id = a.new_evaluator_id
 AND ap.evaluatee_id = a.new_evaluatee_id
WHERE a.new_session_id IS NOT NULL
  AND a.new_evaluator_id IS NOT NULL
  AND a.new_evaluatee_id IS NOT NULL
ON CONFLICT (legacy_assignment_id) DO UPDATE
SET new_assignment_id = EXCLUDED.new_assignment_id;

INSERT INTO migration_staging.stg_pilot_relationships_unresolved (legacy_key, unresolved_reason, detail_json)
SELECT
    a.legacy_assignment_id,
    'ASSIGNMENT_MAPPING_MISSING',
    jsonb_build_object(
        'eval_year', a.eval_year,
        'evaluator_legacy_user_id', a.evaluator_legacy_user_id,
        'evaluatee_legacy_user_id', a.evaluatee_legacy_user_id,
        'data_ev', a.data_ev,
        'data_type', a.data_type
    )
FROM migration_staging.stg_pilot_assignment_resolved a
LEFT JOIN migration_staging.map_assignment ma
  ON ma.legacy_assignment_id = a.legacy_assignment_id
WHERE a.new_session_id IS NOT NULL
  AND a.new_evaluator_id IS NOT NULL
  AND a.new_evaluatee_id IS NOT NULL
  AND ma.new_assignment_id IS NULL;

-- ----------------------------------------------------------------------
-- 4) summary
-- ----------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM migration_staging.map_session) AS mapped_session_count,
    (SELECT COUNT(*) FROM migration_staging.map_relationship) AS mapped_relationship_count,
    (SELECT COUNT(*) FROM migration_staging.map_assignment) AS mapped_assignment_count,
    (SELECT COUNT(*) FROM migration_staging.stg_pilot_relation_type_todo) AS relation_type_todo_count,
    (SELECT COUNT(*) FROM migration_staging.stg_pilot_relationships_unresolved) AS unresolved_count;

COMMIT;
