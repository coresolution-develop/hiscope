-- Archive: KPI legacy tables (separate track)
-- 대상/성격:
--   - kpi_eval_clinic      : 진료부/의료진 KPI 원천
--   - kpi_personal_2025    : 병원 일반직원 KPI 원천
--   - kpi_info_general_2025: 경혁팀 KPI 원천
-- 정책:
--   - 1차는 archive-only (핵심 다면평가 트랜잭션 도메인 직접 적재 금지)
--   - 2차 KPI 도메인 설계 후 별도 이관

BEGIN;

CREATE SCHEMA IF NOT EXISTS migration_archive;

CREATE TABLE IF NOT EXISTS migration_archive.legacy_kpi_archive (
    archive_id BIGSERIAL PRIMARY KEY,
    source_table TEXT NOT NULL,
    kpi_track TEXT NOT NULL,
    legacy_row_pk TEXT NOT NULL,
    organization_hint TEXT,
    year_hint TEXT,
    row_checksum TEXT NOT NULL,
    payload JSONB NOT NULL,
    archived_at TIMESTAMP NOT NULL DEFAULT NOW(),
    archive_batch_id TEXT NOT NULL DEFAULT to_char(NOW(), 'YYYYMMDDHH24MISS'),
    UNIQUE (source_table, legacy_row_pk)
);

INSERT INTO migration_archive.legacy_kpi_archive (
    source_table,
    kpi_track,
    legacy_row_pk,
    organization_hint,
    year_hint,
    row_checksum,
    payload
)
SELECT
    'kpi_eval_clinic' AS source_table,
    'CLINIC' AS kpi_track,
    COALESCE(NULLIF(to_jsonb(k)->>'id', ''), md5(to_jsonb(k)::text)) AS legacy_row_pk,
    NULLIF(btrim(COALESCE(k.hospital_name, '')), '') AS organization_hint,
    NULLIF(k.eval_year::text, '') AS year_hint,
    md5(to_jsonb(k)::text) AS row_checksum,
    to_jsonb(k) AS payload
FROM migration_staging.stg_legacy_kpi_eval_clinic k
ON CONFLICT (source_table, legacy_row_pk) DO UPDATE
SET kpi_track = EXCLUDED.kpi_track,
    organization_hint = EXCLUDED.organization_hint,
    year_hint = EXCLUDED.year_hint,
    row_checksum = EXCLUDED.row_checksum,
    payload = EXCLUDED.payload,
    archived_at = NOW();

INSERT INTO migration_archive.legacy_kpi_archive (
    source_table,
    kpi_track,
    legacy_row_pk,
    organization_hint,
    year_hint,
    row_checksum,
    payload
)
SELECT
    'kpi_personal_2025' AS source_table,
    'PERSONAL' AS kpi_track,
    COALESCE(NULLIF(to_jsonb(k)->>'id', ''), md5(to_jsonb(k)::text)) AS legacy_row_pk,
    NULLIF(btrim(COALESCE(k.hospital_name, '')), '') AS organization_hint,
    NULLIF(k.eval_year::text, '') AS year_hint,
    md5(to_jsonb(k)::text) AS row_checksum,
    to_jsonb(k) AS payload
FROM migration_staging.stg_legacy_kpi_personal_2025 k
ON CONFLICT (source_table, legacy_row_pk) DO UPDATE
SET kpi_track = EXCLUDED.kpi_track,
    organization_hint = EXCLUDED.organization_hint,
    year_hint = EXCLUDED.year_hint,
    row_checksum = EXCLUDED.row_checksum,
    payload = EXCLUDED.payload,
    archived_at = NOW();

INSERT INTO migration_archive.legacy_kpi_archive (
    source_table,
    kpi_track,
    legacy_row_pk,
    organization_hint,
    year_hint,
    row_checksum,
    payload
)
SELECT
    'kpi_info_general_2025' AS source_table,
    'CHANGE_INNOVATION' AS kpi_track,
    COALESCE(NULLIF(to_jsonb(k)->>'kcol02', ''), md5(to_jsonb(k)::text)) AS legacy_row_pk,
    NULLIF(btrim(COALESCE(k.kcol01, '')), '') AS organization_hint,
    NULL::text AS year_hint,
    md5(to_jsonb(k)::text) AS row_checksum,
    to_jsonb(k) AS payload
FROM migration_staging.stg_legacy_kpi_info_general_2025 k
ON CONFLICT (source_table, legacy_row_pk) DO UPDATE
SET kpi_track = EXCLUDED.kpi_track,
    organization_hint = EXCLUDED.organization_hint,
    year_hint = EXCLUDED.year_hint,
    row_checksum = EXCLUDED.row_checksum,
    payload = EXCLUDED.payload,
    archived_at = NOW();

SELECT
    source_table,
    kpi_track,
    COUNT(*) AS row_count,
    COUNT(DISTINCT organization_hint) AS organization_hint_count,
    COUNT(DISTINCT year_hint) AS year_hint_count
FROM migration_archive.legacy_kpi_archive
GROUP BY source_table, kpi_track
ORDER BY source_table, kpi_track;

COMMIT;
