# 레거시 실측 테이블 기반 신규 이관 매핑 (실측 반영안)

기준 저장소: `hiscope`  
기준일: 2026-03-19  
근거: 현재 저장소 엔티티/DDL + 사용자 제공 레거시 실측 DDL/샘플

## 1. 파일럿 범위 (확정)

- 파일럿 기관: `효사랑가족요양병원`
- 기관 식별 우선 컬럼:
  - 인사/조직 원천: `users_2025.c_name`, `users_2025.c_name2`
  - KPI 원천: `kpi_eval_clinic.hospital_name`, `kpi_personal_2025.hospital_name`
  - KPI 일반정보: `kpi_info_general_2025.kcol01`
- 추출 SQL은 위 값 기반으로 파일럿 1기관만 선택하도록 구성한다.

## 2. 레거시 테이블별 1차 처리

| 레거시 테이블 | 신규 대상/트랙 | 처리 방안 | 상태 |
|---|---|---|---|
| `users_2025` | `employees`, `user_accounts`, `employee_attribute_values`(일부) | 직원/계정 원천으로 사용. `pwd`는 bcrypt 해시로 보고 `user_accounts.password_hash` 직접 이관 가능성 검토 | 확정(원천), 일부 가정 |
| `user_roles_2025` | `employee_attribute_values`, RULE_BASED matcher 입력 | `team_head`, `team_member`, `sub_head`, `sub_member`, `one_person_sub`, `medical_leader`를 역할 분류 원천으로 사용. role 의미(경혁팀장/경혁팀원/부서장/부서원/1인부서)는 확정 | 확정(원천), 일부 미확정 |
| `sub_management` | `departments` | 부서 코드/명 원천으로 적재 | 확정 |
| `team` | `departments` 보조 또는 속성(`change_innovation_team*`) 보조 | `GH_TEAM` 등 팀 분류 원천. role/속성 최종 해석은 `user_roles_2025` 매핑 규칙과 함께 적용 | 확정(보조 원천) |
| `admin_default_targets` | 관계 기본 원천 | 기본 자동 매핑 원천. `LEGACY`/`RULE_BASED` 모드별 적재 경로 분기 | 확정 |
| `admin_custom_targets` | 관계 override 원천 | 관리자 수동 추가/override 원천. `reason`, `is_active` 보존 | 확정 |
| `evaluation_submissions` | `evaluation_assignments`, `evaluation_responses`, `evaluation_response_items` | 제출 원천. `answers_json.radios/essays`를 item으로 전개 | 확정 |
| `evaluation_comment_summary` | `migration_archive` | 캐시/파생 데이터로 archive 우선, 핵심 트랜잭션 이관 제외 | 확정 |
| `kpi_eval_clinic` | `migration_archive` (KPI 트랙) | 진료부/의료진 KPI 원천, 1차 archive-only | 확정 |
| `kpi_personal_2025` | `migration_archive` (KPI 트랙) | 병원 일반직원 KPI 원천, 1차 archive-only | 확정 |
| `kpi_info_general_2025` | `migration_archive` (KPI 트랙) | 경혁팀 KPI 원천, 1차 archive-only | 확정 |
| `notice_v2` | `migration_archive` | 공지 원천이나 신규 핵심 도메인 부재로 1차 archive-only | 확정 |

## 3. 핵심 매핑 상세

### 3.1 `users_2025` -> `employees` / `user_accounts` / `employee_attribute_values`

- `employees`
  - `id` -> `employee_number`
  - `name` -> `name`
  - `position` -> `job_title` (원문 직책 문자열 보존)
  - `sub_code` -> `departments.code` 조인 키
  - `eval_year`는 파일럿 연도 필터 및 세션/관계 매핑의 기준 연도로 사용
- `user_accounts`
  - `id` -> `login_id`
  - `pwd` -> `password_hash` (bcrypt 문자열 보존)
- `employee_attribute_values`(보조)
  - `c_name`, `c_name2`, `phone`, `create_at`, `delete_at`, `del_yn` 등은 핵심 컬럼 외 보조 속성으로 보존 가능

판정:
- 확정: `users_2025`가 직원/로그인 원천이며 `pwd`가 bcrypt 형식(`$2a$...`)으로 관측됨
- 가정: `delete_at`/`del_yn` -> `employees.status`의 최종 매핑 규칙

### 3.2 `user_roles_2025` + `team` + `sub_management` -> 속성/규칙 구조

- 확정 사실
  - `user_roles_2025.role`은 직원 평가 역할 분류 원천
  - 관측 role 집합: `team_head`, `team_member`, `sub_head`, `sub_member`, `one_person_sub`, `medical_leader`
  - role 의미 확정:
    - `team_head` = 경혁팀장
    - `team_member` = 경혁팀원
    - `sub_head` = 부서장
    - `sub_member` = 부서원
    - `one_person_sub` = 1인부서
- 1차 매핑 원칙
  - `one_person_sub` -> `single_member_department=Y` 후보
  - `medical_leader` -> `medical_leader=Y` 후보
  - `team_head`, `team_member`, `sub_head`, `sub_member`는 RULE_BASED matcher 입력을 위한 속성으로 적재
