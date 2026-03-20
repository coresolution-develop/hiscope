-- Legacy DB profiling SQL template (PostgreSQL)
-- 목적: 레거시 실측 전까지 "추측" 대신 메타데이터/분포를 수집
-- 사용법: 레거시 DB 접속 후 실행

-- E-01: 테이블 목록
SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_type = 'BASE TABLE'
  AND table_schema NOT IN ('information_schema', 'pg_catalog')
ORDER BY table_schema, table_name;

-- E-02: 테이블별 컬럼 사전
SELECT
    table_schema,
    table_name,
    ordinal_position,
    column_name,
    data_type,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_schema NOT IN ('information_schema', 'pg_catalog')
ORDER BY table_schema, table_name, ordinal_position;

-- E-03: PK / UNIQUE 제약
SELECT
    tc.table_schema,
    tc.table_name,
    tc.constraint_name,
    tc.constraint_type,
    string_agg(kcu.column_name, ', ' ORDER BY kcu.ordinal_position) AS columns
FROM information_schema.table_constraints tc
LEFT JOIN information_schema.key_column_usage kcu
    ON tc.constraint_name = kcu.constraint_name
   AND tc.table_schema = kcu.table_schema
   AND tc.table_name = kcu.table_name
WHERE tc.table_schema NOT IN ('information_schema', 'pg_catalog')
  AND tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE')
GROUP BY tc.table_schema, tc.table_name, tc.constraint_name, tc.constraint_type
ORDER BY tc.table_schema, tc.table_name, tc.constraint_type, tc.constraint_name;

-- E-04: FK 제약
SELECT
    tc.table_schema,
    tc.table_name,
    tc.constraint_name,
    kcu.column_name,
    ccu.table_schema AS foreign_table_schema,
    ccu.table_name   AS foreign_table_name,
    ccu.column_name  AS foreign_column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON tc.constraint_name = kcu.constraint_name
 AND tc.table_schema = kcu.table_schema
JOIN information_schema.constraint_column_usage ccu
  ON ccu.constraint_name = tc.constraint_name
 AND ccu.table_schema = tc.table_schema
WHERE tc.constraint_type = 'FOREIGN KEY'
  AND tc.table_schema NOT IN ('information_schema', 'pg_catalog')
ORDER BY tc.table_schema, tc.table_name, tc.constraint_name, kcu.ordinal_position;

-- E-05: login/password 후보 컬럼 탐색
SELECT table_schema, table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema NOT IN ('information_schema', 'pg_catalog')
  AND (
       LOWER(column_name) LIKE '%login%'
    OR LOWER(column_name) LIKE '%user%'
    OR LOWER(column_name) LIKE '%account%'
    OR LOWER(column_name) LIKE '%password%'
    OR LOWER(column_name) LIKE '%passwd%'
    OR LOWER(column_name) LIKE '%employee%'
    OR column_name IN ('id', 'emp_id', 'emp_no', 'sabun', 'org_code')
  )
ORDER BY table_schema, table_name, ordinal_position;

-- E-06: 계정 해시 prefix 분포 확인용 템플릿
-- 아래 <legacy_account_table>, <password_hash_column>은 실측 후 치환
-- SELECT
--   LEFT(<password_hash_column>, 4) AS hash_prefix,
--   COUNT(*) AS cnt
-- FROM <legacy_account_table>
-- WHERE <password_hash_column> IS NOT NULL
-- GROUP BY LEFT(<password_hash_column>, 4)
-- ORDER BY cnt DESC;

-- E-07: 기관/사번/로그인 중복 진단 템플릿
-- 아래 쿼리는 실측 테이블/컬럼으로 치환해서 사용
-- (1) 전역 login_id 중복
-- SELECT login_id, COUNT(*) FROM legacy_users GROUP BY login_id HAVING COUNT(*) > 1;
-- (2) 기관+login_id 중복
-- SELECT org_code, login_id, COUNT(*) FROM legacy_users GROUP BY org_code, login_id HAVING COUNT(*) > 1;
-- (3) 기관+employee_number 중복
-- SELECT org_code, employee_number, COUNT(*) FROM legacy_users GROUP BY org_code, employee_number HAVING COUNT(*) > 1;
