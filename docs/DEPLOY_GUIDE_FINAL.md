# 배포 가이드 (최종본)

기준일: 2026-03-11

## 1. 목적

본 가이드는 운영 배포를 안전하게 수행하고, 배포 직후 서비스 정상 여부를 빠르게 확인하기 위한 절차를 정의한다.

## 2. 배포 전 준비

1. 코드/아티팩트
- 릴리즈 브랜치 확정
- `./gradlew clean test` 성공
- 배포 아티팩트 버전/태그 확정

2. DB
- 운영 DB 백업 완료
- 이번 릴리즈 Flyway migration 목록 검토
- `db/migration/common`만 적용되는지 확인

3. 환경변수
- `SPRING_PROFILES_ACTIVE=prod`
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `SESSION_COOKIE_SECURE=true`
- `APP_BOOTSTRAP_SUPER_ADMIN_ENABLED=false`
- `APP_UPLOAD_MAX_FILE_SIZE_BYTES`, `APP_UPLOAD_MAX_ROWS`
- `LOG_DIR`

## 3. 배포 절차 (Docker Compose 기준)

1. `.env` 준비
```bash
cp .env.example .env
```

2. 이미지 빌드/기동
```bash
docker compose -f docker-compose.prod.yml up -d --build
```

3. 상태 확인
```bash
docker compose -f docker-compose.prod.yml ps
curl -f http://<host>:8099/actuator/health
```

## 4. 배포 직후 스모크 테스트

1. 로그인 페이지 접근
- `/login` 접속 확인

2. 권한 경계 확인
- 일반 사용자로 `/admin/**` 접근 차단
- 기관 관리자로 타 기관 리소스 접근 차단

3. 핵심 기능 확인
- 부서/직원 업로드 1건씩 수행
- 업로드 이력 반영 확인
- 감사로그(`audit_logs`) 이벤트 기록 확인

## 5. 배포 완료 기준

- 헬스체크 `UP`
- 핵심 시나리오 성공
- 배포 차단 이슈 0건
- 운영 책임자 승인 완료
