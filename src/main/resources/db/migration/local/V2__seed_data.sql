-- =============================================
-- 시연용 초기 데이터
-- 슈퍼 관리자: super / password123
-- 기관 관리자: admin / password123
-- 직원 계정: emp001~emp010 / password123
-- BCrypt hash of 'password123'
-- =============================================

-- 슈퍼 관리자
INSERT INTO accounts (login_id, password_hash, name, email, role, organization_id)
VALUES ('super', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', '슈퍼관리자', 'super@system.com', 'ROLE_SUPER_ADMIN', NULL);

-- 기관 1: 한국인재개발원
INSERT INTO organizations (name, code, status)
VALUES ('한국인재개발원', 'KIDR', 'ACTIVE');

-- 기관 1 관리자
INSERT INTO accounts (organization_id, login_id, password_hash, name, email, role)
VALUES (1, 'admin', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', '기관관리자', 'admin@kidr.or.kr', 'ROLE_ORG_ADMIN');

-- 기관 2: 미래교육재단
INSERT INTO organizations (name, code, status)
VALUES ('미래교육재단', 'FEF', 'ACTIVE');

-- 기관 2 관리자
INSERT INTO accounts (organization_id, login_id, password_hash, name, email, role)
VALUES (2, 'admin2', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', '미래교육재단관리자', 'admin@fef.or.kr', 'ROLE_ORG_ADMIN');

-- =============================================
-- 기관 1 부서 구조
-- =============================================
INSERT INTO departments (organization_id, parent_id, name, code, is_active) VALUES (1, NULL, '경영지원본부', 'MGMT', true);
INSERT INTO departments (organization_id, parent_id, name, code, is_active) VALUES (1, NULL, '교육운영본부', 'EDU', true);
INSERT INTO departments (organization_id, parent_id, name, code, is_active) VALUES (1, 1, '인사팀', 'HR', true);
INSERT INTO departments (organization_id, parent_id, name, code, is_active) VALUES (1, 1, '재무팀', 'FIN', true);
INSERT INTO departments (organization_id, parent_id, name, code, is_active) VALUES (1, 2, '교육기획팀', 'EDU_PLAN', true);
INSERT INTO departments (organization_id, parent_id, name, code, is_active) VALUES (1, 2, '교육운영팀', 'EDU_OPS', true);

-- =============================================
-- 기관 1 직원 (HR팀)
-- =============================================
INSERT INTO employees (organization_id, department_id, name, employee_number, position, job_title, email, status)
VALUES (1, 3, '김민수', 'E001', '과장', '팀장', 'mskim@kidr.or.kr', 'ACTIVE');

INSERT INTO employees (organization_id, department_id, name, employee_number, position, job_title, email, status)
VALUES (1, 3, '이지영', 'E002', '대리', '팀원', 'jylee@kidr.or.kr', 'ACTIVE');

INSERT INTO employees (organization_id, department_id, name, employee_number, position, job_title, email, status)
VALUES (1, 3, '박성호', 'E003', '사원', '팀원', 'shpark@kidr.or.kr', 'ACTIVE');

-- 기관 1 직원 (재무팀)
INSERT INTO employees (organization_id, department_id, name, employee_number, position, job_title, email, status)
VALUES (1, 4, '최수진', 'E004', '차장', '팀장', 'sjchoi@kidr.or.kr', 'ACTIVE');

INSERT INTO employees (organization_id, department_id, name, employee_number, position, job_title, email, status)
VALUES (1, 4, '정우혁', 'E005', '대리', '팀원', 'whjung@kidr.or.kr', 'ACTIVE');

-- 기관 1 직원 (교육기획팀)
INSERT INTO employees (organization_id, department_id, name, employee_number, position, job_title, email, status)
VALUES (1, 5, '한지수', 'E006', '과장', '팀장', 'jsahn@kidr.or.kr', 'ACTIVE');

INSERT INTO employees (organization_id, department_id, name, employee_number, position, job_title, email, status)
VALUES (1, 5, '오민준', 'E007', '사원', '팀원', 'mjoh@kidr.or.kr', 'ACTIVE');

-- 기관 1 직원 (교육운영팀)
INSERT INTO employees (organization_id, department_id, name, employee_number, position, job_title, email, status)
VALUES (1, 6, '강태양', 'E008', '과장', '팀장', 'tykang@kidr.or.kr', 'ACTIVE');

INSERT INTO employees (organization_id, department_id, name, employee_number, position, job_title, email, status)
VALUES (1, 6, '윤서연', 'E009', '대리', '팀원', 'syyoon@kidr.or.kr', 'ACTIVE');

INSERT INTO employees (organization_id, department_id, name, employee_number, position, job_title, email, status)
VALUES (1, 6, '임현우', 'E010', '사원', '팀원', 'hwlim@kidr.or.kr', 'ACTIVE');

-- =============================================
-- 직원 로그인 계정 (emp001~emp010 / password123)
-- =============================================
INSERT INTO user_accounts (employee_id, organization_id, login_id, password_hash, role)
VALUES (1, 1, 'emp001', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', 'ROLE_USER');

INSERT INTO user_accounts (employee_id, organization_id, login_id, password_hash, role)
VALUES (2, 1, 'emp002', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', 'ROLE_USER');

INSERT INTO user_accounts (employee_id, organization_id, login_id, password_hash, role)
VALUES (3, 1, 'emp003', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', 'ROLE_USER');

INSERT INTO user_accounts (employee_id, organization_id, login_id, password_hash, role)
VALUES (4, 1, 'emp004', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', 'ROLE_USER');

INSERT INTO user_accounts (employee_id, organization_id, login_id, password_hash, role)
VALUES (5, 1, 'emp005', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', 'ROLE_USER');

INSERT INTO user_accounts (employee_id, organization_id, login_id, password_hash, role)
VALUES (6, 1, 'emp006', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', 'ROLE_USER');

INSERT INTO user_accounts (employee_id, organization_id, login_id, password_hash, role)
VALUES (7, 1, 'emp007', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', 'ROLE_USER');

INSERT INTO user_accounts (employee_id, organization_id, login_id, password_hash, role)
VALUES (8, 1, 'emp008', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', 'ROLE_USER');

INSERT INTO user_accounts (employee_id, organization_id, login_id, password_hash, role)
VALUES (9, 1, 'emp009', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', 'ROLE_USER');

INSERT INTO user_accounts (employee_id, organization_id, login_id, password_hash, role)
VALUES (10, 1, 'emp010', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', 'ROLE_USER');

-- =============================================
-- 평가 템플릿
-- =============================================
INSERT INTO evaluation_templates (organization_id, name, description, is_active)
VALUES (1, '2026 상반기 다면평가 템플릿', '공통역량, 협업, 리더십, 정성평가 포함', true);

-- 평가 문항 (공통역량)
INSERT INTO evaluation_questions (template_id, organization_id, category, content, question_type, max_score, sort_order)
VALUES (1, 1, '공통역량', '업무 전문성과 지식 수준은 어떻게 평가하십니까?', 'SCALE', 5, 1);

INSERT INTO evaluation_questions (template_id, organization_id, category, content, question_type, max_score, sort_order)
VALUES (1, 1, '공통역량', '책임감과 성실성은 어떻게 평가하십니까?', 'SCALE', 5, 2);

INSERT INTO evaluation_questions (template_id, organization_id, category, content, question_type, max_score, sort_order)
VALUES (1, 1, '공통역량', '문제 해결 능력은 어떻게 평가하십니까?', 'SCALE', 5, 3);

-- 평가 문항 (협업)
INSERT INTO evaluation_questions (template_id, organization_id, category, content, question_type, max_score, sort_order)
VALUES (1, 1, '협업', '팀원과의 협업 능력은 어떻게 평가하십니까?', 'SCALE', 5, 4);

INSERT INTO evaluation_questions (template_id, organization_id, category, content, question_type, max_score, sort_order)
VALUES (1, 1, '협업', '소통 능력 및 정보 공유는 어떻게 평가하십니까?', 'SCALE', 5, 5);

-- 평가 문항 (리더십)
INSERT INTO evaluation_questions (template_id, organization_id, category, content, question_type, max_score, sort_order)
VALUES (1, 1, '리더십', '팀원을 동기부여하고 이끄는 능력은 어떻게 평가하십니까?', 'SCALE', 5, 6);

INSERT INTO evaluation_questions (template_id, organization_id, category, content, question_type, max_score, sort_order)
VALUES (1, 1, '리더십', '의사결정 능력과 판단력은 어떻게 평가하십니까?', 'SCALE', 5, 7);

-- 평가 문항 (정성평가)
INSERT INTO evaluation_questions (template_id, organization_id, category, content, question_type, max_score, sort_order)
VALUES (1, 1, '정성평가', '해당 구성원의 강점을 서술해 주세요.', 'DESCRIPTIVE', NULL, 8);

INSERT INTO evaluation_questions (template_id, organization_id, category, content, question_type, max_score, sort_order)
VALUES (1, 1, '정성평가', '해당 구성원에게 개선이 필요한 부분을 서술해 주세요.', 'DESCRIPTIVE', NULL, 9);

-- =============================================
-- 평가 세션
-- =============================================
INSERT INTO evaluation_sessions (organization_id, name, description, status, start_date, end_date, allow_resubmit, template_id, created_by)
VALUES (1, '2026년 상반기 다면평가', '2026년 상반기 전 직원 다면평가', 'PENDING', '2026-03-15', '2026-03-31', false, 1, 2);
