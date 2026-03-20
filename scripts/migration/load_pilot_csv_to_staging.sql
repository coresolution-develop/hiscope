-- Load pilot CSV extracts to PostgreSQL staging (psql script)
-- 기준:
--   - 파일럿 기관: 효사랑가족요양병원
--   - CSV 실제 헤더가 최우선
--   - UTF-8 / NULL은 빈 문자열('')
-- 주의:
--   - admin_default_targets CSV는 eval_type_code 단일 컬럼만 제공됨
--   - kpi_info_general_2025 CSV는 현재 미제공(빈 staging 생성)

BEGIN;

CREATE SCHEMA IF NOT EXISTS migration_staging;

-- ----------------------------------------------------------------------
-- 0) raw CSV tables (header-aligned)
-- ----------------------------------------------------------------------
DROP TABLE IF EXISTS migration_staging.raw_legacy_admin_default_targets;
DROP TABLE IF EXISTS migration_staging.raw_legacy_users_2025;
DROP TABLE IF EXISTS migration_staging.raw_legacy_user_roles_2025;
DROP TABLE IF EXISTS migration_staging.raw_legacy_sub_management;
DROP TABLE IF EXISTS migration_staging.raw_legacy_team;
DROP TABLE IF EXISTS migration_staging.raw_legacy_evaluation;
DROP TABLE IF EXISTS migration_staging.raw_legacy_admin_custom_targets;
DROP TABLE IF EXISTS migration_staging.raw_legacy_evaluation_submissions;
DROP TABLE IF EXISTS migration_staging.raw_legacy_evaluation_comment_summary;
DROP TABLE IF EXISTS migration_staging.raw_legacy_kpi_eval_clinic;
DROP TABLE IF EXISTS migration_staging.raw_legacy_kpi_personal_2025;
DROP TABLE IF EXISTS migration_staging.raw_legacy_notice_v2;

CREATE TABLE migration_staging.raw_legacy_admin_default_targets (
    eval_type_code TEXT
);

CREATE TABLE migration_staging.raw_legacy_users_2025 (
    idx TEXT,
    c_name TEXT,
    c_name2 TEXT,
    sub_code TEXT,
    team_code TEXT,
    position TEXT,
    id TEXT,
    pwd TEXT,
    name TEXT,
    create_at TEXT,
    delete_at TEXT,
    phone TEXT,
    del_yn TEXT,
    eval_year TEXT
);

CREATE TABLE migration_staging.raw_legacy_user_roles_2025 (
    idx TEXT,
    user_id TEXT,
    role TEXT,
    eval_year TEXT
);

CREATE TABLE migration_staging.raw_legacy_sub_management (
    idx TEXT,
    sub_name TEXT,
    sub_code TEXT,
    eval_year TEXT
);

CREATE TABLE migration_staging.raw_legacy_team (
    idx TEXT,
    team_name TEXT,
    team_code TEXT,
    eval_year TEXT
);

CREATE TABLE migration_staging.raw_legacy_evaluation (
    idx TEXT,
    d1 TEXT,
    d2 TEXT,
    d3 TEXT,
    eval_year TEXT
);

CREATE TABLE migration_staging.raw_legacy_admin_custom_targets (
    id TEXT,
    eval_year TEXT,
    user_id TEXT,
    target_id TEXT,
    eval_type_code TEXT,
    data_ev TEXT,
    data_type TEXT,
    form_id TEXT,
    reason TEXT,
    is_active TEXT,
    created_at TEXT,
    updated_at TEXT
);

CREATE TABLE migration_staging.raw_legacy_evaluation_submissions (
    id TEXT,
    eval_year TEXT,
    evaluator_id TEXT,
    target_id TEXT,
    data_ev TEXT,
    data_type TEXT,
    answers_json TEXT,
    answered_count TEXT,
    radio_count TEXT,
    total_score TEXT,
    avg_score TEXT,
    version TEXT,
    del_yn TEXT,
    is_active TEXT,
    created_at TEXT,
    updated_at TEXT,
    updated_by TEXT,
    uq_active_key TEXT
);

CREATE TABLE migration_staging.raw_legacy_evaluation_comment_summary (
    eval_year TEXT,
    target_id TEXT,
    data_ev TEXT,
    kind TEXT,
    input_hash TEXT,
    essay_count TEXT,
    submission_count TEXT,
    locale TEXT,
    model TEXT,
    summary TEXT,
    status TEXT,
    error_msg TEXT,
    created_at TEXT,
    updated_at TEXT
);

