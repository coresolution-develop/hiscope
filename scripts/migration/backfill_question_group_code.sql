-- Backfill SQL: question/assignment group code 보정 (PostgreSQL)
-- 목적:
--   - 이미 이관된 데이터에서 question_group_code / resolved_question_group_code 누락을 최대한 자동 복구
--   - 서비스 전역 로직 변경 없이 데이터 정합성으로 문항 노출 범위를 맞춤
--
-- 보정 기준:
--   BF-01) 같은 기관 내 "문항 내용(content)"이 단일 group code로 확정되는 경우 그대로 채움
--   BF-02) 아직 NULL이면, 같은 기관에서 "문항 수가 동일하고 group code가 완전한" 참조 템플릿의 문항 순서(rank)로 채움
--   BF-03) assignment는 응답한 문항들의 group code가 1개로 수렴하면 해당 값으로 채움
--   BF-04) 세션/템플릿이 단일 group code로 수렴하면 남은 assignment NULL 채움
--
-- 주의:
--   - BF-02는 템플릿 구조(문항 순서)가 같은 경우에만 안전하다.
--   - 자동 복구 후에도 NULL이 남으면 운영 정책에 따라 수동 매핑(BF-MANUAL)을 추가한다.

BEGIN;

-- BF-01: content 기반 단일 group code 매핑
WITH content_group_map AS (
    SELECT
        q.organization_id,
        q.content,
        MIN(q.question_group_code) AS group_code
    FROM evaluation_questions q
    WHERE q.question_group_code IS NOT NULL
      AND TRIM(q.question_group_code) <> ''
    GROUP BY q.organization_id, q.content
    HAVING COUNT(DISTINCT q.question_group_code) = 1
)
UPDATE evaluation_questions q
SET question_group_code = cgm.group_code
FROM content_group_map cgm
WHERE q.organization_id = cgm.organization_id
  AND q.content = cgm.content
  AND (q.question_group_code IS NULL OR TRIM(q.question_group_code) = '');

-- BF-02: 같은 기관 + 같은 문항 수 템플릿 간 문항 순서(rank) 매핑
WITH target_templates AS (
    SELECT
        q.template_id,
        q.organization_id,
        COUNT(*) AS total_question_count
    FROM evaluation_questions q
    GROUP BY q.template_id, q.organization_id
    HAVING COUNT(*) FILTER (
        WHERE q.question_group_code IS NULL
           OR TRIM(q.question_group_code) = ''
    ) > 0
),
reference_templates AS (
    SELECT
        q.template_id,
        q.organization_id,
        COUNT(*) AS total_question_count
    FROM evaluation_questions q
    GROUP BY q.template_id, q.organization_id
    HAVING COUNT(*) FILTER (
        WHERE q.question_group_code IS NOT NULL
          AND TRIM(q.question_group_code) <> ''
    ) = COUNT(*)
),
candidate_references AS (
    SELECT
        tt.template_id AS target_template_id,
        rt.template_id AS reference_template_id,
        ROW_NUMBER() OVER (
            PARTITION BY tt.template_id
            ORDER BY rt.template_id
        ) AS ref_rank
    FROM target_templates tt
    JOIN reference_templates rt
      ON rt.organization_id = tt.organization_id
     AND rt.total_question_count = tt.total_question_count
     AND rt.template_id <> tt.template_id
),
chosen_reference AS (
    SELECT
        target_template_id,
        reference_template_id
    FROM candidate_references
    WHERE ref_rank = 1
),
target_question_order AS (
    SELECT
        q.id AS question_id,
        q.template_id,
        ROW_NUMBER() OVER (
            PARTITION BY q.template_id
            ORDER BY q.sort_order, q.id
        ) AS question_rank
    FROM evaluation_questions q
),
reference_question_order AS (
    SELECT
        q.template_id,
        q.question_group_code,
        ROW_NUMBER() OVER (
            PARTITION BY q.template_id
            ORDER BY q.sort_order, q.id
        ) AS question_rank
    FROM evaluation_questions q
    WHERE q.question_group_code IS NOT NULL
      AND TRIM(q.question_group_code) <> ''
)
UPDATE evaluation_questions tq
SET question_group_code = rqo.question_group_code
FROM target_question_order tqo
JOIN chosen_reference cr ON cr.target_template_id = tqo.template_id
JOIN reference_question_order rqo
  ON rqo.template_id = cr.reference_template_id
 AND rqo.question_rank = tqo.question_rank
WHERE tq.id = tqo.question_id
  AND (tq.question_group_code IS NULL OR TRIM(tq.question_group_code) = '');

-- BF-MANUAL (선택): 자동 복구 후에도 남는 템플릿은 정책값으로 수동 채움
-- 예시:
-- WITH manual_template_group(template_id, question_group_code) AS (
--     VALUES
--         -- (3, 'AA')
-- )
-- UPDATE evaluation_questions q
-- SET question_group_code = mtg.question_group_code
-- FROM manual_template_group mtg
-- WHERE q.template_id = mtg.template_id
--   AND (q.question_group_code IS NULL OR TRIM(q.question_group_code) = '');

-- BF-03: response item 기반 assignment group 추론
WITH inferred_assignment_group AS (
    SELECT
        ea.id AS assignment_id,
        MIN(eq.question_group_code) AS inferred_group_code
    FROM evaluation_assignments ea
    JOIN evaluation_responses er ON er.assignment_id = ea.id
    JOIN evaluation_response_items eri ON eri.response_id = er.id
    JOIN evaluation_questions eq ON eq.id = eri.question_id
    WHERE eq.question_group_code IS NOT NULL
      AND TRIM(eq.question_group_code) <> ''
    GROUP BY ea.id
    HAVING COUNT(DISTINCT eq.question_group_code) = 1
)
UPDATE evaluation_assignments ea
SET resolved_question_group_code = iag.inferred_group_code
FROM inferred_assignment_group iag
WHERE ea.id = iag.assignment_id
  AND (ea.resolved_question_group_code IS NULL OR TRIM(ea.resolved_question_group_code) = '');

