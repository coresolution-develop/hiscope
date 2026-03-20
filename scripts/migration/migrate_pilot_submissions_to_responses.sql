-- Pilot execution SQL (evaluation_submissions -> responses/items)
-- 대상 기관: 효사랑가족요양병원 (1기관)
-- 전제:
--   - scripts/migration/load_pilot_csv_to_staging.sql 실행 완료
--   - scripts/migration/migrate_pilot_relationships.sql 실행 완료 (map_assignment 준비)
--   - scripts/migration/migrate_pilot_org_master.sql 실행 완료 (map_question 준비)

BEGIN;

CREATE SCHEMA IF NOT EXISTS migration_staging;

DROP TABLE IF EXISTS migration_staging.stg_pilot_submission_unresolved;
CREATE TABLE migration_staging.stg_pilot_submission_unresolved (
    legacy_submission_id TEXT,
    legacy_assignment_id TEXT,
    unresolved_reason TEXT NOT NULL,
    detail_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

DROP TABLE IF EXISTS migration_staging.stg_pilot_submission_item_unresolved;
CREATE TABLE migration_staging.stg_pilot_submission_item_unresolved (
    legacy_submission_id TEXT,
    legacy_assignment_id TEXT,
    item_source TEXT,
    question_key TEXT,
    unresolved_reason TEXT NOT NULL,
    detail_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE OR REPLACE FUNCTION migration_staging.try_parse_jsonb(p_input TEXT)
RETURNS JSONB
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_input IS NULL OR btrim(p_input) = '' THEN
        RETURN '{}'::jsonb;
    END IF;
    RETURN p_input::jsonb;
EXCEPTION
    WHEN OTHERS THEN
        RETURN NULL;
END;
$$;

-- ----------------------------------------------------------------------
-- 0) 라디오 raw score 확정표
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS migration_staging.map_legacy_radio_label_score (
    data_type TEXT NOT NULL,
    label_text TEXT NOT NULL,
    raw_score_value INTEGER NOT NULL,
    note TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (data_type, label_text)
);

INSERT INTO migration_staging.map_legacy_radio_label_score (data_type, label_text, raw_score_value, note, updated_at)
VALUES
    ('AA', '매우우수', 5, 'raw Likert score; AA/AB conversion handled separately', NOW()),
    ('AA', '우수', 4, 'raw Likert score; AA/AB conversion handled separately', NOW()),
    ('AA', '보통', 3, 'raw Likert score; AA/AB conversion handled separately', NOW()),
    ('AA', '미흡', 2, 'raw Likert score; AA/AB conversion handled separately', NOW()),
    ('AA', '매우미흡', 1, 'raw Likert score; AA/AB conversion handled separately', NOW()),
    ('AB', '매우우수', 5, 'raw Likert score; AA/AB conversion handled separately', NOW()),
    ('AB', '우수', 4, 'raw Likert score; AA/AB conversion handled separately', NOW()),
    ('AB', '보통', 3, 'raw Likert score; AA/AB conversion handled separately', NOW()),
    ('AB', '미흡', 2, 'raw Likert score; AA/AB conversion handled separately', NOW()),
    ('AB', '매우미흡', 1, 'raw Likert score; AA/AB conversion handled separately', NOW())
ON CONFLICT (data_type, label_text) DO UPDATE
SET raw_score_value = EXCLUDED.raw_score_value,
    note = EXCLUDED.note,
    updated_at = NOW();

-- ----------------------------------------------------------------------
-- 1) 문항키 카탈로그 구성 (evaluation idx/d2/d3)
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS migration_staging.map_legacy_question_key (
    legacy_question_key TEXT PRIMARY KEY,
    legacy_question_idx INTEGER NOT NULL,
    legacy_data_type TEXT NOT NULL,
    legacy_category TEXT NOT NULL,
    legacy_prefix TEXT NOT NULL,
    new_question_id BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

DROP TABLE IF EXISTS migration_staging.stg_pilot_question_key_catalog;
CREATE TABLE migration_staging.stg_pilot_question_key_catalog AS
SELECT
    e.idx::INTEGER AS legacy_question_idx,
    UPPER(btrim(e.d2)) AS legacy_data_type,
    btrim(e.d3) AS legacy_category,
    CASE
        WHEN btrim(e.d3) = '섬김' THEN '섬'
        WHEN btrim(e.d3) = '배움' THEN '배'
        WHEN btrim(e.d3) = '키움' THEN '키'
        WHEN btrim(e.d3) = '나눔' THEN '나'
        WHEN btrim(e.d3) = '목표관리' THEN '목'
        WHEN btrim(e.d3) = '주관식' THEN 't'
        ELSE NULL
    END AS legacy_prefix,
    CASE
        WHEN btrim(e.d3) = '섬김' THEN '섬' || e.idx::text
        WHEN btrim(e.d3) = '배움' THEN '배' || e.idx::text
        WHEN btrim(e.d3) = '키움' THEN '키' || e.idx::text
        WHEN btrim(e.d3) = '나눔' THEN '나' || e.idx::text
        WHEN btrim(e.d3) = '목표관리' THEN '목' || e.idx::text
        WHEN btrim(e.d3) = '주관식' THEN 't' || e.idx::text
        ELSE NULL
    END AS legacy_question_key
FROM migration_staging.stg_legacy_evaluation e
WHERE e.idx IS NOT NULL;

INSERT INTO migration_staging.map_legacy_question_key (
    legacy_question_key,
    legacy_question_idx,
    legacy_data_type,
    legacy_category,
    legacy_prefix,
    new_question_id,
    updated_at
)
SELECT
    c.legacy_question_key,
    c.legacy_question_idx,
    c.legacy_data_type,
    c.legacy_category,
    c.legacy_prefix,
    NULL::BIGINT,
    NOW()
FROM migration_staging.stg_pilot_question_key_catalog c
WHERE c.legacy_prefix IS NOT NULL
  AND c.legacy_question_key IS NOT NULL
ON CONFLICT (legacy_question_key) DO UPDATE
SET legacy_question_idx = EXCLUDED.legacy_question_idx,
    legacy_data_type = EXCLUDED.legacy_data_type,
    legacy_category = EXCLUDED.legacy_category,
    legacy_prefix = EXCLUDED.legacy_prefix,
    updated_at = NOW();

UPDATE migration_staging.map_legacy_question_key qk
SET new_question_id = mq.new_question_id,
    updated_at = NOW()
FROM migration_staging.map_question mq
WHERE mq.legacy_question_id = qk.legacy_question_idx::TEXT;

INSERT INTO migration_staging.stg_pilot_submission_item_unresolved (
    legacy_submission_id,
    legacy_assignment_id,
    item_source,
    question_key,
    unresolved_reason,
    detail_json
)
SELECT
    NULL,
    NULL,
    'catalog',
    c.legacy_question_key,
    'QUESTION_KEY_PREFIX_UNKNOWN',
    jsonb_build_object(
        'legacy_question_idx', c.legacy_question_idx,
        'legacy_category', c.legacy_category
    )
FROM migration_staging.stg_pilot_question_key_catalog c
WHERE c.legacy_prefix IS NULL
   OR c.legacy_question_key IS NULL;

-- ----------------------------------------------------------------------
-- 2) 제출 원천 최신 활성 1건 선택
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS migration_staging.stg_pilot_submission_candidates;
CREATE TABLE migration_staging.stg_pilot_submission_candidates AS
SELECT
    s.id::TEXT AS legacy_submission_id,
    concat_ws(':', s.eval_year::text, btrim(s.evaluator_id), btrim(s.target_id), UPPER(btrim(s.data_ev)), UPPER(btrim(s.data_type))) AS legacy_assignment_id,
    s.eval_year,
    btrim(s.evaluator_id) AS evaluator_id,
    btrim(s.target_id) AS target_id,
    UPPER(btrim(s.data_ev)) AS data_ev,
    UPPER(btrim(s.data_type)) AS data_type,
    s.answers_json AS answers_json_raw_text,
    pj.answers_json AS answers_json,
    CASE WHEN pj.answers_json IS NULL THEN 1 ELSE 0 END AS answers_json_parse_error_flag,
    s.version,
    s.is_active,
    s.del_yn,
    s.created_at,
    s.updated_at,
    ROW_NUMBER() OVER (
        PARTITION BY s.eval_year, btrim(s.evaluator_id), btrim(s.target_id), UPPER(btrim(s.data_ev)), UPPER(btrim(s.data_type))
        ORDER BY
            CASE WHEN COALESCE(s.is_active, 0) = 1 THEN 0 ELSE 1 END,
            COALESCE(s.version, 0) DESC,
            COALESCE(s.updated_at, s.created_at) DESC,
            s.id DESC
    ) AS row_priority