CREATE TABLE migration_staging.raw_legacy_kpi_eval_clinic (
    id TEXT,
    eval_year TEXT,
    hospital_name TEXT,
    emp_id TEXT,
    kpi_i20 TEXT,
    kpi_ii15 TEXT,
    kpi_iii15 TEXT,
    multi_gh25 TEXT,
    multi_chief25 TEXT,
    multi_sum50 TEXT,
    total_score TEXT,
    percentile TEXT,
    eval_grade TEXT,
    created_by TEXT,
    updated_by TEXT,
    created_at TEXT,
    updated_at TEXT
);

CREATE TABLE migration_staging.raw_legacy_kpi_personal_2025 (
    id TEXT,
    eval_year TEXT,
    hospital_name TEXT,
    emp_id TEXT,
    total_score TEXT,
    bed_occ_2024_pct TEXT,
    bed_occ_2025_pct TEXT,
    bed_occ_yoy_pct TEXT,
    bed_occ_remarks TEXT,
    bed_occ_2024_score TEXT,
    bed_occ_yoy_score TEXT,
    bed_occ_subtotal_a TEXT,
    day_rev_2024 TEXT,
    day_rev_2025 TEXT,
    day_rev_yoy_pct TEXT,
    day_rev_remarks TEXT,
    day_rev_yoy_score TEXT,
    day_rev_target_score TEXT,
    day_rev_subtotal_b TEXT,
    sales_2024 TEXT,
    sales_2025 TEXT,
    sales_yoy_pct TEXT,
    sales_yoy_score TEXT,
    sales_target_score TEXT,
    sales_subtotal_c TEXT,
    finance_remarks TEXT,
    finance_total TEXT,
    guardian_sat_5 TEXT,
    patient_sat_5 TEXT,
    cs_total_10 TEXT,
    incident_rate_2024 TEXT,
    incident_rate_2025 TEXT,
    incident_diff_pt TEXT,
    incident_curr_score TEXT,
    incident_diff_score TEXT,
    incident_total TEXT,
    incident_remarks TEXT,
    promo_goal_2025 TEXT,
    promo_personal_count TEXT,
    promo_daily_avg TEXT,
    promo_score_1 TEXT,
    link_goal TEXT,
    link_inpatient_cnt TEXT,
    link_funeral_cnt TEXT,
    link_total_cnt TEXT,
    link_personal_score_6 TEXT,
    link_dept_rate_pct TEXT,
    link_dept_score_1 TEXT,
    link_total_score_7 TEXT,
    link_remarks TEXT,
    act5_goal_2025 TEXT,
    act5_personal_count TEXT,
    act5_score_5 TEXT,
    qi_topic TEXT,
    qi_role TEXT,
    qi_role_score_2 TEXT,
    qi_final_org TEXT,
    qi_award TEXT,
    qi_dept_award_score_2 TEXT,
    qi_dept_part_score_1 TEXT,
    qi_total_score_3 TEXT,
    qi_remarks TEXT,
    edu_goal TEXT,
    edu_personal_rate_pct TEXT,
    edu_personal_score_2 TEXT,
    edu_dept_rate_pct TEXT,
    edu_dept_score_1 TEXT,
    edu_total_score_3 TEXT,
    edu_remarks TEXT,
    club_goal TEXT,
    club_personal_count TEXT,
    club_personal_score_3 TEXT,
    club_dept_rate_pct TEXT,
    club_dept_score_1 TEXT,
    club_dept_remarks TEXT,
    volunteer_score_4 TEXT,
    book_goal TEXT,
    book_attend_count TEXT,
    book_attend_score_2 TEXT,
    book_present_count TEXT,
    book_present_score_1 TEXT,
    book_total_score_3 TEXT,
    book_remarks TEXT,
    created_at TEXT,
    updated_at TEXT
);

CREATE TABLE migration_staging.raw_legacy_notice_v2 (
    id TEXT,
    eval_year TEXT,
    title TEXT,
    body_md TEXT,
    pinned TEXT,
    sort_order TEXT,
    publish_from TEXT,
    publish_to TEXT,
    is_active TEXT,
    version_tag TEXT,
    created_at TEXT,
    updated_at TEXT
);

