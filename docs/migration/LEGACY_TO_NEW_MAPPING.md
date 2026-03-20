# 레거시 DB → 신규 DB 이관 매핑/정책 (저장소 사실 기준)

기준 저장소: `hiscope`  
기준일: 2026-03-19  
원칙: 현재 저장소 코드/마이그레이션/테스트/문서로 확인 가능한 사실만 기록

## 1. 핵심 매핑

| 레거시 개념 | 신규 스키마 | 확정 사실 | 추가 확인 필요 |
|---|---|---|---|
| 기관 | `organizations` | 기관 유형/프로파일 컬럼 존재 (`organization_type`, `organization_profile`) | 레거시 기관별 프로파일 확정 값 |
| 관리자 계정 | `accounts` | 슈퍼관리자 `organization_id IS NULL`, 기관관리자 `organization_id=org` | 레거시 관리자 role 코드 매핑 |
| 직원 | `employees` | 기관별 사번 unique (`UNIQUE (organization_id, employee_number)`) | 레거시 사번 NULL/중복 정리 정책 |
| 직원 로그인 | `user_accounts` | 기관 스코프 로그인 unique (`organization_id, login_id`) | 레거시 비밀번호 해시 호환성 |
| 부서 | `departments` | 계층 구조 (`parent_id`) 지원 | 레거시 부서코드 표준화 |
| 직원 속성 | `employee_attributes`, `employee_attribute_values` | Rule 기반 관계 생성에 사용 | 레거시 속성 컬럼→키 매핑 표 확정 |
| 평가 룰셋 | `relationship_definition_sets/rules/matchers` | 기관 생성 시 기본 Rule Set 부트스트랩 | 이관 삽입 기관에 대한 별도 bootstrap 절차 |
| 세션 | `evaluation_sessions` | `relationship_generation_mode` 기본 `LEGACY` | 과거 세션의 mode 정책 |
| 관계/배정 | `evaluation_relationships`, `evaluation_assignments` | RULE_BASED에서 `resolved_question_group_code` 채움, LEGACY는 NULL 허용 | 과거 데이터 백필 범위 |
| 응답 | `evaluation_responses`, `evaluation_response_items` | assignment 단위 final 제출 구조 | 레거시 응답 원문의 question-id 재매핑 |
| 조직 설정 | `organization_settings` | 업로드/비밀번호/세션 기본값 저장 | 레거시 설정값 이관 대상 여부 |
| KPI | (신규 도메인 부재) | 신규 Java 도메인/마이그레이션에 KPI 테이블 없음 | 레거시 KPI 별도 보관/2차 이관 정책 |

## 2. Claude 리뷰 항목 판정 (사실 검증)