FROM migration_staging.stg_legacy_evaluation_submissions s
CROSS JOIN LATERAL (
    SELECT migration_staging.try_parse_jsonb(s.answers_json) AS answers_json
) pj
WHERE UPPER(COALESCE(btrim(s.del_yn), 'N')) = 'N';

DROP TABLE IF EXISTS migration_staging.stg_pilot_submission_core;
CREATE TABLE migration_staging.stg_pilot_submission_core AS
SELECT *
FROM migration_staging.stg_pilot_submission_candidates
WHERE row_priority = 1;

INSERT INTO migration_staging.stg_pilot_submission_unresolved (
    legacy_submission_id,
    legacy_assignment_id,
    unresolved_reason,
    detail_json
)
SELECT
    c.legacy_submission_id,
    c.legacy_assignment_id,
    'ANSWERS_JSON_PARSE_FAILED',
    jsonb_build_object(
        'eval_year', c.eval_year,
        'evaluator_id', c.evaluator_id,
        'target_id', c.target_id,
        'data_ev', c.data_ev,
        'data_type', c.data_type,
        'answers_json_raw_preview', left(COALESCE(c.answers_json_raw_text, ''), 500)
    )
FROM migration_staging.stg_pilot_submission_core c
WHERE c.answers_json_parse_error_flag = 1;

