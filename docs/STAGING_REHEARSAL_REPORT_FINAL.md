# 스테이징 최종 리허설 결과 (최종본)

기준일: 2026-03-11
검증 방식: PostgreSQL 엔진 기반 통합 리허설
테스트: `StagingPostgresFinalRehearsalIntegrationTest`

## 1. 실행 절차

1. PostgreSQL 엔진 준비 (embedded postgres)
2. 앱 컨텍스트 기동
3. Flyway migration 적용 (`common + local`)
4. 역할별 E2E 시나리오 수행
5. 감사로그/업로드 이력/권한/기관격리 확인

실행 명령:
```bash
./gradlew test --tests "*StagingPostgresFinalRehearsalIntegrationTest"
```

## 2. 시나리오 체크리스트 결과

- [x] 최고 관리자 기관 생성
- [x] 기관 관리자 생성 및 로그인
- [x] 부서/사용자 엑셀 업로드
- [x] 평가 항목 업로드/수정/삭제
- [x] 평가 세션 생성
- [x] 평가 관계 자동 생성 및 수동 조정
- [x] 일반 사용자 평가 제출
- [x] 감사 로그 및 오류 추적 확인

## 3. 발견 이슈

## 배포 차단 이슈
- 없음

## 수정 후 배포 가능 이슈
- 없음

## 후속 백로그
- Docker 기반 실스테이징(운영망/LB/스토리지 포함)에서 동일 시나리오 자동화 파이프라인 추가

## 4. 분류 결과

- 결론: `즉시 배포 가능`

근거:
- PostgreSQL 기준 리허설 시나리오 전 항목 통과
- 권한 경계/기관 격리/업로드 실패 처리/감사 추적 확인 완료
- 전체 테스트 통과
