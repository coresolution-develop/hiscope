-- Archive: evaluation_comment_summary (legacy AI cache)
-- 분류:
--   - evaluation_comment_summary는 원천 제출이 아니라 캐시/파생 데이터
--   - TOTAL, SCORE_KY는 KPI 결합 결과일 수 있으므로 핵심 다면평가 원천으로 취급하지 않음
-- 정책:
--   - 1차는 archive-only
--   - kind/model/status/error_msg를 상위 컬럼으로 보존

BEGIN;

CREATE SCHEMA IF NOT EXISTS migration_archive;

CREATE TABLE IF NOT EXISTS migration_archive.legacy_ai_summary_archive (
    archive_id BIGSERIAL PRIMARY KEY,
    source_table TEXT NOT NULL,
    legacy_row_pk TEXT NOT NULL,
    eval_year INTEGER,
    target_id TEXT,
    data_ev TEXT,
    kind TEXT,
    model TEXT,
    status TEXT,
    error_msg TEXT,
    legacy_submission_id TEXT,
    organization_hint TEXT,
    summary_payload JSONB NOT NULL,
    archived_at TIMESTAMP NOT NULL DEFAULT NOW(),
    archive_batch_id TEXT NOT NULL DEFAULT to_char(NOW(), 'YYYYMMDDHH24MISS'),
    UNIQUE (source_table, legacy_row_pk)
);

-- response 연결(선택)
CREATE TABLE IF NOT EXISTS migration_archive.legacy_ai_summary_response_link (
    archive_id BIGINT NOT NULL REFERENCES migration_archive.legacy_ai_summary_archive (archive_id),
    new_response_id BIGINT,
    link_status TEXT NOT NULL,
    linked_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (archive_id)
);

INSERT INTO migration_archive.legacy_ai_summary_archive (
    source_table,
    legacy_row_pk,
    eval_year,
    target_id,
    data_ev,
    kind,
    model,
    status,
    error_msg,
    legacy_submission_id,
    organization_hint,
    summary_payload
)
SELECT
    'evaluation_comment_summary' AS source_table,
    concat_ws(':', s.eval_year::text, s.target_id, s.data_ev, s.kind::text) AS legacy_row_pk,
    s.eval_year,
    s.target_id,
    s.data_ev,
    s.kind::text,
    s.model,
    s.status::text,
    s.error_msg,
    NULL::text AS legacy_submission_id,
    NULL::text AS organization_hint,
    to_jsonb(s) AS summary_payload
FROM migration_staging.stg_legacy_evaluation_comment_summary s
ON CONFLICT (source_table, legacy_row_pk) DO UPDATE
SET eval_year = EXCLUDED.eval_year,
    target_id = EXCLUDED.target_id,
    data_ev = EXCLUDED.data_ev,
    kind = EXCLUDED.kind,
    model = EXCLUDED.model,
    status = EXCLUDED.status,
    error_msg = EXCLUDED.error_msg,
    legacy_submission_id = EXCLUDED.legacy_submission_id,
    organization_hint = EXCLUDED.organization_hint,
    summary_payload = EXCLUDED.summary_payload,
    archived_at = NOW();

INSERT INTO migration_archive.legacy_ai_summary_response_link (
    archive_id,
    new_response_id,
    link_status
)
SELECT
    a.archive_id,
    mr.new_response_id,
    CASE
        WHEN a.legacy_submission_id IS NULL THEN 'NO_SUBMISSION_KEY'
        WHEN mr.new_response_id IS NULL THEN 'UNRESOLVED_SUBMISSION'
        ELSE 'LINKED'
    END AS link_status
FROM migration_archive.legacy_ai_summary_archive a
LEFT JOIN migration_staging.map_response mr
  ON mr.legacy_submission_id = a.legacy_submission_id
WHERE a.source_table = 'evaluation_comment_summary'
ON CONFLICT (archive_id) DO UPDATE
SET new_response_id = EXCLUDED.new_response_id,
    link_status = EXCLUDED.link_status,
    linked_at = NOW();

SELECT
    kind,
    status,
    COUNT(*) AS archived_rows
FROM migration_archive.legacy_ai_summary_archive
WHERE source_table = 'evaluation_comment_summary'
GROUP BY kind, status
ORDER BY kind, status;

COMMIT;
