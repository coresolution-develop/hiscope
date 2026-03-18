# RULE_BASED 운영 리허설/승인 패키지 (실데이터 UAT)

> 조직 유형(HOSPITAL/AFFILIATE)별 기본 프로파일 검증/보정 내용은
> `docs/operations/organization_type_rule_profile_validation.md`를 함께 참고한다.
>
> 운영 파일 호환 import 정책(login_id 스코프, profile 기반 문항군, 부서 루트 정책)은
> `docs/operations/p0_import_profile_policy.md`를 함께 참고한다.

## 1. 실제 데이터 UAT 체크리스트

### 1-1. 리허설 전 준비
- [ ] 대상 병원/기관 코드, 리허설 책임자, 승인자 지정
- [ ] 기준 일자 확정 (직원/부서 마스터 스냅샷 기준일)
- [ ] 리허설 대상 범위 확정
  - 포함 부서
  - 제외 부서
  - 제외 직원(휴직/파견/신규 미반영 등)
- [ ] `직원_업로드_템플릿.xlsx` 최신본 다운로드 및 배포
- [ ] 리허설용 세션 명명 규칙 확정 (예: `2026Q2_병원A_UAT_RULE_BASED`)

### 1-2. 실데이터 업로드 검증
- [ ] 부서 업로드 성공
- [ ] 직원 업로드 성공
- [ ] 속성 컬럼 반영 확인
  - 기본 속성: `경혁팀`, `경혁팀장`, `부서장`, `1인부서`, `진료팀장`, `평가제외`, `의료리더`, `기관장`, `소속장`
  - 자유 속성: `attr:*`
  - 독립 속성 원칙: `경혁팀/경혁팀장/부서장`은 서로 추론하지 않고 개별 확인
  - 보조 컬럼: `평가대상여부`, `이전부서명`은 v1에서 검증/점검 목적 사용
- [ ] 업로드 실패 행에 대해 오류 CSV 기준 수정/재업로드 완료
- [ ] 핵심 표본 직원 20명 수기 대조 완료
  - 부서/직책
  - 평가제외 여부
  - 병원형 속성값

### 1-3. RULE_BASED 생성 검증
- [ ] 리허설 세션 생성 (`RULE_BASED`, definition set 지정)
- [ ] 자동 생성 실행
- [ ] 실행 이력 확인
  - 생성 건수
  - 제외 건수
  - 자기평가 제거 건수
  - 중복 제거 건수
  - override 반영 건수
  - 최종 건수
  - 실행자/실행시각/실패 사유
  - 룰별 생성 건수
- [ ] `start` 직전 최신 `auto-generate` 실행 완료 여부 확인
- [ ] override 변경이 있었다면 `auto-generate` 재실행 완료 여부 확인
- [ ] 관계 목록 표본 검증
  - 부서장/팀장 라인
  - 평가제외 대상
  - 1인부서/기관장/소속장
- [ ] assignment 생성 전 최종 관계 표본 검증 완료
- [ ] assignment 표본의 `resolved_question_group_code` 확인 완료

### 1-4. LEGACY 비교 검증
- [ ] `LEGACY 비교` 실행
- [ ] 결과 수치 기록
  - 공통 건수
  - RULE_BASED 전용 건수
  - LEGACY 전용 건수
- [ ] 차이 Top 20건 원인 분류 완료

### 1-5. 운영 전환 직전 확인
- [ ] 필요한 override(ADD/REMOVE) 반영
- [ ] 자동 생성 재실행 후 수치 재확인
- [ ] 승인자 리뷰 완료
- [ ] 세션 시작 전 최종 승인 회의록 기록
- [ ] 세션 시작 시 재생성되지 않고 기존 generated snapshot을 사용한다는 점을 운영자에게 재확인

---

## 2. 차이 분석 가이드

### 2-1. LEGACY vs RULE_BASED 차이 분석 절차
1. 비교 수치 확인 (공통/각 전용)
2. 전용 건수에서 표본 20~50건 추출
3. 아래 대표 원인 분류표로 태깅
4. 분류별 조치(룰 수정/속성 수정/override) 결정
5. 재생성 후 재비교

### 2-2. 대표 원인 분류표
- 조직 마스터 이슈
  - 부서코드/상위부서 구조 불일치
  - 직원 부서 소속 누락/오입력
- 직원 기본정보 이슈
  - 직책(`팀장` 등) 오입력
  - 상태(`ACTIVE`/`INACTIVE`) 불일치