- 미확정
  - role -> 신규 `attribute_key` 최종 매핑/우선순위 (`team_head/sub_head/team_member/sub_member/one_person_sub/medical_leader`)

### 3.3 `admin_default_targets` / `admin_custom_targets` -> 관계/override

- 확정 의미
  - `data_ev`: 평가 관계 코드 (`A`~`G`)
    - `A` = 진료팀장 -> 진료부
    - `B` = 진료부 -> 경혁팀
    - `C` = 경혁팀 -> 진료부
    - `D` = 경혁팀 -> 경혁팀
    - `E` = 부서장 -> 부서원
    - `F` = 부서원 -> 부서장
    - `G` = 부서원 -> 부서원
  - `data_type`: 평가 유형 코드
    - `AA` = 10문항 세트 (부서장 평가, 진료부 평가, 경혁팀 평가)
    - `AB` = 20문항 세트 (부서원 평가)
  - `eval_type_code`: 평가자→평가대상 관계 성격 코드
    - `GH_TO_GH` = 경혁팀 -> 경혁팀
    - `GH_TO_MEDICAL` = 경혁팀 -> 진료부
    - `SUB_HEAD_TO_MEMBER` = 부서장 -> 부서원
    - `SUB_MEMBER_TO_HEAD` = 부서원 -> 부서장
    - `SUB_MEMBER_TO_MEMBER` = 부서원 -> 부서원
    - `GH` = 평가대상: 경혁팀
    - `MEDICAL` = 평가대상: 진료부
- 확정 처리
  - `admin_default_targets`: 기본 자동 매핑 원천
  - `admin_custom_targets`: 관리자 수동 추가(override) 원천
- 신규 연결 전략
  - `RULE_BASED` 세션: `session_relationship_overrides`에 반영 (수동 추가/제거 이력)
  - `LEGACY` 세션: `evaluation_relationships(source='ADMIN_ADDED')` 또는 동등 구조 반영
- 보존 정책
  - `admin_custom_targets.reason` 보존
  - `admin_custom_targets.is_active` 보존
  - `updated_at/created_at`는 감사 목적 보조 이관

주의:
- `data_ev`/`eval_type_code`는 확정 코드표로 운영하며, 변환/검증 SQL에서 동일 코드셋을 사용한다.

### 3.4 `evaluation_submissions` -> assignments / responses / response_items

확정 사실:
- 한 행이 평가자-대상자 제출 1건
- `answers_json`에 `radios` + `essays` 동시 저장
- `total_score`, `avg_score`는 item 기반 파생값
- `del_yn`, `is_active`, `version` 존재
- `evaluation_submissions` 자체에는 신규 `evaluation_sessions.id`를 직접 가리키는 키가 없음

1차 변환 원칙:
- 세션/배정 연결
  - 제출 행은 `map_assignment`(legacy assignment key -> new assignment id) 선행 매핑을 통해 `evaluation_assignments`에 연결
  - `eval_year+evaluator_id+target_id+data_ev+data_type` 복합키를 assignment key 후보로 사용
- 헤더(`evaluation_responses`) 적재 전 필터
  - `del_yn='N'`만 대상
  - 동일 `(eval_year, evaluator_id, target_id, data_ev, data_type)` 내 우선순위:
    1. `is_active=1` 우선
    2. `version` 최신 우선
    3. `updated_at`/`created_at` 최신 우선
- 문항 item(`evaluation_response_items`) 전개
  - `answers_json.radios` -> score item
  - `answers_json.essays` -> text item
- `answers_json.radios` 라벨 원점수(확정):
  - `매우우수`=5, `우수`=4, `보통`=3, `미흡`=2, `매우미흡`=1
- `AA/AB` 환산 구조(확정):
  - `AA`는 10문항 세트이므로 문항당 10점 기준 (raw 5점 -> 환산 10점)
  - `AB`는 20문항 세트이므로 문항당 5점 기준 (raw 5점 -> 환산 5점)
- 저장 기준(코드 근거):
  - 신규 `evaluation_response_items.score_value`는 **raw score(1~5)** 저장
  - 근거 1: `EvaluationResponseService.validateAnswers`는 SCALE 점수를 `1~maxScore`로 검증
  - 근거 2: `MyPageService`, `EvaluationResultService`는 `score_value`를 직접 평균 집계
  - 근거 3: `QuestionUploadHandler` OPS_BANK 변환 기본 SCALE `maxScore=5`
  - 결론: AA/AB 환산점수는 저장값이 아니라 집계/비교 단계 파생값으로 관리
  - 참조 코드:
    - [EvaluationResponseService.java](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/domain/evaluation/response/service/EvaluationResponseService.java:146)
    - [MyPageService.java](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/domain/mypage/service/MyPageService.java:196)
    - [EvaluationResultService.java](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/domain/evaluation/result/service/EvaluationResultService.java:415)
    - [QuestionUploadHandler.java](/Users/leesumin/hiscope/src/main/java/com/hiscope/evaluation/domain/upload/handler/QuestionUploadHandler.java:175)
