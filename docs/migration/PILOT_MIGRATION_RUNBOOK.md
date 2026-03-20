# 기관 1개 파일럿 이관 Runbook

기준 저장소: `hiscope` (2026-03-19)  
대상: 기관 1개(병원 또는 계열사 1개)  
전제: `docs/migration/LEGACY_INPUT_SPEC.md`의 "없으면 진행 불가" 입력이 모두 충족됨.

## 0. 사용 파일

- 입력 명세: `docs/migration/LEGACY_INPUT_SPEC.md`
- 매핑 기준: `docs/migration/LEGACY_TO_NEW_MAPPING.md`
- 리허설 체크리스트: `docs/migration/MIGRATION_REHEARSAL_CHECKLIST.md`
- 사후 검증 SQL: `scripts/migration/validate_post_migration.sql`
- 적재 템플릿: `scripts/migration/load_staging_template.sql`
- 변환 템플릿: `scripts/migration/transform_legacy_to_new_template.sql`

## 1) 레거시 실측

입력:
- 레거시 스키마 DDL
- 도메인별 샘플 5건+
- 코드표/상태값 매핑표

출력:
- placeholder 치환 사전(`legacy_table -> 실제 테이블명`)
- 기관 1개 범위 필터 조건(기관코드/기관ID)

검증 SQL:
- 레거시 측 row count SQL(기관, 부서, 직원, 세션, 관계, 응답)

실패 시 조치:
- PK/FK/상태값 정의 누락 시 중단
- nullable 정책 불명확 시 "입력 필요"로 회수

## 2) staging 적재

입력:
- `scripts/migration/load_staging_template.sql` (placeholder 치환본)

출력:
- `stg_legacy_*` 테이블 적재 완료

검증 SQL:
- staging 건수 점검
- 중복키/NULL 필수값 점검

실패 시 조치:
- CSV 컬럼 순서/타입 mismatch 수정 후 재적재
- 사번/로그인ID 정규화 규칙 재확인

## 3) 신규 스키마 변환

입력:
- `scripts/migration/transform_legacy_to_new_template.sql` (placeholder 치환본)

출력:
- 신규 테이블 데이터 적재
  - `organizations`, `departments`, `employees`, `accounts`, `user_accounts`
  - `employee_attributes`, `employee_attribute_values`
  - `evaluation_templates`, `evaluation_questions`
  - `evaluation_sessions`, `evaluation_relationships`, `evaluation_assignments`
  - `evaluation_responses`, `evaluation_response_items`

검증 SQL:
- `scripts/migration/validate_post_migration.sql`의 `V-01`, `V-02`, `V-03`, `V-07`

실패 시 조치:
- `V-01` 실패: 기관 type/profile 매핑 수정
- `V-02` 실패: null-org super admin login 중복 제거
- `V-07` 실패: employee_number/login_id 정제 규칙 수정

## 4) bootstrap 실행

핵심 사실:
- 코드상 bootstrap은 `OrganizationService.create()` 경로에서만 자동 호출됨.
- SQL 직접 INSERT로 이관한 기관은 자동 bootstrap 되지 않음.

입력:
- 이관 대상 기관 ID
- bootstrap 실행 수단(운영에서 확정 필요)

출력:
- 기본 활성 rule definition set 1개 이상 생성

검증 SQL:
- `validate_post_migration.sql`의 `V-06`

실패 시 조치:
- bootstrap 누락 기관에 대해 재실행
- rule set 중복 생성 여부 확인 후 정리

## 5) RULE_BASED / LEGACY 검증

입력:
- 세션별 `relationship_generation_mode` 결정표

출력:
- RULE_BASED 세션: definition set 연결 + assignment의 `resolved_question_group_code` 채움
- LEGACY 세션: `resolved_question_group_code` NULL 허용

검증 SQL:
- `V-04`, `V-05`, `V-08`, `V-09`

실패 시 조치:
- `V-04` 실패: RULE_BASED 배정 문항군 코드 채움 로직 보정
- `V-08` 실패: self-evaluation 제거 정책 재적용
- `V-09` 실패: 관계-배정 생성 순서/참조키 점검

## 6) 로그인 검증

입력:
- 기관별 테스트 계정(관리자 1, 직원 2)

출력:
- 기관 스코프 로그인 동작 확인

검증 항목:
- `orgCode + loginId + password` 성공
- orgCode 미입력 + 중복 loginId 실패
- `must_change_password` 동작(대상 계정만)

실패 시 조치:
- `accounts`, `user_accounts` login 중복/상태값 확인
- 해시 알고리즘 불일치 시 비밀번호 재설정 배치 사용

## 7) 마이페이지 검증

입력:
- 파일럿 기관의 완료된 assignment/response/item 샘플

출력:
- 마이페이지 요약(점수/코멘트/카테고리) 정상 집계

검증 포인트:
- 집계 기준은 `evaluation_responses` + `evaluation_response_items` 최종 제출 데이터
- AI 요약은 신규 기능이며, 과거 응답 데이터가 있으면 재집계 가능

실패 시 조치:
- response-item 연결(`assignment_id`, `question_id`) 재검증
- question/category 누락 시 문항 이관 매핑 보정

## 8) Go / No-go 판정

Go 최소 조건:
- `V-06` bootstrap 누락 0건
- `V-02` null-org super admin 중복 0건
- `V-04` RULE_BASED + group code NULL 0건
- `V-08` self-evaluation 0건(정책상 허용 시 별도 승인)
- 핵심 로그인 시나리오 성공

No-go 조건:
- 로그인 실패/계정 충돌 다수
- RULE_BASED 세션 문항군 결정 실패
- 관계-배정 orphan 발생

## 부록: 실행 로그 권장 포맷

- 실행자
- 실행 시각(KST)
- 대상 기관코드
- 실행한 SQL 파일/버전
- 검증 SQL 결과 요약(건수)
- 이슈/조치/재실행 여부
