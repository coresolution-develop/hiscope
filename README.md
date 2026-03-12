# HiScope Evaluation (MVP Ops Ready)

다면평가 운영용 웹 애플리케이션입니다.  
Spring Boot + Thymeleaf + PostgreSQL + Flyway 기반으로 동작합니다.

## 1. 운영 관점 핵심 변경사항

- 로그 전략:
  - `logback-spring.xml` 적용
  - 일반 로그와 감사 로그(`AUDIT`) 분리 출력
  - 요청 추적용 `X-Request-Id`/MDC(`requestId`) 적용
- 감사 로그:
  - 로그인 성공/실패
  - 기관/관리자 생성, 세션 생성/시작/종료, 관계 추가/삭제, 업로드, 사용자 제출
  - DB 테이블 `audit_logs`에 저장 + audit 로그 파일 출력
- 환경 분리:
  - `application.yml`(공통)
  - `application-local.yml`(개발)
  - `application-prod.yml`(운영)
  - `application-test.yml`(테스트)
- DB migration 분리:
  - 공통 스키마: `db/migration/common`
  - 로컬 데모 데이터: `db/migration/local`
- 보안:
  - CSRF 쿠키 토큰 저장소 적용
  - CSP/Referrer/Frame/Content-Type 보안 헤더 명시
  - 세션 고정 보호, 세션 만료 URL 지정, 기본 쿠키 보안 옵션 강화
- 업로드 정책:
  - 빈 파일/확장자/파일 크기 검증 강화
  - 업로드 최대 행수 제한(`APP_UPLOAD_MAX_ROWS`, 기본 5,000행)
- v1.0 B 범위 반영:
  - 세션 복제 고도화(복제명 중복 방지/자동명 충돌 보정)
  - 결과 CSV 확장(배정별 CSV 추가)
  - 감사 로그 작업군(`actionGroup`) 필터 추가
  - 대시보드 위험지표/미제출 상위 지표 추가

## 2. 로컬 실행

### 사전 준비
- Java 17

### DB 준비
- 기본값은 내장 H2 DB라서 별도 DB 없이 바로 실행 가능
- PostgreSQL을 쓰려면 `application-local.yml` 또는 환경변수(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_DRIVER_CLASS_NAME`)로 설정

### 실행
```bash
./gradlew bootRun
```

기본 포트: `8099`  
기본 프로필: `local`

## 3. 테스트

```bash
./gradlew test
```

테스트는 H2 + Flyway(common/local)로 동작합니다.

## 4. 운영 배포 (Docker Compose)

### 환경변수 파일 생성
```bash
cp .env.example .env
```

### 배포
```bash
docker compose -f docker-compose.prod.yml up -d --build
```

애플리케이션: `http://localhost:8099`

## 5. 초기 관리자 계정 정책

- 자동 생성은 기본 비활성화(`APP_BOOTSTRAP_SUPER_ADMIN_ENABLED=false`)
- 운영에서 1회 계정 부트스트랩이 필요할 때만 아래 설정 사용:
  - `APP_BOOTSTRAP_SUPER_ADMIN_ENABLED=true`
  - `APP_BOOTSTRAP_SUPER_ADMIN_LOGIN_ID`
  - `APP_BOOTSTRAP_SUPER_ADMIN_PASSWORD`
- 계정 생성 후 반드시 비활성화 또는 비밀번호 변경

## 6. 운영 체크리스트

- DB 백업/복구 시나리오 점검
- 로그 저장 경로/보관 정책 점검(`LOG_DIR`)
- TLS 종료 지점(리버스 프록시)에서 HTTPS 강제
- 관리자 계정 비밀번호 정책 및 주기적 변경
- 감사 로그(`audit_logs`) 정기 점검

## 7. 관리자 운영 가이드

관리자 실제 사용 흐름은 아래 문서 참고:

- [ADMIN_OPERATIONS.md](docs/ADMIN_OPERATIONS.md)
- [ADMIN_MANUAL_DRAFT.md](docs/ADMIN_MANUAL_DRAFT.md)
- [EXCEL_UPLOAD_GUIDE_DRAFT.md](docs/EXCEL_UPLOAD_GUIDE_DRAFT.md)
- [UAT_CHECKLIST.md](docs/UAT_CHECKLIST.md)
- [E2E_OPERATION_SCENARIOS.md](docs/E2E_OPERATION_SCENARIOS.md)
- [OPS_RISK_ASSESSMENT.md](docs/OPS_RISK_ASSESSMENT.md)
- [PRE_DEPLOY_CHECKLIST.md](docs/PRE_DEPLOY_CHECKLIST.md)
- [DEPLOY_GUIDE_FINAL.md](docs/DEPLOY_GUIDE_FINAL.md)
- [ROLLBACK_GUIDE_FINAL.md](docs/ROLLBACK_GUIDE_FINAL.md)
- [ADMIN_MANUAL_FINAL.md](docs/ADMIN_MANUAL_FINAL.md)
- [EXCEL_UPLOAD_GUIDE_FINAL.md](docs/EXCEL_UPLOAD_GUIDE_FINAL.md)
- [TROUBLESHOOTING_GUIDE_FINAL.md](docs/TROUBLESHOOTING_GUIDE_FINAL.md)
- [GO_NO_GO_CHECKLIST_FINAL.md](docs/GO_NO_GO_CHECKLIST_FINAL.md)
- [STAGING_ENV_SETUP_FINAL.md](docs/STAGING_ENV_SETUP_FINAL.md)
- [STAGING_REHEARSAL_REPORT_FINAL.md](docs/STAGING_REHEARSAL_REPORT_FINAL.md)
- [PRODUCTION_DEPLOYMENT_CHECKLIST_FINAL.md](docs/PRODUCTION_DEPLOYMENT_CHECKLIST_FINAL.md)
- [POST_DEPLOY_SMOKE_TEST_CHECKLIST_FINAL.md](docs/POST_DEPLOY_SMOKE_TEST_CHECKLIST_FINAL.md)
- [INITIAL_MONITORING_CHECKLIST_FINAL.md](docs/INITIAL_MONITORING_CHECKLIST_FINAL.md)
- [INCIDENT_FIRST_RESPONSE_GUIDE_FINAL.md](docs/INCIDENT_FIRST_RESPONSE_GUIDE_FINAL.md)
- [STABILIZATION_BACKLOG_DRAFT.md](docs/STABILIZATION_BACKLOG_DRAFT.md)
- [V1_A_SCOPE_STATUS.md](docs/V1_A_SCOPE_STATUS.md)
- [V1_B_ENHANCEMENTS_STATUS.md](docs/V1_B_ENHANCEMENTS_STATUS.md)
- [DEFAULT_SORT_FILTER_POLICY.md](docs/DEFAULT_SORT_FILTER_POLICY.md)
