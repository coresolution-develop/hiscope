# AGENTS.md

## 1. 목적과 범위
- 이 문서는 `hiscope` 저장소에서 작업하는 모든 에이전트의 공통 실행 규칙이다.
- 목표는 기능 추가보다 먼저 운영 안정성(권한, 감사로그, 데이터 정합성, 마이그레이션 안전성)을 지키는 것이다.
- 적용 범위는 루트 전체이며, 특히 `src/main`, `src/test`, `src/main/resources/db/migration`, `docs` 변경 시 반드시 따른다.

## 2. 프로젝트 사실(작업 전 공통 인지)
- 기술 스택: Java 17, Spring Boot 3.2.x, Gradle Kotlin DSL, Thymeleaf, Spring Security, JPA, Flyway.
- 실행 프로필: 기본은 `local`, 테스트는 `test`를 사용한다.
- 로컬 DB 기본값은 `application-local.yml` 기준 PostgreSQL(`jdbc:postgresql://localhost:5432/hiscope_eval`)이다.
- 테스트는 Testcontainers PostgreSQL(`jdbc:tc:postgresql:17`)을 사용하므로 Docker가 필요하다.
- 운영 핵심: 멀티테넌시(org 스코프), 감사로그(`audit_logs` + `AUDIT` logger), 요청 추적(`X-Request-Id`).
- DB 마이그레이션 경로: 공통 스키마는 `src/main/resources/db/migration/common`, 로컬 시드는 `src/main/resources/db/migration/local`.

## 3. 기본 실행 명령
- 로컬 실행: `./gradlew bootRun`
- 전체 테스트: `./gradlew test`
- 전체 검증 빌드: `./gradlew clean test`
- 특정 테스트: `./gradlew test --tests "com.hiscope.evaluation.unit.*"`
- 프로덕션 컨테이너 기동: `docker compose -f docker-compose.prod.yml up -d --build`

## 4. 코드 구조 규칙
- 도메인 구조는 `domain/<기능>/{controller,dto,entity,repository,service}` 패턴을 유지한다.
- 공통 기능은 `common/*`, 환경/보안/프로퍼티는 `config/*`에 둔다.
- 화면은 MVC(Thymeleaf) 중심이다. 컨트롤러는 템플릿 경로 반환/리다이렉트를 기본으로 한다.
- API/화면의 에러 처리 모델은 `BusinessException + ErrorCode + GlobalExceptionHandler`를 기준으로 통일한다.

## 5. 보안/권한/테넌시 필수 규칙
- 엔드포인트 권한 경계는 `SecurityConfig` 기준으로 유지한다.
- `/super-admin/**`는 `ROLE_SUPER_ADMIN`만 접근 가능해야 한다.
- `/admin/**`는 `ROLE_ORG_ADMIN` 또는 `ROLE_SUPER_ADMIN`만 접근 가능해야 한다.
- `/user/**`는 `ROLE_USER`만 접근 가능해야 한다.
- 기관 데이터 접근은 반드시 org 스코프를 강제한다.
- 서비스 계층에서 `SecurityUtils.checkOrgAccess(...)` 또는 동등한 검증을 수행한다.
- Repository 조회는 가능하면 `organizationId + id` 형태 메서드를 우선 사용한다.
- 새 폼/POST 추가 시 CSRF 흐름을 깨지 않도록 구현한다.

## 6. 서비스/트랜잭션 규칙
- 서비스 클래스 기본은 `@Transactional(readOnly = true)` 패턴을 따른다.
- 쓰기 작업 메서드에만 `@Transactional`을 명시한다.
- `REQUIRES_NEW`는 독립 커밋이 필요한 경우(예: 업로드 이력, 감사로그)로 제한한다.
- 엔티티 상태 변경은 서비스에서 필드 직접 수정보다 엔티티 메서드(`update`, `deactivate`, `start` 등) 사용을 우선한다.

## 7. 감사로그/운영추적 규칙
- 운영 의미가 있는 상태 변경에는 `AuditLogger.success/fail` 호출을 추가한다.
- 액션명은 기존 스타일(대문자 스네이크 케이스, 예: `DEPT_CREATE`)을 따른다.
- `targetType`, `targetId`, `detail(AuditDetail.of(...))`를 누락하지 않는다.
- 오류 대응 가능성을 위해 예외/실패 시에도 가능한 한 감사 이력을 남긴다.

## 8. DB/Flyway 규칙
- 스키마 변경 시 기존 마이그레이션 파일 수정 대신 새 버전 파일(`V{N}__*.sql`)을 추가한다.
- 운영 영향 변경은 `common`에, 로컬 전용 데이터는 `local`에 분리한다.
- DB 변경 시 JPA Entity/Repository, 서비스 비즈니스 로직, 테스트(통합 또는 단위), 운영/이관 문서(`docs/*`, `docs/migration/*`)를 함께 맞춘다.

## 9. 테스트 규칙
- 비즈니스 흐름 변경: `scenario` 통합테스트(MockMvc, `@SpringBootTest`, `@ActiveProfiles("test")`) 보강.
- 순수 로직 변경: `unit` 테스트(Mockito) 보강.
- 권한/조직 경계/재제출/세션 상태 같은 정책성 로직은 회귀 테스트를 우선 추가한다.
- 테스트 실행 결과를 확인하지 못했으면 결과를 추정하지 말고 “미실행/실패”를 명시한다.

## 10. 프런트/템플릿 규칙
- 템플릿은 `templates/admin`, `templates/user`, `templates/super-admin` 중심으로 수정한다.
- 정렬/필터/페이지네이션은 `docs/DEFAULT_SORT_FILTER_POLICY.md` 기본값/보정 규칙을 따른다.
- 폼 검증 실패는 기존 UX 패턴(리다이렉트 + flash message 또는 same view 반환)을 유지한다.

## 11. AI 요약(OpenAI) 관련 규칙
- OpenAI 기능은 설정 기반 토글(`app.mypage.ai.enabled`)을 유지한다.
- API 키는 코드에 하드코딩하지 않고 환경변수(`OPENAI_API_KEY`)만 사용한다.
- OpenAI 호출 실패 시 fallback 정책(`fallback-to-heuristic`)을 깨지 않도록 변경한다.

## 12. 수정 금지/주의 영역
- 생성 산출물(`build/`, `logs/`, `bin/`)은 수동 편집하지 않는다.
- 비밀값 파일(`.env`)은 생성/수정 시 반드시 템플릿(`.env.example`) 기준을 지킨다.
- 레거시 자산(`templates/pe-origin`, `static/pe-origin`)은 명시적 요구가 없으면 광범위 수정하지 않는다.

## 13. 작업 완료 체크리스트
- 변경 범위에 맞는 테스트를 실행했고 결과를 확인했다.
- 권한/테넌시 검증 누락이 없다.
- 운영 이벤트에 대한 감사로그 누락이 없다.
- DB 스키마 변경 시 Flyway/엔티티/테스트/문서를 동기화했다.
- 사용자 영향이 큰 정책 변경은 관련 문서(`README.md`, `docs/*`)를 갱신했다.