INSERT INTO migration_staging.stg_pilot_submission_unresolved (
    legacy_submission_id,
    legacy_assignment_id,
    unresolved_reason,
    detail_json
)
SELECT
    c.legacy_submission_id,
    c.legacy_assignment_id,
    'ASSIGNMENT_MAPPING_MISSING',
    jsonb_build_object(
        'eval_year', c.eval_year,
        'evaluator_id', c.evaluator_id,
        'target_id', c.target_id,
        'data_ev', c.data_ev,
        'data_type', c.data_type
    )
FROM migration_staging.stg_pilot_submission_core c
LEFT JOIN migration_staging.map_assignment ma
  ON ma.legacy_assignment_id = c.legacy_assignment_id
WHERE ma.new_assignment_id IS NULL;

-- ----------------------------------------------------------------------
-- 3) response 헤더 upsert
-- ----------------------------------------------------------------------
INSERT INTO evaluation_responses (assignment_id, organization_id, is_final, submitted_at)
SELECT
    ma.new_assignment_id,
    ea.organization_id,
    TRUE,
    COALESCE(c.updated_at, c.created_at)
FROM migration_staging.stg_pilot_submission_core c
JOIN migration_staging.map_assignment ma
  ON ma.legacy_assignment_id = c.legacy_assignment_id
JOIN evaluation_assignments ea
  ON ea.id = ma.new_assignment_id
ON CONFLICT (assignment_id) DO UPDATE
SET is_final = TRUE,
    submitted_at = COALESCE(EXCLUDED.submitted_at, evaluation_responses.submitted_at),
    updated_at = NOW();

DROP TABLE IF EXISTS migration_staging.map_response;
CREATE TABLE migration_staging.map_response AS
SELECT
    c.legacy_submission_id,
    c.legacy_assignment_id,
    er.id AS new_response_id
FROM migration_staging.stg_pilot_submission_core c
JOIN migration_staging.map_assignment ma
  ON ma.legacy_assignment_id = c.legacy_assignment_id
JOIN evaluation_responses er
  ON er.assignment_id = ma.new_assignment_id;

