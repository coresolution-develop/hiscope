-- Transform: evaluation_submissions -> evaluation_responses / evaluation_response_items
-- 목적:
--   1) 레거시 제출(evaluation_submissions)에서 최신 활성 제출 1건을 선택
--   2) answers_json.radios / answers_json.essays를 response_items로 전개
--   3) 문항키는 evaluation(idx,d2,d3) 기반 map_legacy_question_key를 통해서만 신규 question_id로 연결
--
-- score_value 저장 기준 (코드 근거):
--   - EvaluationResponseService.validateAnswers 는 SCALE 점수를 1~maxScore로 검증한다.
--     (src/main/java/com/hiscope/evaluation/domain/evaluation/response/service/EvaluationResponseService.java)
--   - MyPageService / EvaluationResultService 는 score_value를 그대로 평균 집계한다.
--     (src/main/java/com/hiscope/evaluation/domain/mypage/service/MyPageService.java,
--      src/main/java/com/hiscope/evaluation/domain/evaluation/result/service/EvaluationResultService.java)
--   - QuestionUploadHandler(OPS_BANK) 기본 SCALE maxScore는 5로 세팅된다.
--     (src/main/java/com/hiscope/evaluation/domain/upload/handler/QuestionUploadHandler.java)
--   => evaluation_response_items.score_value 는 raw Likert score(1~5)로 저장한다.
--      AA/AB 환산(AA 문항당 10점, AB 문항당 5점)은 변환 중 파생값으로만 계산하고 저장하지 않는다.
--
-- 주의:
--   - 레거시 key(예: 나39/목41/t43)를 신규 question_id에 직접 직결하지 않는다.
--   - 라디오 답변 라벨은 map_legacy_radio_label_score(확정: 매우우수5/우수4/보통3/미흡2/매우미흡1)로만 변환한다.
--   - data_ev 확정 코드표:
--       A=진료팀장->진료부, B=진료부->경혁팀, C=경혁팀->진료부, D=경혁팀->경혁팀,
--       E=부서장->부서원, F=부서원->부서장, G=부서원->부서원
--   - eval_type_code 확정 코드표:
--       GH_TO_GH, GH_TO_MEDICAL, SUB_HEAD_TO_MEMBER, SUB_MEMBER_TO_HEAD, SUB_MEMBER_TO_MEMBER, GH, MEDICAL
--     (eval_type_code는 submission 원천 컬럼이 아니므로 assignment linking cross-check에서 사용)

BEGIN;

CREATE SCHEMA IF NOT EXISTS migration_staging;