- `total_score`, `avg_score`
  - 직접 적재 핵심 컬럼이 아니라 검증/비교용으로만 활용

#### 문항키 파싱 규칙 (확정)

문항 마스터: `evaluation(idx, d1, d2, d3, eval_year)`

- `evaluation.idx` = 문항 번호
- `d2` = 평가 유형 코드 (`AA`/`AB`)
- `d3` = 구분 (`섬김`, `배움`, `키움`, `나눔`, `목표관리`, `주관식`)

`answers_json` 키 해석:
- `나39` = `d3='나눔'` + `idx=39`
- `목41` = `d3='목표관리'` + `idx=41`
- `t43` = `d3='주관식'` + `idx=43`
- `t64` = `d3='주관식'` + `idx=64`
- 동일 패턴으로 `섬33`, `배35`, `키37` 등 prefix + idx

핵심 원칙:
- 레거시 키를 신규 `question_id`로 즉시 직결하지 않는다.
- `evaluation.idx/d2/d3` 기반 `migration_staging.map_legacy_question_key`를 먼저 만든 후,
  그 다음 신규 `map_question`과 연결한다.

### 3.5 `evaluation_comment_summary` 처리

- 성격: AI 요약 캐시(원천 제출 데이터 아님)
- 1차 방안: 핵심 이관 제외, `migration_archive` 보관
- archive 보존 필드:
  - `kind` (`ESSAY`, `SCORE`, `TOTAL`, `SCORE_KY`, `MIXED`)
  - `model`, `status`, `error_msg`, `summary`
- 명시:
  - `TOTAL`, `SCORE_KY`는 KPI 결합 결과를 포함할 수 있으므로 핵심 다면평가 원천으로 취급하지 않음

### 3.6 KPI 3개 테이블 처리

- `kpi_eval_clinic`: 진료부/의료진 KPI 원천
- `kpi_personal_2025`: 병원 일반직원 KPI 원천
- `kpi_info_general_2025`: 경혁팀 KPI 원천

처리 방안:
- 1차: archive-only
- 2차: KPI 전용 도메인/DDL 설계 후 별도 이관

### 3.7 `notice_v2` 처리

- 공지사항 원천 테이블은 확정
- 현재 hiscope 핵심 스키마에 notice 도메인이 없어 1차 핵심 이관 제외
- 1차는 archive-only, 2차 기능 범위 확정 후 별도 이관

## 4. 확정 / 가정 / 미확정

### 4.1 확정

- 파일럿 기관은 `효사랑가족요양병원`
- `evaluation.idx/d2/d3` 기준 문항키 파싱 규칙
- `data_ev` 확정 코드표
  - `A`=진료팀장->진료부, `B`=진료부->경혁팀, `C`=경혁팀->진료부, `D`=경혁팀->경혁팀, `E`=부서장->부서원, `F`=부서원->부서장, `G`=부서원->부서원
- `data_type` 의미/대상 확정 (`AA`=10문항/부서장·진료부·경혁팀, `AB`=20문항/부서원)
- `eval_type_code` 확정 코드표
  - `GH_TO_GH`, `GH_TO_MEDICAL`, `SUB_HEAD_TO_MEMBER`, `SUB_MEMBER_TO_HEAD`, `SUB_MEMBER_TO_MEMBER`, `GH`, `MEDICAL`
- radio label 원점수 확정 (`매우우수5/우수4/보통3/미흡2/매우미흡1`)
- `evaluation_response_items.score_value` 저장 기준은 raw score(1~5) 확정
- role 의미 확정 (`team_head`/`team_member`/`sub_head`/`sub_member`/`one_person_sub`)
- `admin_default_targets`=기본 자동 매핑 원천
- `admin_custom_targets`=수동 override 원천
- `evaluation_submissions`는 제출 원천이며 `del_yn/is_active/version` 고려 필요
- `evaluation_comment_summary`는 캐시/파생 데이터
- KPI 3개/`notice_v2`는 1차 핵심 도메인 직접 이관 제외

### 4.2 가정

- `users_2025.pwd`를 신규 인증체계에서 추가 재해시 없이 검증 가능
- 제출의 assignment 복원 키(`eval_year+evaluator_id+target_id+data_ev+data_type`)를 운영 매핑에서 사용 가능

### 4.3 미확정

- `user_roles_2025.role` -> 신규 `attribute_key` 최종 매핑/우선순위
- `evaluation_submissions` -> `evaluation_assignments` 연결 시 추가 보강키 채택 여부

## 5. 연결 파일

- 추출 SQL: `scripts/migration/extract_pilot_org_from_legacy.sql`
- 제출 변환 SQL: `scripts/migration/transform_legacy_submissions.sql`
- AI 요약 아카이브: `scripts/migration/archive_legacy_ai_summary.sql`
- KPI 아카이브: `scripts/migration/archive_legacy_kpi.sql`
- 갭 분석: `docs/migration/LEGACY_GAP_ANALYSIS.md`