-- ----------------------------------------------------------------------
-- 4) item 전개
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS migration_staging.stg_pilot_submission_items_expanded;
CREATE TABLE migration_staging.stg_pilot_submission_items_expanded AS
WITH radio_items AS (
    SELECT
        c.legacy_submission_id,
        c.legacy_assignment_id,
        c.eval_year,
        c.data_type,
        r.key AS question_key,
        NULLIF(btrim(r.value), '') AS radio_value_text,
        CASE
            WHEN NULLIF(btrim(r.value), '') ~ '^-?[0-9]+$' THEN NULLIF(btrim(r.value), '')::INTEGER
            ELSE NULL
        END AS numeric_score_value,
        mrs.raw_score_value AS mapped_label_raw_score_value,
        COALESCE(
            CASE
                WHEN NULLIF(btrim(r.value), '') ~ '^-?[0-9]+$' THEN NULLIF(btrim(r.value), '')::INTEGER
                ELSE NULL
            END,
            mrs.raw_score_value
        ) AS raw_score_value,
        CASE
            WHEN UPPER(c.data_type) = 'AA' THEN COALESCE(
                CASE
                    WHEN NULLIF(btrim(r.value), '') ~ '^-?[0-9]+$' THEN NULLIF(btrim(r.value), '')::INTEGER
                    ELSE NULL
                END,
                mrs.raw_score_value
            ) * 2
            WHEN UPPER(c.data_type) = 'AB' THEN COALESCE(
                CASE
                    WHEN NULLIF(btrim(r.value), '') ~ '^-?[0-9]+$' THEN NULLIF(btrim(r.value), '')::INTEGER
                    ELSE NULL
                END,
                mrs.raw_score_value
            )
            ELSE NULL
        END AS converted_item_score_value,
        NULL::TEXT AS text_value,
        'radios'::TEXT AS item_source
    FROM migration_staging.stg_pilot_submission_core c
    CROSS JOIN LATERAL jsonb_each_text(COALESCE(c.answers_json -> 'radios', '{}'::jsonb)) r
    LEFT JOIN migration_staging.map_legacy_radio_label_score mrs
      ON mrs.data_type = UPPER(c.data_type)
     AND mrs.label_text = NULLIF(btrim(r.value), '')
), essay_items AS (
    SELECT
        c.legacy_submission_id,
        c.legacy_assignment_id,
        c.eval_year,
        c.data_type,
        e.key AS question_key,
        NULL::TEXT AS radio_value_text,
        NULL::INTEGER AS numeric_score_value,
        NULL::INTEGER AS mapped_label_raw_score_value,
        NULL::INTEGER AS raw_score_value,
        NULL::INTEGER AS converted_item_score_value,
        NULLIF(btrim(e.value), '') AS text_value,
        'essays'::TEXT AS item_source
    FROM migration_staging.stg_pilot_submission_core c
    CROSS JOIN LATERAL jsonb_each_text(COALESCE(c.answers_json -> 'essays', '{}'::jsonb)) e
), all_items AS (
    SELECT * FROM radio_items
    UNION ALL
    SELECT * FROM essay_items
)
SELECT
    i.*,
    CASE
        WHEN i.question_key ~ '^t[0-9]+$' THEN 't'
        WHEN i.question_key ~ '^[^0-9]+[0-9]+$' THEN regexp_replace(i.question_key, '([0-9]+)$', '')
        ELSE NULL
    END AS question_key_prefix,
    CASE
        WHEN i.question_key ~ '.*[0-9]+$' THEN substring(i.question_key FROM '([0-9]+)$')::INTEGER
        ELSE NULL
    END AS legacy_question_idx
FROM all_items i;

DROP TABLE IF EXISTS migration_staging.stg_pilot_submission_items_joined;
CREATE TABLE migration_staging.stg_pilot_submission_items_joined AS
SELECT
    i.*,
    qk.legacy_data_type AS mapped_data_type,
    qk.legacy_category AS mapped_category,
    qk.new_question_id
FROM migration_staging.stg_pilot_submission_items_expanded i
LEFT JOIN migration_staging.map_legacy_question_key qk
  ON qk.legacy_question_key = i.question_key;

INSERT INTO migration_staging.stg_pilot_submission_item_unresolved (
    legacy_submission_id,
    legacy_assignment_id,
    item_source,
    question_key,
    unresolved_reason,
    detail_json
)
SELECT
    i.legacy_submission_id,
    i.legacy_assignment_id,
    i.item_source,
    i.question_key,
    CASE
        WHEN i.question_key IS NULL OR btrim(i.question_key) = '' THEN 'QUESTION_KEY_MISSING'
        WHEN i.legacy_question_idx IS NULL THEN 'QUESTION_KEY_INDEX_PARSE_FAILED'
        WHEN i.new_question_id IS NULL AND i.mapped_data_type IS NULL THEN 'QUESTION_KEY_NOT_IN_EVALUATION_MASTER'
        WHEN i.mapped_data_type IS NOT NULL AND UPPER(i.data_type) <> UPPER(i.mapped_data_type) THEN 'SUBMISSION_DATA_TYPE_MISMATCH'
        WHEN i.item_source = 'radios' AND UPPER(COALESCE(i.data_type, '')) NOT IN ('AA', 'AB') THEN 'UNSUPPORTED_DATA_TYPE'
        WHEN i.new_question_id IS NULL THEN 'QUESTION_ID_MAPPING_MISSING'
        WHEN i.item_source = 'radios' AND i.raw_score_value IS NULL AND i.radio_value_text IS NOT NULL THEN 'RADIO_LABEL_SCORE_MAPPING_MISSING'
        WHEN i.item_source = 'essays' AND i.text_value IS NULL THEN 'ESSAY_TEXT_EMPTY'
        ELSE 'UNKNOWN'
    END AS unresolved_reason,
    jsonb_build_object(
        'legacy_question_idx', i.legacy_question_idx,
        'data_type', i.data_type,
        'mapped_data_type', i.mapped_data_type,
        'raw_score_value', i.raw_score_value,
        'converted_item_score_value', i.converted_item_score_value,
        'radio_value_text', i.radio_value_text,
        'text_value', i.text_value
    )