\copy migration_staging.raw_legacy_admin_default_targets FROM '/Users/leesumin/admin_default_targets_202603191616.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8', NULL '');
\copy migration_staging.raw_legacy_users_2025 FROM '/Users/leesumin/users_2025_202603191619.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8', NULL '');
\copy migration_staging.raw_legacy_user_roles_2025 FROM '/Users/leesumin/user_roles_2025_202603191621.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8', NULL '');
\copy migration_staging.raw_legacy_sub_management FROM '/Users/leesumin/sub_management_202603191621.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8', NULL '');
\copy migration_staging.raw_legacy_team FROM '/Users/leesumin/team_202603191622.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8', NULL '');
\copy migration_staging.raw_legacy_evaluation FROM '/Users/leesumin/evaluation_202603191622.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8', NULL '');
\copy migration_staging.raw_legacy_admin_custom_targets FROM '/Users/leesumin/admin_custom_targets_202603191650.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8', NULL '');
\copy migration_staging.raw_legacy_evaluation_submissions FROM '/Users/leesumin/evaluation_submissions_202603191651.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8', NULL '');
\copy migration_staging.raw_legacy_evaluation_comment_summary FROM '/Users/leesumin/evaluation_comment_summary_202603191652.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8', NULL '');
\copy migration_staging.raw_legacy_kpi_eval_clinic FROM '/Users/leesumin/kpi_eval_clinic_202603191653.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8', NULL '');
\copy migration_staging.raw_legacy_kpi_personal_2025 FROM '/Users/leesumin/kpi_personal_2025_202603191656.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8', NULL '');
\copy migration_staging.raw_legacy_notice_v2 FROM '/Users/leesumin/notice_v2_202603191656.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8', NULL '');

CREATE OR REPLACE FUNCTION migration_staging.try_parse_jsonb(p_input TEXT)
RETURNS JSONB
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_input IS NULL OR btrim(p_input) = '' THEN
        RETURN NULL;
    END IF;
    RETURN p_input::jsonb;
EXCEPTION
    WHEN OTHERS THEN
        RETURN NULL;
END;
$$;

-- ----------------------------------------------------------------------
-- 1) normalized staging tables used by migration SQL
-- ----------------------------------------------------------------------
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
SELECT
    CASE WHEN btrim(COALESCE(u.idx, '')) ~ '^[0-9]+$' THEN btrim(u.idx)::INTEGER ELSE NULL END AS idx,
    NULLIF(btrim(COALESCE(u.c_name, '')), '') AS c_name,
    NULLIF(btrim(COALESCE(u.c_name2, '')), '') AS c_name2,
    NULLIF(btrim(COALESCE(u.sub_code, '')), '') AS sub_code,
    NULLIF(btrim(COALESCE(u.team_code, '')), '') AS team_code,
    NULLIF(btrim(COALESCE(u.position, '')), '') AS position,
    NULLIF(btrim(COALESCE(u.id, '')), '') AS id,
    NULLIF(btrim(COALESCE(u.pwd, '')), '') AS pwd,
    NULLIF(btrim(COALESCE(u.name, '')), '') AS name,
    CASE WHEN btrim(COALESCE(u.create_at, '')) = '' THEN NULL ELSE u.create_at::timestamp END AS create_at,
    CASE WHEN btrim(COALESCE(u.delete_at, '')) = '' THEN NULL ELSE u.delete_at::timestamp END AS delete_at,
    NULLIF(btrim(COALESCE(u.phone, '')), '') AS phone,
    NULLIF(btrim(COALESCE(u.del_yn, '')), '') AS del_yn,
    CASE WHEN btrim(COALESCE(u.eval_year, '')) ~ '^[0-9]+$' THEN btrim(u.eval_year)::INTEGER ELSE NULL END AS eval_year
FROM migration_staging.raw_legacy_users_2025 u
WHERE btrim(COALESCE(u.c_name, '')) = '효사랑가족요양병원'
   OR btrim(COALESCE(u.c_name2, '')) = '효사랑가족요양병원';

CREATE TABLE migration_staging.stg_legacy_pilot_user_keys AS
SELECT DISTINCT
    u.eval_year,
    u.id AS user_id
FROM migration_staging.stg_legacy_users_2025 u
WHERE u.eval_year IS NOT NULL
  AND u.id IS NOT NULL
  AND btrim(u.id) <> '';