-- ----------------------------------------------------------------------
-- 필수/보조 매핑 테이블
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS migration_staging.map_assignment (
    legacy_assignment_id TEXT PRIMARY KEY,
    new_assignment_id BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS migration_staging.map_question (
    legacy_question_id TEXT PRIMARY KEY,
    new_question_id BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS migration_staging.map_legacy_radio_label_score (
    data_type TEXT NOT NULL,
    label_text TEXT NOT NULL,
    raw_score_value INTEGER NOT NULL,
    note TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (data_type, label_text)
);

CREATE TABLE IF NOT EXISTS migration_staging.map_legacy_question_key (
    legacy_question_key TEXT PRIMARY KEY,
    legacy_question_idx INTEGER NOT NULL,
    legacy_data_type TEXT NOT NULL,
    legacy_category TEXT NOT NULL,
    legacy_prefix TEXT NOT NULL,
    new_question_id BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 확정 raw Likert score 입력
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
-- 1) evaluation 문항 마스터 기반 레거시 question key 카탈로그 생성
--    - d3='섬김'     -> '섬'||idx
--    - d3='배움'     -> '배'||idx
--    - d3='키움'     -> '키'||idx
--    - d3='나눔'     -> '나'||idx
--    - d3='목표관리' -> '목'||idx
--    - d3='주관식'   -> 't'||idx
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS migration_staging.stg_legacy_question_key_catalog;
CREATE TABLE migration_staging.stg_legacy_question_key_catalog AS
SELECT
    e.idx::integer AS legacy_question_idx,
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

DROP TABLE IF EXISTS migration_staging.stg_legacy_question_key_unresolved;
CREATE TABLE migration_staging.stg_legacy_question_key_unresolved AS
SELECT *
FROM migration_staging.stg_legacy_question_key_catalog
WHERE legacy_prefix IS NULL
   OR legacy_question_key IS NULL;

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
    NULL::bigint,
    NOW()
FROM migration_staging.stg_legacy_question_key_catalog c
WHERE c.legacy_question_key IS NOT NULL
ON CONFLICT (legacy_question_key) DO UPDATE
SET legacy_question_idx = EXCLUDED.legacy_question_idx,
    legacy_data_type = EXCLUDED.legacy_data_type,
    legacy_category = EXCLUDED.legacy_category,
    legacy_prefix = EXCLUDED.legacy_prefix,
    updated_at = NOW();

-- map_question(legacy_question_id -> new_question_id)가 준비된 경우 자동 연결
UPDATE migration_staging.map_legacy_question_key qk
SET new_question_id = mq.new_question_id,
    updated_at = NOW()
FROM migration_staging.map_question mq
WHERE qk.new_question_id IS NULL
  AND mq.legacy_question_id = qk.legacy_question_idx::text;

-- ----------------------------------------------------------------------
-- 2) 제출 원천 정규화 + 최신 활성 제출 선택
--    정책:
--      - del_yn='N'만 이관 대상
--      - 동일 (eval_year, evaluator_id, target_id, data_ev, data_type) 내
--        is_active=1 우선 -> version DESC -> updated_at DESC -> created_at DESC -> id DESC
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS migration_staging.stg_legacy_submission_candidates;
CREATE TABLE migration_staging.stg_legacy_submission_candidates AS
SELECT
    s.id::text AS legacy_submission_id,
    concat_ws(':', s.eval_year::text, s.evaluator_id, s.target_id, s.data_ev, s.data_type) AS legacy_assignment_key,
    s.eval_year,
    s.evaluator_id,
    s.target_id,
    s.data_ev,
    s.data_type,
    s.answers_json::jsonb AS answers_json,
    s.answered_count,
    s.radio_count,
    s.total_score,
    s.avg_score,
    s.version,
    s.del_yn,
    s.is_active,
    s.created_at,
    s.updated_at,
    ROW_NUMBER() OVER (
        PARTITION BY s.eval_year, s.evaluator_id, s.target_id, s.data_ev, s.data_type
        ORDER BY
            CASE WHEN COALESCE(s.is_active, 0) = 1 THEN 0 ELSE 1 END,
            COALESCE(s.version, 0) DESC,
            COALESCE(s.updated_at, s.created_at) DESC,
            COALESCE(s.created_at, s.updated_at) DESC,
            s.id DESC
    ) AS row_priority
FROM migration_staging.stg_legacy_evaluation_submissions s
WHERE UPPER(COALESCE(btrim(s.del_yn), 'N')) = 'N';

DROP TABLE IF EXISTS migration_staging.stg_legacy_submission_core;
CREATE TABLE migration_staging.stg_legacy_submission_core AS
SELECT c.*
FROM migration_staging.stg_legacy_submission_candidates c
WHERE c.row_priority = 1;

DROP TABLE IF EXISTS migration_staging.stg_legacy_submission_dropped;
CREATE TABLE migration_staging.stg_legacy_submission_dropped AS
SELECT c.*
FROM migration_staging.stg_legacy_submission_candidates c
WHERE c.row_priority > 1;

-- ----------------------------------------------------------------------
-- 3) assignment 매핑 확인
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS migration_staging.stg_legacy_submission_assignment_unresolved;
CREATE TABLE migration_staging.stg_legacy_submission_assignment_unresolved AS
SELECT
    c.*,
    CASE
        WHEN c.legacy_assignment_key IS NULL OR btrim(c.legacy_assignment_key) = '' THEN 'ASSIGNMENT_KEY_MISSING'
        ELSE 'ASSIGNMENT_MAPPING_MISSING'
    END AS unresolved_reason
FROM migration_staging.stg_legacy_submission_core c
LEFT JOIN migration_staging.map_assignment ma
  ON ma.legacy_assignment_id = c.legacy_assignment_key
WHERE ma.new_assignment_id IS NULL;

-- ----------------------------------------------------------------------
-- 4) response 헤더 upsert
--    - evaluation_submissions는 제출 원천이므로 is_final=TRUE로 반영
-- ----------------------------------------------------------------------
INSERT INTO evaluation_responses (assignment_id, organization_id, is_final, submitted_at)
SELECT
    ma.new_assignment_id,
    ea.organization_id,
    TRUE AS is_final,
    COALESCE(c.updated_at, c.created_at) AS submitted_at
FROM migration_staging.stg_legacy_submission_core c
JOIN migration_staging.map_assignment ma
  ON ma.legacy_assignment_id = c.legacy_assignment_key
JOIN evaluation_assignments ea
  ON ea.id = ma.new_assignment_id
ON CONFLICT (assignment_id) DO UPDATE
SET is_final = TRUE,
    submitted_at = COALESCE(EXCLUDED.submitted_at, evaluation_responses.submitted_at);

