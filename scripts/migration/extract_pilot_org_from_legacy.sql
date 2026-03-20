-- Pilot extraction from measured legacy tables (PostgreSQL)
-- 파일럿 기관: 효사랑가족요양병원
-- 기관 식별 기준:
--   - users_2025: c_name / c_name2
--   - KPI: hospital_name (kpi_eval_clinic, kpi_personal_2025), kcol01 (kpi_info_general_2025)

BEGIN;

CREATE SCHEMA IF NOT EXISTS migration_staging;

DROP TABLE IF EXISTS migration_staging.stg_legacy_notice_v2;
DROP TABLE IF EXISTS migration_staging.stg_legacy_kpi_info_general_2025;
DROP TABLE IF EXISTS migration_staging.stg_legacy_kpi_personal_2025;
DROP TABLE IF EXISTS migration_staging.stg_legacy_kpi_eval_clinic;
DROP TABLE IF EXISTS migration_staging.stg_legacy_evaluation_comment_summary;
DROP TABLE IF EXISTS migration_staging.stg_legacy_evaluation_submissions;
DROP TABLE IF EXISTS migration_staging.stg_legacy_admin_custom_targets;
DROP TABLE IF EXISTS migration_staging.stg_legacy_admin_default_targets;
DROP TABLE IF EXISTS migration_staging.stg_legacy_team;
DROP TABLE IF EXISTS migration_staging.stg_legacy_sub_management;
DROP TABLE IF EXISTS migration_staging.stg_legacy_user_roles_2025;
DROP TABLE IF EXISTS migration_staging.stg_legacy_users_2025;
DROP TABLE IF EXISTS migration_staging.stg_legacy_evaluation;
DROP TABLE IF EXISTS migration_staging.stg_legacy_pilot_years;
DROP TABLE IF EXISTS migration_staging.stg_legacy_pilot_user_keys;

CREATE TABLE migration_staging.stg_legacy_users_2025 AS
WITH params AS (
    SELECT '효사랑가족요양병원'::text AS pilot_org_name
)
SELECT u.*
FROM users_2025 u
CROSS JOIN params p
WHERE btrim(COALESCE(u.c_name, '')) = p.pilot_org_name
   OR btrim(COALESCE(u.c_name2, '')) = p.pilot_org_name;

CREATE TABLE migration_staging.stg_legacy_pilot_user_keys AS
SELECT DISTINCT
    u.eval_year,
    u.id AS user_id
FROM migration_staging.stg_legacy_users_2025 u
WHERE u.id IS NOT NULL
  AND btrim(u.id) <> '';

CREATE TABLE migration_staging.stg_legacy_pilot_years AS
SELECT DISTINCT eval_year
FROM migration_staging.stg_legacy_pilot_user_keys;

CREATE TABLE migration_staging.stg_legacy_user_roles_2025 AS
SELECT r.*
FROM user_roles_2025 r
JOIN migration_staging.stg_legacy_pilot_user_keys pu
  ON pu.eval_year = r.eval_year
 AND pu.user_id = r.user_id;

CREATE TABLE migration_staging.stg_legacy_sub_management AS
SELECT s.*
FROM sub_management s
JOIN migration_staging.stg_legacy_pilot_years py
  ON py.eval_year = s.eval_year;

CREATE TABLE migration_staging.stg_legacy_team AS
SELECT t.*
FROM team t
JOIN migration_staging.stg_legacy_pilot_years py
  ON py.eval_year = t.eval_year;

CREATE TABLE migration_staging.stg_legacy_admin_default_targets AS
SELECT d.*
FROM admin_default_targets d
JOIN migration_staging.stg_legacy_pilot_user_keys pe
  ON pe.eval_year = d.eval_year
 AND pe.user_id = d.user_id
JOIN migration_staging.stg_legacy_pilot_user_keys pt
  ON pt.eval_year = d.eval_year
 AND pt.user_id = d.target_id;

CREATE TABLE migration_staging.stg_legacy_admin_custom_targets AS
SELECT c.*
FROM admin_custom_targets c
JOIN migration_staging.stg_legacy_pilot_user_keys pe
  ON pe.eval_year = c.eval_year
 AND pe.user_id = c.user_id
JOIN migration_staging.stg_legacy_pilot_user_keys pt
  ON pt.eval_year = c.eval_year
 AND pt.user_id = c.target_id;

CREATE TABLE migration_staging.stg_legacy_evaluation_submissions AS
SELECT s.*
FROM evaluation_submissions s
JOIN migration_staging.stg_legacy_pilot_user_keys pe
  ON pe.eval_year = s.eval_year
 AND pe.user_id = s.evaluator_id
JOIN migration_staging.stg_legacy_pilot_user_keys pt
  ON pt.eval_year = s.eval_year
 AND pt.user_id = s.target_id;