CREATE TABLE migration_staging.stg_legacy_pilot_years AS
SELECT DISTINCT eval_year
FROM migration_staging.stg_legacy_pilot_user_keys
WHERE eval_year IS NOT NULL;

CREATE TABLE migration_staging.stg_legacy_user_roles_2025 AS
WITH parsed AS (
    SELECT
        CASE WHEN btrim(COALESCE(r.idx, '')) ~ '^[0-9]+$' THEN btrim(r.idx)::INTEGER ELSE NULL END AS idx,
        NULLIF(btrim(COALESCE(r.user_id, '')), '') AS user_id,
        NULLIF(btrim(COALESCE(r.role, '')), '') AS role,
        CASE WHEN btrim(COALESCE(r.eval_year, '')) ~ '^[0-9]+$' THEN btrim(r.eval_year)::INTEGER ELSE NULL END AS eval_year
    FROM migration_staging.raw_legacy_user_roles_2025 r
)
SELECT p.*
FROM parsed p
JOIN migration_staging.stg_legacy_pilot_user_keys pu
  ON pu.eval_year = p.eval_year
 AND pu.user_id = p.user_id;

CREATE TABLE migration_staging.stg_legacy_sub_management AS
WITH parsed AS (
    SELECT
        CASE WHEN btrim(COALESCE(s.idx, '')) ~ '^[0-9]+$' THEN btrim(s.idx)::INTEGER ELSE NULL END AS idx,
        NULLIF(btrim(COALESCE(s.sub_name, '')), '') AS sub_name,
        NULLIF(btrim(COALESCE(s.sub_code, '')), '') AS sub_code,
        CASE WHEN btrim(COALESCE(s.eval_year, '')) ~ '^[0-9]+$' THEN btrim(s.eval_year)::INTEGER ELSE NULL END AS eval_year
    FROM migration_staging.raw_legacy_sub_management s
)
SELECT p.*
FROM parsed p
JOIN migration_staging.stg_legacy_pilot_years py
  ON py.eval_year = p.eval_year;

CREATE TABLE migration_staging.stg_legacy_team AS
WITH parsed AS (
    SELECT
        CASE WHEN btrim(COALESCE(t.idx, '')) ~ '^[0-9]+$' THEN btrim(t.idx)::INTEGER ELSE NULL END AS idx,
        NULLIF(btrim(COALESCE(t.team_name, '')), '') AS team_name,
        NULLIF(btrim(COALESCE(t.team_code, '')), '') AS team_code,
        CASE WHEN btrim(COALESCE(t.eval_year, '')) ~ '^[0-9]+$' THEN btrim(t.eval_year)::INTEGER ELSE NULL END AS eval_year
    FROM migration_staging.raw_legacy_team t
)
SELECT p.*
FROM parsed p
JOIN migration_staging.stg_legacy_pilot_years py
  ON py.eval_year = p.eval_year;

CREATE TABLE migration_staging.stg_legacy_evaluation AS
WITH parsed AS (
    SELECT
        CASE WHEN btrim(COALESCE(e.idx, '')) ~ '^[0-9]+$' THEN btrim(e.idx)::INTEGER ELSE NULL END AS idx,
        NULLIF(btrim(COALESCE(e.d1, '')), '') AS d1,
        NULLIF(btrim(COALESCE(e.d2, '')), '') AS d2,
        NULLIF(btrim(COALESCE(e.d3, '')), '') AS d3,
        CASE WHEN btrim(COALESCE(e.eval_year, '')) ~ '^[0-9]+$' THEN btrim(e.eval_year)::INTEGER ELSE NULL END AS eval_year
    FROM migration_staging.raw_legacy_evaluation e
)
SELECT p.*
FROM parsed p
JOIN migration_staging.stg_legacy_pilot_years py
  ON py.eval_year = p.eval_year;

CREATE TABLE migration_staging.stg_legacy_admin_default_targets AS
SELECT DISTINCT
    NULLIF(btrim(COALESCE(d.eval_type_code, '')), '') AS eval_type_code
FROM migration_staging.raw_legacy_admin_default_targets d
WHERE btrim(COALESCE(d.eval_type_code, '')) <> '';

