# P0 운영 파일 호환 Import 정책 보정안

## 1. 로그인 ID / 계정 식별 정책
- 운영 기준: 로그인 ID는 사번 그대로 사용.
- SaaS 확장 기준:
  - 계정 식별: `organization_id + login_id`
  - 내부 식별: PK(`accounts.id`, `user_accounts.id`) 유지
  - 기관 컨텍스트 로그인: 기관 코드(`orgCode`) + 로그인ID 조합 우선
- 보정 요약:
  - DB unique를 전역 login_id에서 org 스코프로 전환
  - orgCode 입력 시 기관 내 조회 우선
  - orgCode 미입력 시 중복 login_id면 로그인 거절(기관 코드 입력 유도)

## 2. 문제은행 문항군 처리 정책 (AA/AB/AC/AD/AE)
- 해석:
  - `AA/AB/AC/AD/AE`는 문항 타입이 아니라 문항군(템플릿 변형) 코드
- v1 최소안:
  - `evaluation_questions.question_group_code` 컬럼에 문항군 코드 저장
  - `question_type(SCALE/DESCRIPTIVE)`는 별도 유지
  - 운영 문제은행 import(`문제/문제유형/구분`) 지원
- profile별 허용 코드:
  - `HOSPITAL_DEFAULT`: `AA/AB`
  - `AFFILIATE_HOSPITAL`: `AC/AD`
  - `AFFILIATE_GENERAL`: `AC/AE`
- 배정 스냅샷 정책:
  - RULE_BASED 세션은 세션 시작 시 `resolved_question_group_code`를 assignment에 고정 저장
  - LEGACY 세션은 미적용(기존 전체 문항 사용)

## 2-1. organization_profile 정책
- organization_type 유지:
  - `HOSPITAL`
  - `AFFILIATE`
- organization_profile 추가:
  - `HOSPITAL_DEFAULT`
  - `AFFILIATE_HOSPITAL`
  - `AFFILIATE_GENERAL`
- 기본값:
  - `HOSPITAL` 생성 시 `HOSPITAL_DEFAULT`
  - `AFFILIATE` 생성 시 `AFFILIATE_GENERAL`

## 3. 부서 루트 정책 / 계층 확장 전략
- v1 운영:
  - 상위부서 코드가 없으면 `parent_id = null` 루트 부서로 등록
  - 운영 파일(B열 부서명, C열 부서코드) 직접 import 허용
- 확장성:
  - 도메인 `departments.parent_id` 유지
  - 향후 타 기관 계층 파일(상위부서코드 포함) import 시 기존 구조로 확장 가능

## 4. P0 호환 Import 프로파일 반영 범위
- 직원: 병원/계열사 실파일 헤더 위치 및 boolean 형식(TRUE/FALSE/0/1) 호환
- 부서: B/C 컬럼 기반 파일 + 상위부서 없는 루트 정책 호환
- 문제은행: 3열 구조(`문제`,`문제유형`,`구분`) 호환 + `question_group_code` 저장