-- BF-04: 세션에서 단일 group code로 수렴하는 경우 남은 assignment NULL 채움
WITH session_single_group AS (
    SELECT
        ea.session_id,
        MIN(ea.resolved_question_group_code) AS single_group_code
    FROM evaluation_assignments ea
    WHERE ea.resolved_question_group_code IS NOT NULL
      AND TRIM(ea.resolved_question_group_code) <> ''
    GROUP BY ea.session_id
    HAVING COUNT(DISTINCT ea.resolved_question_group_code) = 1
)
UPDATE evaluation_assignments ea
SET resolved_question_group_code = ssg.single_group_code
FROM session_single_group ssg
WHERE ea.session_id = ssg.session_id
  AND (ea.resolved_question_group_code IS NULL OR TRIM(ea.resolved_question_group_code) = '');

-- BF-05: 템플릿 문항이 단일 group code인 경우 남은 assignment NULL 채움
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
UPDATE evaluation_assignments ea
SET resolved_question_group_code = tsg.single_group_code
FROM evaluation_sessions es
JOIN template_single_group tsg ON tsg.template_id = es.template_id
WHERE ea.session_id = es.id
  AND (ea.resolved_question_group_code IS NULL OR TRIM(ea.resolved_question_group_code) = '');

-- 결과 스냅샷 1: grouped 템플릿 후보(동일 기관/동일 문항수의 참조 grouped 템플릿 존재)인데 문항 group code 전부 NULL
WITH template_question_stats AS (
    SELECT
        q.organization_id,
        q.template_id,
        COUNT(*) AS total_questions,
        COUNT(*) FILTER (
            WHERE q.question_group_code IS NOT NULL
              AND TRIM(q.question_group_code) <> ''
        ) AS grouped_question_count
    FROM evaluation_questions q
    WHERE q.is_active = TRUE
    GROUP BY q.organization_id, q.template_id
),
grouped_reference_shape AS (
    SELECT
        tqs.organization_id,
        tqs.total_questions
    FROM template_question_stats tqs
    WHERE tqs.grouped_question_count > 0
    GROUP BY tqs.organization_id, tqs.total_questions
),
grouped_template_candidates AS (
    SELECT
        tqs.organization_id,
        tqs.template_id,
        tqs.total_questions,
        tqs.grouped_question_count
    FROM template_question_stats tqs
    JOIN grouped_reference_shape grs
      ON grs.organization_id = tqs.organization_id
     AND grs.total_questions = tqs.total_questions
)
SELECT
    t.id AS template_id,
    t.name AS template_name,
    gtc.total_questions,
    gtc.grouped_question_count
FROM grouped_template_candidates gtc
JOIN evaluation_templates t ON t.id = gtc.template_id
WHERE gtc.grouped_question_count = 0
ORDER BY t.id;

-- 결과 스냅샷 2: grouped assignment 후보(템플릿이 grouped 후보)인데 resolved_question_group_code가 NULL/blank
WITH template_question_stats AS (
    SELECT
        q.organization_id,
        q.template_id,
        COUNT(*) AS total_questions,
        COUNT(*) FILTER (
            WHERE q.question_group_code IS NOT NULL
              AND TRIM(q.question_group_code) <> ''
        ) AS grouped_question_count
    FROM evaluation_questions q
    WHERE q.is_active = TRUE
    GROUP BY q.organization_id, q.template_id
),
grouped_reference_shape AS (
    SELECT
        tqs.organization_id,
        tqs.total_questions
    FROM template_question_stats tqs
    WHERE tqs.grouped_question_count > 0
    GROUP BY tqs.organization_id, tqs.total_questions
),
grouped_template_candidates AS (
    SELECT
        tqs.template_id
    FROM template_question_stats tqs
    JOIN grouped_reference_shape grs
      ON grs.organization_id = tqs.organization_id
     AND grs.total_questions = tqs.total_questions
)
SELECT
    s.id AS session_id,
    s.name AS session_name,
    s.template_id,
    COUNT(*) AS assignment_count,
    SUM(
        CASE
            WHEN a.resolved_question_group_code IS NULL
              OR TRIM(a.resolved_question_group_code) = ''
                THEN 1
            ELSE 0
        END
    ) AS null_or_blank_group_count
FROM evaluation_sessions s
JOIN grouped_template_candidates gtc ON gtc.template_id = s.template_id
JOIN evaluation_assignments a ON a.session_id = s.id
GROUP BY s.id, s.name, s.template_id
HAVING SUM(
    CASE
        WHEN a.resolved_question_group_code IS NULL
          OR TRIM(a.resolved_question_group_code) = ''
            THEN 1
        ELSE 0
    END
) > 0
ORDER BY s.id;

-- 결과 스냅샷 3: response item 문항 group과 assignment group 불일치
SELECT
    COUNT(*) AS mismatch_item_count
FROM evaluation_responses er
JOIN evaluation_assignments ea ON ea.id = er.assignment_id
JOIN evaluation_response_items eri ON eri.response_id = er.id
JOIN evaluation_questions eq ON eq.id = eri.question_id
WHERE ea.resolved_question_group_code IS NOT NULL
  AND TRIM(ea.resolved_question_group_code) <> ''
  AND eq.question_group_code IS NOT NULL
  AND TRIM(eq.question_group_code) <> ''
  AND TRIM(ea.resolved_question_group_code) <> TRIM(eq.question_group_code);

COMMIT;