CREATE TABLE migration_staging.stg_legacy_admin_custom_targets AS
WITH parsed AS (
    SELECT
        CASE WHEN btrim(COALESCE(c.id, '')) ~ '^[0-9]+$' THEN btrim(c.id)::BIGINT ELSE NULL END AS id,
        CASE WHEN btrim(COALESCE(c.eval_year, '')) ~ '^[0-9]+$' THEN btrim(c.eval_year)::INTEGER ELSE NULL END AS eval_year,
        NULLIF(btrim(COALESCE(c.user_id, '')), '') AS user_id,
        NULLIF(btrim(COALESCE(c.target_id, '')), '') AS target_id,
        NULLIF(btrim(COALESCE(c.eval_type_code, '')), '') AS eval_type_code,
        NULLIF(btrim(COALESCE(c.data_ev, '')), '') AS data_ev,
        NULLIF(btrim(COALESCE(c.data_type, '')), '') AS data_type,
        CASE WHEN btrim(COALESCE(c.form_id, '')) ~ '^[0-9]+$' THEN btrim(c.form_id)::BIGINT ELSE NULL END AS form_id,
        NULLIF(btrim(COALESCE(c.reason, '')), '') AS reason,
        CASE WHEN btrim(COALESCE(c.is_active, '')) ~ '^-?[0-9]+$' THEN btrim(c.is_active)::INTEGER ELSE NULL END AS is_active,
        CASE WHEN btrim(COALESCE(c.created_at, '')) = '' THEN NULL ELSE c.created_at::timestamp END AS created_at,
        CASE WHEN btrim(COALESCE(c.updated_at, '')) = '' THEN NULL ELSE c.updated_at::timestamp END AS updated_at
    FROM migration_staging.raw_legacy_admin_custom_targets c
)
SELECT p.*
FROM parsed p
JOIN migration_staging.stg_legacy_pilot_user_keys pe
  ON pe.eval_year = p.eval_year
 AND pe.user_id = p.user_id
JOIN migration_staging.stg_legacy_pilot_user_keys pt
  ON pt.eval_year = p.eval_year
 AND pt.user_id = p.target_id;

CREATE TABLE migration_staging.stg_legacy_evaluation_submissions AS
WITH parsed AS (
    SELECT
        CASE WHEN btrim(COALESCE(s.id, '')) ~ '^[0-9]+$' THEN btrim(s.id)::BIGINT ELSE NULL END AS id,
        CASE WHEN btrim(COALESCE(s.eval_year, '')) ~ '^[0-9]+$' THEN btrim(s.eval_year)::INTEGER ELSE NULL END AS eval_year,
        NULLIF(btrim(COALESCE(s.evaluator_id, '')), '') AS evaluator_id,
        NULLIF(btrim(COALESCE(s.target_id, '')), '') AS target_id,
        NULLIF(btrim(COALESCE(s.data_ev, '')), '') AS data_ev,
        NULLIF(btrim(COALESCE(s.data_type, '')), '') AS data_type,
        s.answers_json AS answers_json,
        CASE WHEN btrim(COALESCE(s.answered_count, '')) ~ '^-?[0-9]+$' THEN btrim(s.answered_count)::INTEGER ELSE NULL END AS answered_count,
        CASE WHEN btrim(COALESCE(s.radio_count, '')) ~ '^-?[0-9]+$' THEN btrim(s.radio_count)::INTEGER ELSE NULL END AS radio_count,
        CASE WHEN btrim(COALESCE(s.total_score, '')) ~ '^-?[0-9]+$' THEN btrim(s.total_score)::INTEGER ELSE NULL END AS total_score,
        CASE WHEN btrim(COALESCE(s.avg_score, '')) ~ '^-?[0-9]+([.][0-9]+)?$' THEN btrim(s.avg_score)::NUMERIC(10,3) ELSE NULL END AS avg_score,
        CASE WHEN btrim(COALESCE(s.version, '')) ~ '^-?[0-9]+$' THEN btrim(s.version)::INTEGER ELSE NULL END AS version,
        NULLIF(btrim(COALESCE(s.del_yn, '')), '') AS del_yn,
        CASE WHEN btrim(COALESCE(s.is_active, '')) ~ '^-?[0-9]+$' THEN btrim(s.is_active)::INTEGER ELSE NULL END AS is_active,
        CASE WHEN btrim(COALESCE(s.created_at, '')) = '' THEN NULL ELSE s.created_at::timestamp END AS created_at,
        CASE WHEN btrim(COALESCE(s.updated_at, '')) = '' THEN NULL ELSE s.updated_at::timestamp END AS updated_at,
        NULLIF(btrim(COALESCE(s.updated_by, '')), '') AS updated_by,
        NULLIF(btrim(COALESCE(s.uq_active_key, '')), '') AS uq_active_key
    FROM migration_staging.raw_legacy_evaluation_submissions s
)
SELECT p.*
FROM parsed p
JOIN migration_staging.stg_legacy_pilot_user_keys pe
  ON pe.eval_year = p.eval_year
 AND pe.user_id = p.evaluator_id
