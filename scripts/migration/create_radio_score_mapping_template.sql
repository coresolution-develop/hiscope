-- Template: map_legacy_radio_label_score 초기 입력
-- 목적:
--   - answers_json.radios 라벨(문자열)을 raw Likert score(1~5)로 명시 매핑
--   - data_type(AA/AB) 분기를 테이블 키로 분리
-- 주의:
--   - 최종 환산(AA 문항당 10점, AB 문항당 5점)은 별도 집계 단계에서 처리
--   - 본 매핑 테이블은 raw score만 관리

BEGIN;

CREATE SCHEMA IF NOT EXISTS migration_staging;

DROP TABLE IF EXISTS migration_staging.map_legacy_radio_label_score;
CREATE TABLE migration_staging.map_legacy_radio_label_score (
    data_type TEXT NOT NULL,
    label_text TEXT NOT NULL,
    raw_score_value INTEGER NOT NULL,
    note TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (data_type, label_text)
);

-- ----------------------------------------------------------------------
-- 확정 raw Likert score (AA/AB 공통)
-- ----------------------------------------------------------------------
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
-- 파일럿 기관(효사랑가족요양병원) submissions의 radio label 분포
-- ----------------------------------------------------------------------
WITH pilot_users AS (
    SELECT DISTINCT u.eval_year, u.id AS user_id
    FROM users_2025 u
    WHERE btrim(COALESCE(u.c_name, '')) = '효사랑가족요양병원'
       OR btrim(COALESCE(u.c_name2, '')) = '효사랑가족요양병원'
), pilot_submissions AS (
    SELECT s.*
    FROM evaluation_submissions s
    JOIN pilot_users pe
      ON pe.eval_year = s.eval_year
     AND pe.user_id = s.evaluator_id
    JOIN pilot_users pt
      ON pt.eval_year = s.eval_year
     AND pt.user_id = s.target_id
)
SELECT
    s.data_type,
    NULLIF(btrim(r.value), '') AS radio_label,
    COUNT(*) AS label_count
FROM pilot_submissions s
CROSS JOIN LATERAL jsonb_each_text(COALESCE(s.answers_json::jsonb -> 'radios', '{}'::jsonb)) r
GROUP BY s.data_type, NULLIF(btrim(r.value), '')
ORDER BY s.data_type, label_count DESC, radio_label;

COMMIT;
