# Post-Deploy Smoke Test Checklist

기준일: 2026-03-11

## 1. Platform

- [ ] `/actuator/health` = `UP`
- [ ] 애플리케이션 로그 파일 생성 확인
- [ ] 감사 로그 파일 생성 확인

## 2. Authentication & Authorization

- [ ] 최고 관리자 로그인 성공
- [ ] 기관 관리자 로그인 성공
- [ ] 일반 사용자 로그인 성공
- [ ] 일반 사용자의 `/admin/**` 접근 차단 확인(403)

## 3. Tenant Isolation

- [ ] 기관 A 관리자 계정으로 기관 B 자원 URL 접근 차단
- [ ] 기관 A 업로드/조회 화면에 기관 B 데이터 비노출 확인

## 4. Upload Flow

- [ ] 부서 엑셀 업로드 성공 1건
- [ ] 직원 엑셀 업로드 성공 1건
- [ ] 의도적 실패 업로드 1건(.txt 또는 형식 오류)
- [ ] 업로드 이력(`upload_histories`)에 SUCCESS/FAILED 기록 확인

## 5. Evaluation Flow

- [ ] 템플릿 생성
- [ ] 문항 추가/수정/삭제
- [ ] 평가 세션 생성
- [ ] 관계 자동생성 + 수동 추가/삭제
- [ ] 세션 시작
- [ ] 일반 사용자 평가 제출(complete 이동)

## 6. Audit Traceability

- [ ] 핵심 이벤트 감사로그 기록 확인
- [ ] 실패 이벤트 감사로그(Fail outcome) 확인
- [ ] requestId 기준 앱 로그 ↔ 감사로그 추적 가능 확인

## 7. Smoke Exit Criteria

- [ ] Critical 실패 0건
- [ ] High 실패 0건
- [ ] 미해결 이슈는 운영 우회책 포함