JOIN migration_staging.stg_legacy_pilot_user_keys pt
  ON pt.eval_year = p.eval_year
 AND pt.user_id = p.target_id;

CREATE TABLE migration_staging.stg_legacy_evaluation_comment_summary AS
WITH parsed AS (
    SELECT
        CASE WHEN btrim(COALESCE(s.eval_year, '')) ~ '^[0-9]+$' THEN btrim(s.eval_year)::INTEGER ELSE NULL END AS eval_year,
        NULLIF(btrim(COALESCE(s.target_id, '')), '') AS target_id,
        NULLIF(btrim(COALESCE(s.data_ev, '')), '') AS data_ev,
        NULLIF(btrim(COALESCE(s.kind, '')), '') AS kind,
        NULLIF(btrim(COALESCE(s.input_hash, '')), '') AS input_hash,
        CASE WHEN btrim(COALESCE(s.essay_count, '')) ~ '^-?[0-9]+$' THEN btrim(s.essay_count)::INTEGER ELSE NULL END AS essay_count,
        CASE WHEN btrim(COALESCE(s.submission_count, '')) ~ '^-?[0-9]+$' THEN btrim(s.submission_count)::INTEGER ELSE NULL END AS submission_count,
        NULLIF(btrim(COALESCE(s.locale, '')), '') AS locale,
        NULLIF(btrim(COALESCE(s.model, '')), '') AS model,
        s.summary AS summary,
        NULLIF(btrim(COALESCE(s.status, '')), '') AS status,
        NULLIF(btrim(COALESCE(s.error_msg, '')), '') AS error_msg,
        CASE WHEN btrim(COALESCE(s.created_at, '')) = '' THEN NULL ELSE s.created_at::timestamp END AS created_at,
        CASE WHEN btrim(COALESCE(s.updated_at, '')) = '' THEN NULL ELSE s.updated_at::timestamp END AS updated_at
    FROM migration_staging.raw_legacy_evaluation_comment_summary s
)
SELECT p.*
FROM parsed p
JOIN migration_staging.stg_legacy_pilot_user_keys pt
  ON pt.eval_year = p.eval_year
 AND pt.user_id = p.target_id;

CREATE TABLE migration_staging.stg_legacy_kpi_eval_clinic AS
SELECT *
FROM migration_staging.raw_legacy_kpi_eval_clinic k
WHERE btrim(COALESCE(k.hospital_name, '')) = '효사랑가족요양병원';

CREATE TABLE migration_staging.stg_legacy_kpi_personal_2025 AS
SELECT *
FROM migration_staging.raw_legacy_kpi_personal_2025 k
WHERE btrim(COALESCE(k.hospital_name, '')) = '효사랑가족요양병원';

CREATE TABLE migration_staging.stg_legacy_kpi_info_general_2025 (
    kcol01 TEXT,
    kcol02 TEXT,
    kcol03 TEXT,
    kcol04 TEXT,
    kcol05 TEXT,
    kcol06 TEXT,
    kcol07 TEXT,
    kcol08 TEXT,
    kcol09 TEXT,
    kcol10 TEXT,
    kcol11 TEXT,
    kcol12 TEXT,
    kcol13 TEXT,
    kcol14 TEXT,
    kcol15 TEXT,
    kcol16 TEXT,
    kcol17 TEXT
);

