-- Post-migration validation SQL (PostgreSQL)
-- 목적: 이관 직후 운영 리허설에서 즉시 실행 가능한 정합성 점검

-- V-01: organization_type / organization_profile 불일치 탐지
SELECT id, name, organization_type, organization_profile
FROM organizations
WHERE (organization_type = 'AFFILIATE' AND organization_profile = 'HOSPITAL_DEFAULT')
   OR (organization_type = 'HOSPITAL' AND organization_profile <> 'HOSPITAL_DEFAULT')
ORDER BY id;

-- V-02: NULL organization_id(슈퍼관리자) login_id 중복 탐지
SELECT login_id, COUNT(*) AS cnt
FROM accounts
WHERE organization_id IS NULL
GROUP BY login_id
HAVING COUNT(*) > 1
ORDER BY cnt DESC, login_id;

-- V-03: 기관별 핵심 건수 요약
SELECT
    o.id AS org_id,
    o.code AS org_code,
    o.name AS org_name,
    COUNT(DISTINCT d.id) AS department_count,
    COUNT(DISTINCT e.id) AS employee_count,
    COUNT(DISTINCT ua.id) AS user_account_count,
    COUNT(DISTINCT s.id) AS session_count
FROM organizations o
LEFT JOIN departments d ON d.organization_id = o.id
LEFT JOIN employees e ON e.organization_id = o.id
LEFT JOIN user_accounts ua ON ua.organization_id = o.id
LEFT JOIN evaluation_sessions s ON s.organization_id = o.id
GROUP BY o.id, o.code, o.name
ORDER BY o.id;

-- V-04: RULE_BASED 세션의 resolved_question_group_code NULL 탐지 (assignment)
SELECT
    s.id AS session_id,
    s.name AS session_name,
    COUNT(*) AS assignment_count,
    SUM(CASE WHEN a.resolved_question_group_code IS NULL THEN 1 ELSE 0 END) AS null_group_code_count
FROM evaluation_sessions s
JOIN evaluation_assignments a ON a.session_id = s.id
WHERE s.relationship_generation_mode = 'RULE_BASED'
GROUP BY s.id, s.name
HAVING SUM(CASE WHEN a.resolved_question_group_code IS NULL THEN 1 ELSE 0 END) > 0
ORDER BY s.id;

-- V-05: LEGACY 세션의 resolved_question_group_code non-null 탐지 (정책 위반 감지용)
SELECT
    s.id AS session_id,
    s.name AS session_name,
    COUNT(*) AS non_null_count
FROM evaluation_sessions s
JOIN evaluation_assignments a ON a.session_id = s.id
WHERE s.relationship_generation_mode = 'LEGACY'
  AND a.resolved_question_group_code IS NOT NULL
GROUP BY s.id, s.name
ORDER BY s.id;

-- V-06: bootstrap 누락 기관 (기본 활성 rule set 부재)
SELECT o.id, o.code, o.name, o.organization_type, o.organization_profile
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1
    FROM relationship_definition_sets rds
    WHERE rds.organization_id = o.id
      AND rds.is_default = TRUE
      AND rds.is_active = TRUE
)
ORDER BY o.id;

-- V-07: 직원 계정 품질(사번/로그인ID)
SELECT
    o.code AS org_code,
    COUNT(*) FILTER (WHERE e.employee_number IS NULL OR TRIM(e.employee_number) = '') AS employee_number_null_or_blank,
    COUNT(*) FILTER (WHERE ua.login_id IS NULL OR TRIM(ua.login_id) = '') AS login_id_null_or_blank
FROM organizations o
LEFT JOIN employees e ON e.organization_id = o.id
LEFT JOIN user_accounts ua ON ua.employee_id = e.id
GROUP BY o.code
ORDER BY o.code;

-- V-08: self-evaluation 탐지 (관계/배정/override)
SELECT 'evaluation_relationships' AS table_name, session_id, evaluator_id, evaluatee_id
FROM evaluation_relationships
WHERE evaluator_id = evaluatee_id
UNION ALL
SELECT 'evaluation_assignments' AS table_name, session_id, evaluator_id, evaluatee_id
FROM evaluation_assignments
WHERE evaluator_id = evaluatee_id
UNION ALL
SELECT 'session_generated_relationships' AS table_name, session_id, evaluator_id, evaluatee_id
FROM session_generated_relationships
WHERE evaluator_id = evaluatee_id
UNION ALL
SELECT 'session_relationship_overrides' AS table_name, session_id, evaluator_id, evaluatee_id
FROM session_relationship_overrides
WHERE evaluator_id = evaluatee_id;

-- V-09: 관계는 있는데 배정이 없는 orphan (CLOSED 세션)
SELECT
    r.session_id,
    COUNT(*) AS orphan_relationship_count
FROM evaluation_relationships r
LEFT JOIN evaluation_assignments a ON a.relationship_id = r.id
JOIN evaluation_sessions s ON s.id = r.session_id
WHERE s.status = 'CLOSED'
  AND a.id IS NULL
GROUP BY r.session_id
HAVING COUNT(*) > 0
ORDER BY r.session_id;

-- V-10: organization_settings key 분포 (이관 범위 확인)
SELECT setting_key, COUNT(*) AS row_count
FROM organization_settings
GROUP BY setting_key
ORDER BY setting_key;