- 속성 이슈
  - boolean 값 형식 오류 (`MAYBE` 등)
  - `평가제외` 미반영/반영 과다
  - 자유 속성 컬럼명 규칙 미준수 (`attr:` 누락)
- 룰 설정 이슈
  - 우선순위 충돌
  - matcher 대상(subject_type) 설정 오류
  - IN/NOT_IN 설정 반대
- 정책 차이(의도된 차이)
  - LEGACY는 단순 직책/부서 기반, RULE_BASED는 병원형 정책 반영

### 2-3. 차이 분석 점검표
- [ ] 차이가 데이터 품질 이슈인지 정책 이슈인지 분리됨
- [ ] 데이터 수정 대상과 룰 수정 대상이 구분됨
- [ ] override 임시 조치와 다음 회차 룰 반영이 구분됨

---

## 3. override 운영 가이드

### 3-1. override 적용이 필요한 대표 사례
- ADD 필요 사례
  - 신규 조직개편 직후, 마스터 반영 전 임시 평가관계 보강
  - 병원장 지시로 특정 직군 간 평가 관계 임시 추가
- REMOVE 필요 사례
  - 민감 보직/겸직으로 해당 회차 평가 제외 필요
  - 예외 인사(장기휴가/파견)로 관계 제거 필요

### 3-2. 운영 원칙
- [ ] override는 예외 처리에만 사용
- [ ] 반복되는 override는 다음 회차 룰/속성으로 흡수
- [ ] override 적용 사유를 반드시 기록
- [ ] 세션 시작 전 override 확정

### 3-3. 권장 기록 포맷
- 적용일시:
- 적용자:
- 대상(평가자/피평가자):
- 조치(ADD/REMOVE):
- 사유:
- 재발 방지(룰/속성 반영 계획):

---

## 4. rule set 운영 확정 가이드

### 4-1. 네이밍 규칙 (권장)
`<병원코드>_<평가주기>_<정책버전>_<상태>`
- 예: `HOSP_A_2026H1_v1.0_APPROVED`
- 예: `HOSP_A_2026H1_v1.1_DRAFT`

### 4-2. 버전 관리 규칙
- DRAFT: 검토/시뮬레이션 중
- REVIEWED: 실무 검토 완료
- APPROVED: 운영 승인 완료(기준 버전)
- DEPRECATED: 사용 중단

### 4-3. 변경 통제
- [ ] 룰 변경 요청서 발행
- [ ] 변경 전/후 비교 결과 첨부
- [ ] 승인자 서명/결재
- [ ] 변경 이력 로그(적용일, 작성자, 사유, 영향도) 기록

### 4-4. 기본 세트 운영 정책
- 병원별 `APPROVED` 세트 1개만 기본 세트 지정
- 운영 중 급변경은 기본 세트 직접 수정 대신 `복제 -> 버전업 -> 검증 -> 기본 전환`

---

## 5. 최종 운영 승인 체크리스트

### 5-1. 기능/데이터 승인
- [ ] 직원/속성 업로드 오류율 허용 기준 충족
- [ ] RULE_BASED 생성 결과 표본 검증 통과
- [ ] LEGACY 비교 차이 원인 분석 완료
- [ ] 필수 override 반영 완료

### 5-2. 운영/보안 승인
- [ ] 실행 이력(실행자/시각/결과/실패사유) 확인 가능
- [ ] 승인자/실무자 역할 분리 확인
- [ ] 비정상 케이스 대응 절차 확인 (재업로드/재생성/롤백)

### 5-3. Go-Live 승인 조건
- [ ] 병원 운영책임자 승인
- [ ] HR/평가 실무 승인
- [ ] IT 운영 승인
- [ ] 최종 승인 일시 및 회차 기록

### 5-4. Go-Live 직후 점검
- [ ] 첫 운영 세션 생성/시작 로그 확인
- [ ] 관계 건수 이상치 모니터링
- [ ] 사용자 문의/이슈 대응 채널 오픈 확인

---

## 6. 문항군 실데이터 검증 체크리스트