DROP TABLE IF EXISTS migration_staging.map_response;
CREATE TABLE migration_staging.map_response AS
SELECT
    c.legacy_submission_id,
    c.legacy_assignment_key,
    er.id AS new_response_id
FROM migration_staging.stg_legacy_submission_core c
JOIN migration_staging.map_assignment ma
  ON ma.legacy_assignment_id = c.legacy_assignment_key
JOIN evaluation_responses er
  ON er.assignment_id = ma.new_assignment_id;

-- ----------------------------------------------------------------------
-- 5) answers_json 전개
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS migration_staging.stg_legacy_submission_items_expanded;
CREATE TABLE migration_staging.stg_legacy_submission_items_expanded AS
WITH radio_items AS (
    SELECT
        c.legacy_submission_id,
        c.legacy_assignment_key,
        c.eval_year,
        c.data_ev,
        c.data_type,
        r.key AS question_key,
        NULLIF(btrim(r.value), '') AS radio_value_text,
        CASE
            WHEN NULLIF(btrim(r.value), '') ~ '^-?[0-9]+$' THEN NULLIF(btrim(r.value), '')::integer
            ELSE NULL
        END AS numeric_score_value,
        mrs.raw_score_value AS mapped_label_raw_score_value,
        COALESCE(
            CASE
                WHEN NULLIF(btrim(r.value), '') ~ '^-?[0-9]+$' THEN NULLIF(btrim(r.value), '')::integer
                ELSE NULL
            END,
            mrs.raw_score_value
        ) AS raw_score_value,
        CASE
            WHEN UPPER(c.data_type) = 'AA' THEN COALESCE(
                CASE
                    WHEN NULLIF(btrim(r.value), '') ~ '^-?[0-9]+$' THEN NULLIF(btrim(r.value), '')::integer
                    ELSE NULL
                END,
                mrs.raw_score_value
            ) * 2
            WHEN UPPER(c.data_type) = 'AB' THEN COALESCE(
                CASE
                    WHEN NULLIF(btrim(r.value), '') ~ '^-?[0-9]+$' THEN NULLIF(btrim(r.value), '')::integer
                    ELSE NULL
                END,
                mrs.raw_score_value
            )
            ELSE NULL
        END AS converted_item_score_value,
        NULL::text AS text_value,
        'radios'::text AS item_source
    FROM migration_staging.stg_legacy_submission_core c
    CROSS JOIN LATERAL jsonb_each_text(COALESCE(c.answers_json -> 'radios', '{}'::jsonb)) r
    LEFT JOIN migration_staging.map_legacy_radio_label_score mrs
      ON mrs.data_type = UPPER(c.data_type)
     AND mrs.label_text = NULLIF(btrim(r.value), '')
),
essay_items AS (
    SELECT
        c.legacy_submission_id,
        c.legacy_assignment_key,
        c.eval_year,
        c.data_ev,
        c.data_type,
        e.key AS question_key,
        NULL::text AS radio_value_text,
        NULL::integer AS numeric_score_value,
        NULL::integer AS mapped_label_raw_score_value,
        NULL::integer AS raw_score_value,
        NULL::integer AS converted_item_score_value,
        NULLIF(btrim(e.value), '') AS text_value,
        'essays'::text AS item_source
    FROM migration_staging.stg_legacy_submission_core c
    CROSS JOIN LATERAL jsonb_each_text(COALESCE(c.answers_json -> 'essays', '{}'::jsonb)) e
),
all_items AS (
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
        WHEN i.question_key ~ '.*[0-9]+$' THEN substring(i.question_key FROM '([0-9]+)$')::integer
        ELSE NULL
    END AS legacy_question_idx
FROM all_items i;

DROP TABLE IF EXISTS migration_staging.stg_legacy_submission_items_joined;
CREATE TABLE migration_staging.stg_legacy_submission_items_joined AS
SELECT
    i.*,
    qk.legacy_data_type AS mapped_data_type,
    qk.legacy_category AS mapped_category,
    qk.new_question_id
FROM migration_staging.stg_legacy_submission_items_expanded i
LEFT JOIN migration_staging.map_legacy_question_key qk
  ON qk.legacy_question_key = i.question_key;

DROP TABLE IF EXISTS migration_staging.stg_legacy_submission_items_unresolved;
CREATE TABLE migration_staging.stg_legacy_submission_items_unresolved AS
SELECT
    i.legacy_submission_id,
    i.legacy_assignment_key,
    i.item_source,
    i.question_key,
    i.question_key_prefix,
    i.legacy_question_idx,
    i.data_type,
    i.mapped_data_type,
    i.raw_score_value AS score_value,
    i.converted_item_score_value,
    i.text_value,
    i.radio_value_text,
    i.numeric_score_value,
    i.mapped_label_raw_score_value,
    CASE
        WHEN i.question_key IS NULL OR btrim(i.question_key) = '' THEN 'QUESTION_KEY_MISSING'
        WHEN i.legacy_question_idx IS NULL THEN 'QUESTION_KEY_INDEX_PARSE_FAILED'
        WHEN i.new_question_id IS NULL AND i.mapped_data_type IS NULL THEN 'QUESTION_KEY_NOT_IN_EVALUATION_MASTER'
        WHEN i.mapped_data_type IS NOT NULL AND UPPER(i.data_type) <> UPPER(i.mapped_data_type) THEN 'SUBMISSION_DATA_TYPE_MISMATCH'
        WHEN i.item_source = 'radios' AND UPPER(COALESCE(i.data_type, '')) NOT IN ('AA', 'AB') THEN 'UNSUPPORTED_DATA_TYPE'
        WHEN i.new_question_id IS NULL THEN 'QUESTION_ID_MAPPING_MISSING'
        WHEN i.item_source = 'radios' AND i.raw_score_value IS NULL AND i.radio_value_text IS NOT NULL THEN 'RADIO_LABEL_SCORE_MAPPING_MISSING'
        WHEN i.item_source = 'essays' AND i.text_value IS NULL THEN 'ESSAY_TEXT_EMPTY'
        ELSE NULL
    END AS unresolved_reason
FROM migration_staging.stg_legacy_submission_items_joined i
WHERE
    i.question_key IS NULL
 OR btrim(i.question_key) = ''
 OR i.legacy_question_idx IS NULL
 OR (i.new_question_id IS NULL AND i.mapped_data_type IS NULL)
 OR (i.mapped_data_type IS NOT NULL AND UPPER(i.data_type) <> UPPER(i.mapped_data_type))
 OR (i.item_source = 'radios' AND UPPER(COALESCE(i.data_type, '')) NOT IN ('AA', 'AB'))
 OR i.new_question_id IS NULL
 OR (i.item_source = 'radios' AND i.raw_score_value IS NULL AND i.radio_value_text IS NOT NULL)
 OR (i.item_source = 'essays' AND i.text_value IS NULL);

-- ----------------------------------------------------------------------
-- 6) resolved item만 적재
-- ----------------------------------------------------------------------
INSERT INTO evaluation_response_items (response_id, question_id, score_value, text_value)
SELECT
    mr.new_response_id,
    i.new_question_id,
    MAX(i.raw_score_value) AS score_value,
    MAX(i.text_value) AS text_value