| 항목 | 판정 | 근거 |
|---|---|---|
| Bootstrap 호출 정책 누락 | 확정 사실 | 기관 생성 서비스에서만 `bootstrap()` 호출됨. 수동 INSERT 경로는 호출 없음. [OrganizationService.java:60](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/domain/organization/service/OrganizationService.java:60), [OrganizationService.java:76](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/domain/organization/service/OrganizationService.java:76) |
| resolved_question_group_code 채움 정책 누락 | 부분 사실 | 컬럼은 추가됨. [V15__add_resolved_question_group_code.sql:1](/Users/leesumin/hiscope/src/main/resources/db/migration/common/V15__add_resolved_question_group_code.sql:1) RULE_BASED assignment 생성 시 채움, LEGACY는 NULL. [EvaluationAssignmentService.java:58](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/domain/evaluation/assignment/service/EvaluationAssignmentService.java:58), [EvaluationAssignmentService.java:59](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/domain/evaluation/assignment/service/EvaluationAssignmentService.java:59) |
| 퇴직/휴직 직원 처리 정책 누락 | 확정 사실 | 로그인 조회는 `employee.status='ACTIVE'`만 허용. [UserAccountRepository.java:40](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/domain/employee/repository/UserAccountRepository.java:40) 이관 정책 문서화 필요 |
| 과거 세션 relationship_generation_mode 정책 | 확정 사실 | 세션 mode 기본 `LEGACY`. [EvaluationSession.java:55](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/domain/evaluation/session/entity/EvaluationSession.java:55), [V8__add_rule_based_relationship_model.sql:1](/Users/leesumin/hiscope/src/main/resources/db/migration/common/V8__add_rule_based_relationship_model.sql:1) |
| organization_settings 이관 범위 누락 | 확정 사실 | 설정 테이블 존재/운영 사용 중. [V6__add_organization_settings.sql:1](/Users/leesumin/hiscope/src/main/resources/db/migration/common/V6__add_organization_settings.sql:1), [OrganizationSettingService.java:20](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/domain/settings/service/OrganizationSettingService.java:20) |
| accounts NULL organization_id unique hole | 확정 사실 | `(organization_id, login_id)`만으로는 NULL 행 중복 가능. [V12__scope_login_id_by_organization.sql:10](/Users/leesumin/hiscope/src/main/resources/db/migration/common/V12__scope_login_id_by_organization.sql:10) |
| AFFILIATE_HOSPITAL bootstrap 분기 적절성 | 추가 확인 필요 | 현재 로직은 `HOSPITAL_DEFAULT`만 병원 룰, 그 외는 계열사 룰. [OrganizationProfileBootstrapService.java:95](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/domain/organization/service/OrganizationProfileBootstrapService.java:95) 의도 여부는 운영 정책 결정 필요 |
| V14 organization_profile UPDATE no-op | 확정 사실(위험) | 기존 SQL이 `NOT NULL DEFAULT` 추가 후 `IS NULL` 조건 업데이트라 환경에 따라 실질 보정 누락 가능. [V14__add_organization_profile.sql:2](/Users/leesumin/hiscope/src/main/resources/db/migration/common/V14__add_organization_profile.sql:2), [V14__add_organization_profile.sql:10](/Users/leesumin/hiscope/src/main/resources/db/migration/common/V14__add_organization_profile.sql:10) |
| singleMemberDeptRule matcher 쉼표 의미 | 확정 사실 | 쉼표 분해 후 동일 matcher 내 OR, matcher 간 AND. [RelationshipGenerationService.java:328](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/domain/evaluation/rule/service/RelationshipGenerationService.java:328), [RelationshipGenerationService.java:260](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/domain/evaluation/rule/service/RelationshipGenerationService.java:260), [RelationshipGenerationService.java:210](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/domain/evaluation/rule/service/RelationshipGenerationService.java:210) |
| orgCode GET 파라미터 노출 리스크 | 기각(표현 과장) | UI 로그인은 POST 폼. [auth/login.html:48](/Users/leesumin/hiscope/src/main/resources/templates/auth/login.html:48) `getParameter`는 메서드와 무관하게 요청 파라미터 조회. [CustomUserDetailsService.java:93](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/common/security/CustomUserDetailsService.java:93) |
| 초기 비밀번호 하드코딩 리스크 | 확정 사실 | 업로드 사용자 초기 비밀번호 `password123` 하드코딩. [EmployeeUploadHandler.java:256](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/domain/upload/handler/EmployeeUploadHandler.java:256) |
| self-evaluation 허용 여부 | 부분 사실 | DB 제약 없음. [V1__init_schema.sql:116](/Users/leesumin/hiscope/src/main/resources/db/migration/common/V1__init_schema.sql:116) 다만 생성/override 로직은 자기평가를 제거/차단. [EvaluationRelationshipService.java:213](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/domain/evaluation/relationship/service/EvaluationRelationshipService.java:213), [RelationshipGenerationService.java:101](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/domain/evaluation/rule/service/RelationshipGenerationService.java:101), [SessionRelationshipOverrideService.java:62](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/domain/evaluation/rule/service/SessionRelationshipOverrideService.java:62) |
| KPI 이관 부재 | 확정 사실 | 신규 Java/DB 마이그레이션에 KPI 도메인 부재 (`src/main/java` 내 KPI 컨트롤러/엔티티 없음). 단 `pe-origin` 템플릿엔 레거시 KPI 흔적 존재 |

## 3. 이번 반영(코드)

### 3.1 V14 보정 리스크 대응
- 신규 마이그레이션에서 `AFFILIATE + HOSPITAL_DEFAULT`를 `AFFILIATE_GENERAL`로 보정.
- 파일: [V16__fix_org_profile_backfill_and_super_admin_login_scope.sql](/Users/leesumin/hiscope/src/main/resources/db/migration/common/V16__fix_org_profile_backfill_and_super_admin_login_scope.sql)

### 3.2 슈퍼관리자 login_id 중복 방지
- DB 제약은 현행 유지(`UNIQUE (organization_id, login_id)`).
- 로그인 로직에서 `organization_id IS NULL` 동일 loginId 다건을 명시적으로 차단.
- 파일: [CustomUserDetailsService.java](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/common/security/CustomUserDetailsService.java)

## 4. P0/P1/P2

- P0
  - 기관 bootstrap 누락 탐지/실행 절차
  - profile 오적용/슈퍼관리자 중복 탐지
  - mode/resolved_question_group_code 정책 확정
- P1
  - organization_settings 이관 범위 확정
  - INACTIVE/LEAVE 과거 평가 데이터 포함 정책
  - self-evaluation 탐지 리포트 운영화
- P2
  - KPI 2차 이관(신규 도메인 설계 후)
  - 레거시 업로드/감사 이력 이관 범위 확장
