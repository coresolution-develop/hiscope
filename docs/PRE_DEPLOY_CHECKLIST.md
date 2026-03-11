# 배포 전 필수 점검 체크리스트

## 1. 빌드/테스트

- [ ] `./gradlew clean test` 성공
- [ ] 운영 빌드 산출물(`jar`) 생성 확인
- [ ] Flyway migration 충돌/중복 버전 없음

## 2. 환경 변수

- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 설정
- [ ] `SESSION_COOKIE_SECURE=true`
- [ ] `APP_BOOTSTRAP_SUPER_ADMIN_ENABLED=false` (운영 기본)
- [ ] `LOG_DIR` 경로 및 권한 확인

## 3. 보안/권한

- [ ] 관리자/사용자 권한 분리 확인
- [ ] 기관 간 데이터 접근 차단 확인
- [ ] HTTPS 및 프록시 헤더 설정 확인
- [ ] CSP/보안 헤더 응답 확인

## 4. 데이터/마이그레이션

- [ ] 운영 DB 백업 완료
- [ ] Flyway 실행 계획 점검 (`common`만 적용)
- [ ] 초기 계정 부트스트랩 필요 시 1회만 활성화

## 5. 관측성/장애 대응

- [ ] `/actuator/health` 체크
- [ ] 애플리케이션 로그/감사 로그 파일 생성 확인
- [ ] 요청 ID 기반 로그 추적 테스트
- [ ] 알림/모니터링 연계(선택) 확인

## 6. 롤백 계획

- [ ] 직전 버전 이미지/아티팩트 확보
- [ ] DB 롤백 불가 migration 영향 검토
- [ ] 장애 시 트래픽 차단/롤백 절차 문서화
