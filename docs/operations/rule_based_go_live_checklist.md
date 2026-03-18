# RULE_BASED 운영 적용 체크리스트

## 0. RULE_BASED 시작 정책(필독)
- 재생성 책임은 `start`가 아니라 `auto-generate`에 있다.
- `auto-generate` 성공 이력이 있으면 `start`는 generated snapshot을 재사용해 최종 관계/assignment를 확정한다.
- 데이터/룰/override를 변경했다면 `start` 전에 반드시 `auto-generate`를 다시 실행해야 한다.
- `start` 이후 assignment의 `resolved_question_group_code`는 스냅샷으로 고정된다.

## 1. 사전 준비
- [ ] 부서 마스터 최신화 (부서코드/상위부서 구조 확인)
- [ ] 직원 업로드 템플릿 최신 버전 다운로드 (`/admin/uploads/template/employees`)
- [ ] 병원형 속성 컬럼 정의 확인
  - 기본 제공: `경혁팀`, `경혁팀장`, `부서장`, `1인부서`, `진료팀장`, `평가제외`, `의료리더`, `기관장`, `소속장`
  - 자유 속성: `attr:attribute_key` 또는 `속성:attribute_key`
  - 독립 속성 원칙: `경혁팀/경혁팀장/부서장`은 상호 추론하지 않음
  - 보조 검증 컬럼: `평가대상여부`, `이전부서명`

## 2. 직원 명부 업로드 리허설
- [ ] 샘플(10~30명)로 1차 업로드
- [ ] 업로드 결과 확인
  - 성공/실패 건수
  - 컬럼별 오류 메시지
- [ ] 직원 속성 관리 화면에서 값 반영 확인 (`/admin/settings/employee-attributes`)
- [ ] 실패 행은 오류 CSV로 수정 후 재업로드

## 3. 관계 정의 확정
- [ ] 관계 정의 세트 생성 또는 복제
- [ ] 룰 우선순위/활성 여부 확정
- [ ] matcher(subject/matcher/operator/value) 검증
- [ ] 기본 세트 지정 필요 여부 확인

## 4. 세션 리허설 실행
- [ ] 테스트 세션 생성 (RULE_BASED + 정의세트 선택)
- [ ] 관계 자동 생성 실행
- [ ] 실행 이력 검토
  - 생성/제외/자기평가 제거/중복 제거/override 반영/최종 건수
  - 룰별 생성 건수
  - 실행자/실행시각/실패 사유
- [ ] `start` 직전 최신 `auto-generate` 실행 여부 확인
- [ ] override 변경 후 `auto-generate` 재실행 여부 확인
- [ ] assignment 생성 전 최종 관계 표본 검증 완료 여부 확인
- [ ] `resolved_question_group_code` 표본 확인 완료 여부 확인
- [ ] LEGACY 비교 실행 후 차이 건수 확인

## 5. 운영 전환 승인
- [ ] 자동 생성 결과 샘플 검토 (부서장/평가제외 등 핵심 케이스)
- [ ] 필요한 override(ADD/REMOVE) 적용
- [ ] 재실행 후 최종 관계 확정
- [ ] 평가 세션 시작 승인

## 6. 전환 후 모니터링
- [ ] 첫 실행 이력 정상 여부 확인
- [ ] 예상치 대비 관계 건수 이상 여부 점검
- [ ] 운영 이슈 발생 시
  - 세션 종료 전: 룰/속성 수정 후 재생성
  - 세션 시작 후: 다음 회차에 반영

## 7. 운영 FAQ
- Q. 왜 auto-generate를 다시 눌러야 하나?
  - A. 룰/데이터/override 변경은 start에서 자동 재계산되지 않고, auto-generate 실행 시점에만 generated snapshot에 반영된다.
- Q. 왜 start를 눌러도 관계가 자동으로 다시 계산되지 않나?
  - A. start는 재생성 단계가 아니라 확정 단계다. 기존 snapshot으로 최종 관계와 assignment를 만든다.
- Q. 왜 start 후 문항군이 바뀌지 않나?
  - A. start 시 assignment의 `resolved_question_group_code`가 스냅샷으로 고정되기 때문이다.
- Q. override를 바꿨는데 왜 결과가 그대로인가?
  - A. override 변경 후 auto-generate를 다시 실행하지 않으면 최신 변경이 최종 확정본에 반영되지 않는다.
- Q. LEGACY 세션과 RULE_BASED 세션의 차이는 무엇인가?
  - A. LEGACY는 기존 관계 로직을 사용하고, RULE_BASED는 정의세트/룰/매처/override 기반 생성 결과를 사용한다.