CREATE TABLE migration_staging.stg_legacy_notice_v2 AS
WITH parsed AS (
    SELECT
        CASE WHEN btrim(COALESCE(n.id, '')) ~ '^[0-9]+$' THEN btrim(n.id)::INTEGER ELSE NULL END AS id,
        CASE WHEN btrim(COALESCE(n.eval_year, '')) ~ '^[0-9]+$' THEN btrim(n.eval_year)::INTEGER ELSE NULL END AS eval_year,
        NULLIF(btrim(COALESCE(n.title, '')), '') AS title,
        n.body_md AS body_md,
        CASE WHEN btrim(COALESCE(n.pinned, '')) ~ '^-?[0-9]+$' THEN btrim(n.pinned)::INTEGER ELSE NULL END AS pinned,
        CASE WHEN btrim(COALESCE(n.sort_order, '')) ~ '^-?[0-9]+$' THEN btrim(n.sort_order)::INTEGER ELSE NULL END AS sort_order,
        CASE WHEN btrim(COALESCE(n.publish_from, '')) = '' THEN NULL ELSE n.publish_from::timestamp END AS publish_from,
        CASE WHEN btrim(COALESCE(n.publish_to, '')) = '' THEN NULL ELSE n.publish_to::timestamp END AS publish_to,
        CASE WHEN btrim(COALESCE(n.is_active, '')) ~ '^-?[0-9]+$' THEN btrim(n.is_active)::INTEGER ELSE NULL END AS is_active,
        NULLIF(btrim(COALESCE(n.version_tag, '')), '') AS version_tag,
        CASE WHEN btrim(COALESCE(n.created_at, '')) = '' THEN NULL ELSE n.created_at::timestamp END AS created_at,
        CASE WHEN btrim(COALESCE(n.updated_at, '')) = '' THEN NULL ELSE n.updated_at::timestamp END AS updated_at
    FROM migration_staging.raw_legacy_notice_v2 n
)
SELECT p.*
FROM parsed p
JOIN migration_staging.stg_legacy_pilot_years py
  ON py.eval_year = p.eval_year;

-- ----------------------------------------------------------------------
-- 2) load quality checks
-- ----------------------------------------------------------------------
SELECT
    COUNT(*) AS submission_rows,
    COUNT(*) FILTER (WHERE COALESCE(btrim(s.answers_json), '') = '') AS empty_answers_json_rows,
    COUNT(*) FILTER (WHERE COALESCE(btrim(s.answers_json), '') <> '' AND migration_staging.try_parse_jsonb(s.answers_json) IS NULL) AS invalid_answers_json_rows
FROM migration_staging.stg_legacy_evaluation_submissions s;

SELECT 'stg_legacy_users_2025' AS table_name, COUNT(*) AS cnt FROM migration_staging.stg_legacy_users_2025
UNION ALL SELECT 'stg_legacy_pilot_user_keys', COUNT(*) FROM migration_staging.stg_legacy_pilot_user_keys
UNION ALL SELECT 'stg_legacy_pilot_years', COUNT(*) FROM migration_staging.stg_legacy_pilot_years
UNION ALL SELECT 'stg_legacy_user_roles_2025', COUNT(*) FROM migration_staging.stg_legacy_user_roles_2025
UNION ALL SELECT 'stg_legacy_sub_management', COUNT(*) FROM migration_staging.stg_legacy_sub_management
UNION ALL SELECT 'stg_legacy_team', COUNT(*) FROM migration_staging.stg_legacy_team
UNION ALL SELECT 'stg_legacy_evaluation', COUNT(*) FROM migration_staging.stg_legacy_evaluation
UNION ALL SELECT 'stg_legacy_admin_default_targets', COUNT(*) FROM migration_staging.stg_legacy_admin_default_targets
UNION ALL SELECT 'stg_legacy_admin_custom_targets', COUNT(*) FROM migration_staging.stg_legacy_admin_custom_targets
UNION ALL SELECT 'stg_legacy_evaluation_submissions', COUNT(*) FROM migration_staging.stg_legacy_evaluation_submissions
UNION ALL SELECT 'stg_legacy_evaluation_comment_summary', COUNT(*) FROM migration_staging.stg_legacy_evaluation_comment_summary
UNION ALL SELECT 'stg_legacy_kpi_eval_clinic', COUNT(*) FROM migration_staging.stg_legacy_kpi_eval_clinic
UNION ALL SELECT 'stg_legacy_kpi_personal_2025', COUNT(*) FROM migration_staging.stg_legacy_kpi_personal_2025
UNION ALL SELECT 'stg_legacy_kpi_info_general_2025', COUNT(*) FROM migration_staging.stg_legacy_kpi_info_general_2025
UNION ALL SELECT 'stg_legacy_notice_v2', COUNT(*) FROM migration_staging.stg_legacy_notice_v2
ORDER BY table_name;

COMMIT;