FROM migration_staging.stg_pilot_submission_items_joined i
WHERE i.question_key IS NULL
   OR btrim(i.question_key) = ''
   OR i.legacy_question_idx IS NULL
   OR (i.new_question_id IS NULL AND i.mapped_data_type IS NULL)
   OR (i.mapped_data_type IS NOT NULL AND UPPER(i.data_type) <> UPPER(i.mapped_data_type))
   OR (i.item_source = 'radios' AND UPPER(COALESCE(i.data_type, '')) NOT IN ('AA', 'AB'))
   OR i.new_question_id IS NULL
   OR (i.item_source = 'radios' AND i.raw_score_value IS NULL AND i.radio_value_text IS NOT NULL)
   OR (i.item_source = 'essays' AND i.text_value IS NULL);

INSERT INTO evaluation_response_items (response_id, question_id, score_value, text_value)
SELECT
    mr.new_response_id,
    i.new_question_id,
    MAX(i.raw_score_value) AS score_value,
    MAX(i.text_value) AS text_value
FROM migration_staging.stg_pilot_submission_items_joined i
JOIN migration_staging.map_response mr
  ON mr.legacy_submission_id = i.legacy_submission_id
WHERE i.new_question_id IS NOT NULL
  AND (i.raw_score_value IS NOT NULL OR i.text_value IS NOT NULL)
  AND NOT EXISTS (
      SELECT 1
      FROM migration_staging.stg_pilot_submission_item_unresolved u
      WHERE u.legacy_submission_id = i.legacy_submission_id
        AND u.item_source = i.item_source
        AND u.question_key = i.question_key
  )
GROUP BY mr.new_response_id, i.new_question_id
ON CONFLICT (response_id, question_id) DO UPDATE
SET score_value = COALESCE(EXCLUDED.score_value, evaluation_response_items.score_value),
    text_value = COALESCE(EXCLUDED.text_value, evaluation_response_items.text_value),
    updated_at = NOW();

UPDATE evaluation_assignments ea
SET status = 'SUBMITTED',
    submitted_at = COALESCE(sr.submitted_at, ea.submitted_at),
    updated_at = NOW()
FROM (
    SELECT
        ma.new_assignment_id,
        MAX(COALESCE(c.updated_at, c.created_at)) AS submitted_at
    FROM migration_staging.stg_pilot_submission_core c
    JOIN migration_staging.map_assignment ma
      ON ma.legacy_assignment_id = c.legacy_assignment_id
    GROUP BY ma.new_assignment_id
) sr
WHERE ea.id = sr.new_assignment_id;

-- ----------------------------------------------------------------------
-- 5) summary
-- ----------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM migration_staging.stg_pilot_submission_candidates) AS candidate_rows,
    (SELECT COUNT(*) FROM migration_staging.stg_pilot_submission_core) AS selected_latest_rows,
    (SELECT COUNT(*) FROM migration_staging.map_response) AS mapped_response_rows,
    (SELECT COUNT(*) FROM migration_staging.stg_pilot_submission_unresolved) AS submission_unresolved_rows,
    (SELECT COUNT(*) FROM migration_staging.stg_pilot_submission_item_unresolved) AS item_unresolved_rows,
    (SELECT COUNT(*) FROM evaluation_response_items eri
      JOIN evaluation_responses er ON er.id = eri.response_id
      JOIN migration_staging.map_org mo ON er.organization_id = mo.new_org_id) AS inserted_item_rows;

COMMIT;
