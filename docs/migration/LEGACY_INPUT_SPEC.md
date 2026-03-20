# LEGACY 입력 명세 (실측 기반 파일럿 이관용)

기준 저장소: `hiscope` (2026-03-19)  
목적: Codex가 레거시 DB 실측값을 받아 `organizations` ~ `evaluation_response_items`까지 파일럿 이관을 실제 수행할 수 있도록 최소 입력 포맷을 고정한다.

## 1. 입력 등급 정의

- 없으면 진행 불가: 이 값이 없으면 변환 SQL 작성/실행 자체가 불가능한 항목
- 필수 입력: 진행은 가능하지만, 없으면 데이터 유실/왜곡 위험이 큰 항목
- 있으면 좋은 입력: 품질 검증/속도 향상 목적 항목

## 2. 없으면 진행 불가 입력

### 2.1 레거시 DB 접속/덤프 기본정보
- DB 엔진/버전 (예: PostgreSQL 12, MariaDB 10.6)
- 문자셋/콜레이션
- 제공 형태: 접속 정보 또는 덤프 파일(sql/csv/parquet)
- 파일 인코딩(UTF-8 여부)

### 2.2 레거시 테이블-컬럼 사전 (실제 이름)
다음 신규 대상에 대응하는 레거시 테이블명/컬럼명을 반드시 제공:
- 기관: `organizations`
- 부서: `departments`
- 직원: `employees`
- 관리자 계정: `accounts`
- 직원 로그인: `user_accounts`
- 직원 속성: `employee_attributes`, `employee_attribute_values`
- 템플릿/문항: `evaluation_templates`, `evaluation_questions`
- 세션: `evaluation_sessions`
- 관계/배정: `evaluation_relationships`, `evaluation_assignments`
- 응답/응답문항: `evaluation_responses`, `evaluation_response_items`
- (존재 시) 조직 설정: `organization_settings`
- (존재 시) 업로드 이력: `upload_histories`

### 2.3 조인 키 및 PK/FK 정보
- 각 테이블 PK 컬럼
- 기관/직원/부서/세션/문항 연결 FK 컬럼
- 사번/로그인ID 식별 컬럼
- 상태값 컬럼(재직/퇴직/휴직, 활성/비활성)

### 2.4 샘플 데이터
아래 각 도메인별 샘플 row 최소 5건(마스킹 허용):
- 기관, 부서, 직원, 계정, 세션, 관계, 배정, 응답, 응답문항
- 가능하면 문제 케이스 포함: null 사번, 중복 loginId, 퇴직자 포함 세션

## 3. 필수 입력

### 3.1 로그인/인증 관련
- 비밀번호 해시 샘플 50건 (원문 금지, 해시만)
- 해시 prefix 분포 (예: `$2a$`, `$2b$`, `{bcrypt}`, 기타)
- 계정 상태값 코드표 (ACTIVE/LOCKED 등 실제 값)

### 3.2 조직/프로파일 매핑 결정 입력
- 기관별 조직유형 매핑값: `HOSPITAL`/`AFFILIATE`
- 기관별 프로파일 후보: `HOSPITAL_DEFAULT`/`AFFILIATE_HOSPITAL`/`AFFILIATE_GENERAL`
- 기관코드(orgCode) 기준값

### 3.3 직원 속성 매핑 입력
Rule 기반 관계 생성/검증용으로, 레거시에서 다음 속성의 출처 컬럼 또는 산식 제공:
- `institution_head`
- `unit_head`
- `department_head`
- `evaluation_excluded`
- `single_member_department`
- `medical_leader`
- `clinical_team_leader`
- `change_innovation_team`
- `affiliate_policy_group`

### 3.4 세션/관계 생성 정책 입력
- 세션별 관계 생성 방식 지정값: `LEGACY` 또는 `RULE_BASED`
- RULE_BASED 세션의 정의세트 연결 근거(기관 기본 세트 사용 여부)
- `resolved_question_group_code` 원천 값 존재 여부
  - 없으면 정책상 LEGACY만 NULL 허용, RULE_BASED는 채움 로직 필요

### 3.5 KPI 입력
현재 저장소에 KPI 엔티티/마이그레이션이 없으므로(`src/main/java`, `db/migration/common` 기준), 아래 중 하나를 필수로 결정:
- 이관 제외(별도 보관) 또는
- 별도 임시 테이블로 분리 적재

## 4. 있으면 좋은 입력

- 레거시 수동 매핑/예외 매핑 이력 저장 구조
- 과거 업로드 이력/감사 로그
- 레거시 평가 결과 집계 리포트(기관/세션별)
- 기관별 운영 규칙 문서(병원/계열사 예외 룰)

## 5. 제출 포맷 권장

### 5.1 파일 구조
- `legacy_schema/` : 테이블 DDL, 인덱스, 제약조건
- `legacy_samples/` : 도메인별 CSV(각 5건+)
- `legacy_mapping/` : 코드표/상태값/속성 매핑표
- `legacy_sql/` : 원본 추출 SQL(재실행 가능)

### 5.2 매핑표 템플릿(필수)
컬럼 단위로 아래 포맷 제공:
- `legacy_table`
- `legacy_column`
- `new_table`
- `new_column`
- `transform_rule`
- `null_policy`
- `sample_value`

## 6. 즉시 실행 전 체크

실측 자료 수신 후, 아래 파일의 placeholder를 실제 값으로 치환하고 실행:
- `scripts/migration/load_staging_template.sql`
- `scripts/migration/transform_legacy_to_new_template.sql`
- `scripts/migration/validate_post_migration.sql`