FROM migration_staging.stg_legacy_submission_items_joined i
JOIN migration_staging.map_response mr
  ON mr.legacy_submission_id = i.legacy_submission_id
WHERE i.new_question_id IS NOT NULL
  AND (i.raw_score_value IS NOT NULL OR i.text_value IS NOT NULL)
  AND NOT EXISTS (
      SELECT 1
      FROM migration_staging.stg_legacy_submission_items_unresolved u
      WHERE u.legacy_submission_id = i.legacy_submission_id
        AND u.item_source = i.item_source
        AND u.question_key = i.question_key
  )
GROUP BY mr.new_response_id, i.new_question_id
ON CONFLICT (response_id, question_id) DO UPDATE
SET score_value = COALESCE(EXCLUDED.score_value, evaluation_response_items.score_value),
    text_value = COALESCE(EXCLUDED.text_value, evaluation_response_items.text_value);

-- ----------------------------------------------------------------------
-- 7) 변환 요약
-- ----------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM migration_staging.stg_legacy_submission_candidates) AS candidate_rows,
    (SELECT COUNT(*) FROM migration_staging.stg_legacy_submission_core) AS selected_latest_rows,
    (SELECT COUNT(*) FROM migration_staging.stg_legacy_submission_dropped) AS dropped_rows,
    (SELECT COUNT(*) FROM migration_staging.stg_legacy_submission_assignment_unresolved) AS unresolved_assignment_rows,
    (SELECT COUNT(*) FROM migration_staging.stg_legacy_submission_items_expanded) AS expanded_item_rows,
    (SELECT COUNT(*) FROM migration_staging.stg_legacy_submission_items_unresolved) AS unresolved_item_rows,
    (SELECT COUNT(*) FROM migration_staging.map_response) AS mapped_response_rows,
    (SELECT COUNT(*) FROM migration_staging.stg_legacy_question_key_unresolved) AS unresolved_question_key_catalog_rows;

COMMIT;