CREATE TABLE migration_staging.stg_legacy_evaluation_comment_summary AS
SELECT ecs.*
FROM evaluation_comment_summary ecs
JOIN migration_staging.stg_legacy_pilot_user_keys pt
  ON pt.eval_year = ecs.eval_year
 AND pt.user_id = ecs.target_id;

CREATE TABLE migration_staging.stg_legacy_kpi_eval_clinic AS
WITH params AS (
    SELECT '효사랑가족요양병원'::text AS pilot_org_name
)
SELECT k.*
FROM kpi_eval_clinic k
CROSS JOIN params p
JOIN migration_staging.stg_legacy_pilot_years py
  ON py.eval_year = k.eval_year
WHERE btrim(COALESCE(k.hospital_name, '')) = p.pilot_org_name;

CREATE TABLE migration_staging.stg_legacy_kpi_personal_2025 AS
WITH params AS (
    SELECT '효사랑가족요양병원'::text AS pilot_org_name
)
SELECT k.*
FROM kpi_personal_2025 k
CROSS JOIN params p
JOIN migration_staging.stg_legacy_pilot_years py
  ON py.eval_year = k.eval_year
WHERE btrim(COALESCE(k.hospital_name, '')) = p.pilot_org_name;

CREATE TABLE migration_staging.stg_legacy_kpi_info_general_2025 AS
WITH params AS (
    SELECT '효사랑가족요양병원'::text AS pilot_org_name
)
SELECT k.*
FROM kpi_info_general_2025 k
CROSS JOIN params p
WHERE btrim(COALESCE(k.kcol01, '')) = p.pilot_org_name;

CREATE TABLE migration_staging.stg_legacy_notice_v2 AS
SELECT n.*
FROM notice_v2 n
JOIN migration_staging.stg_legacy_pilot_years py
  ON py.eval_year = n.eval_year;

-- 문항 마스터 (answers_json 키 파싱용)
CREATE TABLE migration_staging.stg_legacy_evaluation AS
SELECT e.*
FROM evaluation e
JOIN migration_staging.stg_legacy_pilot_years py
  ON py.eval_year = e.eval_year;

-- answers_json 품질 스냅샷
SELECT
    COUNT(*) AS submission_rows,
    COUNT(*) FILTER (WHERE s.answers_json IS NULL) AS null_answers_json,
    COUNT(*) FILTER (WHERE jsonb_typeof(s.answers_json::jsonb) <> 'object') AS non_object_answers_json,
    COUNT(*) FILTER (WHERE s.answers_json::jsonb ? 'radios') AS has_radios,
    COUNT(*) FILTER (WHERE s.answers_json::jsonb ? 'essays') AS has_essays
FROM migration_staging.stg_legacy_evaluation_submissions s;

-- 추출 건수 요약
SELECT 'stg_legacy_users_2025' AS table_name, COUNT(*) AS cnt FROM migration_staging.stg_legacy_users_2025
UNION ALL SELECT 'stg_legacy_pilot_user_keys', COUNT(*) FROM migration_staging.stg_legacy_pilot_user_keys
UNION ALL SELECT 'stg_legacy_pilot_years', COUNT(*) FROM migration_staging.stg_legacy_pilot_years
UNION ALL SELECT 'stg_legacy_user_roles_2025', COUNT(*) FROM migration_staging.stg_legacy_user_roles_2025
UNION ALL SELECT 'stg_legacy_sub_management', COUNT(*) FROM migration_staging.stg_legacy_sub_management
UNION ALL SELECT 'stg_legacy_team', COUNT(*) FROM migration_staging.stg_legacy_team
UNION ALL SELECT 'stg_legacy_admin_default_targets', COUNT(*) FROM migration_staging.stg_legacy_admin_default_targets
UNION ALL SELECT 'stg_legacy_admin_custom_targets', COUNT(*) FROM migration_staging.stg_legacy_admin_custom_targets
UNION ALL SELECT 'stg_legacy_evaluation_submissions', COUNT(*) FROM migration_staging.stg_legacy_evaluation_submissions
UNION ALL SELECT 'stg_legacy_evaluation_comment_summary', COUNT(*) FROM migration_staging.stg_legacy_evaluation_comment_summary
UNION ALL SELECT 'stg_legacy_kpi_eval_clinic', COUNT(*) FROM migration_staging.stg_legacy_kpi_eval_clinic
UNION ALL SELECT 'stg_legacy_kpi_personal_2025', COUNT(*) FROM migration_staging.stg_legacy_kpi_personal_2025
UNION ALL SELECT 'stg_legacy_kpi_info_general_2025', COUNT(*) FROM migration_staging.stg_legacy_kpi_info_general_2025
UNION ALL SELECT 'stg_legacy_notice_v2', COUNT(*) FROM migration_staging.stg_legacy_notice_v2
UNION ALL SELECT 'stg_legacy_evaluation', COUNT(*) FROM migration_staging.stg_legacy_evaluation
ORDER BY table_name;

COMMIT;