### 6-1. 병원 문항군 검증 체크리스트 (HOSPITAL_DEFAULT)
- [ ] 리허설 세션이 `RULE_BASED`인지 확인
- [ ] 세션 템플릿에 `AA`/`AB` 문항군이 모두 존재하는지 확인
- [ ] 표본 관계 1건 이상에서 `UPWARD` 관계의 assignment `resolved_question_group_code = AA` 확인
- [ ] 표본 관계 1건 이상에서 `DOWNWARD` 관계의 assignment `resolved_question_group_code = AB` 확인
- [ ] 상호평가 표본(부서장 -> 부서장)에서 `resolved_question_group_code = AA` 확인
- [ ] 상호평가 표본(부서원 -> 부서원)에서 `resolved_question_group_code = AB` 확인
- [ ] 관계 표본(부서장 -> 부서원)에서 `resolved_question_group_code = AB` 확인
- [ ] 실제 평가 화면에서 `AA` assignment는 `AA` 문항만 노출되는지 확인
- [ ] 실제 평가 화면에서 `AB` assignment는 `AB` 문항만 노출되는지 확인

### 6-2. 계열사 문항군 검증 체크리스트
- [ ] 기관 `organization_profile` 값이 기대값과 일치하는지 확인
- [ ] `AFFILIATE_HOSPITAL` 기관
  - [ ] 세션 템플릿에 `AC`/`AD` 문항군 존재 확인
  - [ ] 리더 평가 표본 assignment에서 `resolved_question_group_code = AC` 확인
  - [ ] 일반 평가 표본 assignment에서 `resolved_question_group_code = AD` 확인
- [ ] `AFFILIATE_GENERAL` 기관
  - [ ] 세션 템플릿에 `AC`/`AE` 문항군 존재 확인
  - [ ] 리더 평가 표본 assignment에서 `resolved_question_group_code = AC` 확인
  - [ ] 일반 평가 표본 assignment에서 `resolved_question_group_code = AE` 확인
- [ ] 실제 평가 화면에서 assignment별 문항군 코드와 동일한 문항만 보이는지 확인

### 6-3. 계열사 리더 평가 기준 확인 항목
- [ ] 현재 구현 기준이 `evaluatee` 리더 여부인지 운영 담당자에게 명시
- [ ] 운영 정책이 다음 중 무엇인지 확인
  1. `evaluatee` 기준 리더 평가 (현재 구현)
  2. `evaluator` 기준 리더 평가
  3. 별도 조건(예: 양측 리더/관계유형 결합)
- [ ] 운영 정책이 현재 구현과 불일치하면 Go-Live 전 정책 확정 및 코드 보정 일정 등록
- [ ] UAT 결과 문서에 “리더 평가 기준 확정값”을 명시하고 승인 기록 남김

### 6-4. 실제 데이터 리허설 절차 (문항군 연결 검증 중심)
1. 직원명부 업로드
   - 필수 컬럼/속성 반영 및 오류 행 정리
2. 부서 업로드
   - 부서코드/부서명/루트 구조 확인
3. 문제은행 업로드
   - 기관 profile 허용 문항군 코드만 업로드되는지 확인
4. `RULE_BASED` 세션 생성
   - 템플릿/기간/definition set 설정
5. 관계 자동 생성 실행
   - 생성/제외/중복 제거/override 요약 확인
6. assignment 생성(세션 시작)
   - assignment 스냅샷 생성 완료 확인
7. `resolved_question_group_code` 검증
   - DB 또는 목록 화면에서 표본 assignment 코드 확인
8. 실제 문항 표시 검증
   - assignment 진입 시 해당 코드 문항만 노출되는지 확인
9. LEGACY 비교(선택)
   - LEGACY 세션은 `resolved_question_group_code` 미적용인지 확인

---

## 7. 운영 FAQ (start/auto-generate 정책)

- Q. 왜 auto-generate를 다시 눌러야 하나?
  - A. 룰/데이터/override 변경은 start에서 자동 재계산되지 않고 auto-generate 실행 시점에만 generated snapshot에 반영된다.

- Q. 왜 start를 눌러도 관계가 자동으로 다시 계산되지 않나?
  - A. start는 확정 단계다. 이미 생성된 snapshot으로 최종 관계/assignment를 만든다.

- Q. 왜 start 후 문항군이 바뀌지 않나?
  - A. start 시 assignment의 `resolved_question_group_code`가 스냅샷으로 고정되기 때문이다.

- Q. override를 바꿨는데 왜 결과가 그대로인가?
  - A. override 변경 후 auto-generate를 다시 실행하지 않으면 최신 변경이 최종 확정본에 반영되지 않는다.

- Q. LEGACY 세션과 RULE_BASED 세션의 차이는 무엇인가?
  - A. LEGACY는 기존 관계 생성 로직을 사용하고, RULE_BASED는 정의세트/룰/매처/override 기반 생성 결과를 사용한다.
