# Production Deployment Checklist

기준일: 2026-03-11

## A. Release Gate (배포 시작 전)

- [ ] 릴리즈 대상 커밋/태그 확정
- [ ] `./gradlew clean test` 성공
- [ ] 스테이징 PostgreSQL 리허설 결과 확인 (`Go`)
- [ ] 변경사항/릴리즈 노트 공유 완료
- [ ] 배포/롤백 담당자 지정 완료

## B. Environment Gate (운영 설정 점검)

- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 설정 확인
- [ ] `SESSION_COOKIE_SECURE=true`
- [ ] `APP_BOOTSTRAP_SUPER_ADMIN_ENABLED=false`
- [ ] `APP_UPLOAD_MAX_FILE_SIZE_BYTES`, `APP_UPLOAD_MAX_ROWS` 확인
- [ ] `LOG_DIR` 경로/권한 확인
- [ ] 리버스 프록시의 `X-Forwarded-*` 전달 확인

## C. Database Gate

- [ ] 운영 DB 백업 완료 (시각/백업파일 경로 기록)
- [ ] `db/migration/common` 적용 계획 확인
- [ ] migration 충돌/중복 버전 없음 확인
- [ ] 복구/롤백 시나리오 공유

## D. Deployment Execution

- [ ] `.env` 최신값 반영
- [ ] 배포 실행

```bash
cp .env.example .env
docker compose -f docker-compose.prod.yml up -d --build
```

- [ ] 컨테이너 상태 확인

```bash
docker compose -f docker-compose.prod.yml ps
```

- [ ] 헬스체크 확인

```bash
curl -f http://<host>:8099/actuator/health
```

## E. Immediate Validation (배포 직후 15분)

- [ ] 앱 기동 정상
- [ ] 로그인 페이지 접근 정상
- [ ] 관리자 로그인/일반 사용자 로그인 정상
- [ ] 업로드 1건 성공/1건 실패 시나리오 정상
- [ ] 감사로그(`audit_logs`) 적재 확인

## F. Sign-off

- [ ] 운영 책임자 승인
- [ ] 개발 책임자 승인
- [ ] 최종 판정: `Go` / `No-Go`
