# 스테이징 환경 구성 및 실행 절차 (최종본)

기준일: 2026-03-11

## 1. 목표

운영과 최대한 유사한 구성에서 PostgreSQL 기준으로 애플리케이션 기동/검증을 수행한다.

## 2. 권장 구성 (Docker 기반)

구성:
- `app`: Spring Boot (`prod` 프로필)
- `db`: PostgreSQL 16
- `volume`: DB 데이터, 앱 로그

실행:
```bash
cp .env.example .env
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps
curl -f http://<host>:8099/actuator/health
```

필수 env:
- `SPRING_PROFILES_ACTIVE=prod`
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `SESSION_COOKIE_SECURE=true`
- `APP_BOOTSTRAP_SUPER_ADMIN_ENABLED=false`
- `APP_UPLOAD_MAX_FILE_SIZE_BYTES`
- `APP_UPLOAD_MAX_ROWS`
- `LOG_DIR`

## 3. 대체 구성 (Docker 미지원 환경)

Docker가 없는 환경에서는 실제 PostgreSQL 엔진 기반의 embedded postgres 리허설을 수행한다.

실행:
```bash
./gradlew test --tests "*StagingPostgresFinalRehearsalIntegrationTest"
```

특징:
- H2가 아닌 PostgreSQL 엔진 사용
- Spring Boot + Flyway + 업로드 + 감사로그 + 권한 흐름까지 통합 검증

## 4. PostgreSQL 기반 애플리케이션 기동 절차

Docker 기준:
1. PostgreSQL 기동
2. 앱 기동 (`prod`)
3. 헬스체크 확인

로컬 postgres 기준(예시):
```bash
SPRING_PROFILES_ACTIVE=prod \
DB_URL=jdbc:postgresql://localhost:5432/hiscope_eval \
DB_USERNAME=hiscope \
DB_PASSWORD=<password> \
./gradlew bootRun
```

## 5. 마이그레이션/초기 데이터 반영

원칙:
- 운영/스테이징: `db/migration/common`
- 테스트/리허설: 필요 시 `db/migration/local` 포함

점검:
- migration 충돌/중복 버전 확인
- `flyway_schema_history` 반영 여부 확인

초기 데이터:
- 운영/스테이징은 업무 계정 수동 생성 권장
- 테스트 리허설은 local seed 사용 가능

## 6. 검증 체크리스트

- [ ] 앱 기동 성공
- [ ] PostgreSQL 연결 성공
- [ ] Flyway 적용 성공
- [ ] 로그인/권한 경계 정상
- [ ] 기관별 데이터 격리 정상
- [ ] 엑셀 업로드 성공/실패 처리 정상
- [ ] 감사로그 및 요청 ID 추적 가능
- [ ] 대량 데이터 시나리오 정상
