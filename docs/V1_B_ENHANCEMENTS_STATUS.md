# v1.0 B Scope Enhancements Status

기준일: 2026-03-12  
대상: 다면평가 시스템 v1.0 가능하면 포함(B)

## 1. 진행 요약

- B 범위 핵심 항목 4개를 운영 가능 수준으로 반영 완료.
- 기능 추가와 함께 시나리오 통합 테스트로 회귀 검증 수행.

## 2. 완료 항목

### 2.1 세션 복제 고도화

- 복제 이름 중복 방지
  - 명시 입력 이름이 기존 세션과 중복되면 생성 차단
  - 자동 생성 이름 충돌 시 `#2`, `#3` 순번 보정
- UI 안내 문구 추가

### 2.2 결과 다운로드 고도화

- 기존 다운로드:
  - 대상자별 CSV
  - 부서별 CSV
  - 미제출자 CSV
- 추가 다운로드:
  - 배정별 CSV (`assignments.csv`)
  - 평가자/피평가자/상태/제출시각/평균점수 포함
  - 키워드/부서/정렬 파라미터 반영

### 2.3 관리자 작업 이력 강화

- 감사 로그에 `actionGroup`(작업군) 필터 추가:
  - `ORG_ADMIN_OPERATIONS`
  - `USER_ADMIN_OPERATIONS`
  - `UPLOAD_OPERATIONS`
  - `SESSION_OPERATIONS`
  - `RELATIONSHIP_OPERATIONS`
  - `RESULT_DOWNLOAD_OPERATIONS`
  - `AUTH_OPERATIONS`
- 조회/페이징/CSV export 모두 동일 필터 적용

### 2.4 대시보드 개선

- 슈퍼관리자 대시보드
  - 위험 기관 수
  - 기관별 마감경과 진행중 세션 수
  - 기관별 최근 7일 실패 작업 수
- 기관관리자 대시보드
  - 평가자 미제출 상위 리스트

## 3. 운영 사용 포인트

- 세션 복제:
  - `평가 세션 상세 > 세션 복제 옵션`
- 결과 다운로드:
  - `결과 조회 > 배정별 CSV`
- 감사 로그 작업군 필터:
  - `감사 로그 > 작업군`
- 위험 기관/미제출 상위 확인:
  - `슈퍼관리자 대시보드`, `기관관리자 대시보드`

## 4. 테스트 검증

- `SecurityAndAdminScenarioIntegrationTest`
- `EvaluationWorkflowScenarioIntegrationTest`

위 시나리오에서 B 범위 추가 기능의 주요 경로를 검증함.
