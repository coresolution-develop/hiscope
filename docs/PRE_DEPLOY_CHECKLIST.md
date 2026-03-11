# 배포 전 필수 점검 체크리스트

## 1. 릴리즈 후보 검증

- [ ] `./gradlew clean test` 성공
- [ ] 배포 아티팩트(`jar`/이미지) 해시 및 버전 태깅 완료
- [ ] 현재 릴리즈에 포함된 DB migration 목록 확정
- [ ] 변경사항 중 운영 영향(권한/데이터/성능) 검토 완료

## 2. 보안/권한

- [ ] 권한별 접근 제어 점검 (`SUPER_ADMIN`/`ORG_ADMIN`/`USER`)
- [ ] 기관 간 URL 직접 접근 차단 확인
- [ ] 세션/쿠키 보안 옵션 확인 (`Secure`, `HttpOnly`, `SameSite`)
- [ ] TLS 종단(프록시/LB) 및 `X-Forwarded-*` 헤더 전달 정책 확인

## 3. 데이터/마이그레이션

- [ ] 운영 DB 전체 백업 완료 (복구 리허설 포함)
- [ ] Flyway 충돌/중복 버전 없음
- [ ] prod에서 `db/migration/common`만 적용됨 확인
- [ ] 롤백 불가능 migration 영향 검토 및 비상 시나리오 문서화

## 4. 운영 설정 분리

- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 설정
- [ ] `APP_BOOTSTRAP_SUPER_ADMIN_ENABLED=false`
- [ ] `APP_UPLOAD_MAX_FILE_SIZE_BYTES`, `APP_UPLOAD_MAX_ROWS` 정책값 검토
- [ ] `LOG_DIR` 쓰기 권한 및 용량 정책 확인

## 5. 관측성/감사 추적

- [ ] `/actuator/health` 정상
- [ ] 앱 로그/감사로그 파일 생성 확인
- [ ] 감사로그 DB 적재 확인 (`audit_logs`)
- [ ] 요청 ID 기반 추적 테스트 (오류 1건 의도 발생 후 로그 추적)

## 6. 성능/부하 리허설

- [ ] 기관 관리자 핵심 화면(직원/세션/감사로그) 응답시간 확인
- [ ] 업로드 경계값 테스트(허용 크기/행수 근접)
- [ ] 동시 로그인/제출 시 오류율 점검

## 7. 배포 실행/검증 절차

- [ ] 배포 전 공지/점검창 확정
- [ ] 배포 명령/파이프라인 실행
- [ ] 배포 직후 smoke test 완료
- [ ] 장애 시 롤백 책임자/실행 명령/판단 기준 확인

## 8. 최종 Go/No-Go

- [ ] Critical 이슈 0
- [ ] 미해결 이슈에 대한 우회책/모니터링 계획 존재
- [ ] 승인자(개발/운영/보안) 서명 완료
