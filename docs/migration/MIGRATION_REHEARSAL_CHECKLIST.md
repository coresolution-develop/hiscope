# 레거시 이관 리허설 체크리스트

기준: 현재 저장소 코드 + `scripts/migration/validate_post_migration.sql`

## 1. 리허설 전 (P0)

- [ ] 대상 기관 목록 확정 (기관코드/기관유형/프로파일)
- [ ] 레거시 계정 해시 알고리즘 표본 확인(BCrypt 여부)
- [ ] `organization_settings` 이관 대상 key 확정
- [ ] 이관 배치에서 기관 INSERT 직후 bootstrap 실행 경로 확정
- [ ] 아래 쿼리 사전 실행
  - [validate_post_migration.sql](/Users/leesumin/hiscope/scripts/migration/validate_post_migration.sql) 의 `V-01`, `V-02`, `V-06`, `V-07`

## 2. 리허설 실행

- [ ] 기관/부서/직원/계정/속성/세션/관계/배정/응답 순서로 적재
- [ ] RULE_BASED 대상 세션은 `relationship_definition_set_id` 연결 확인
- [ ] `resolved_question_group_code` 정책 확인
  - RULE_BASED 세션 배정: non-null
  - LEGACY 세션 배정: null 허용
- [ ] 로그인 검증
  - orgCode+loginId 성공
  - orgCode 미입력 + 중복 loginId 실패
- [ ] 자기평가 쌍 0건 확인 (`V-08`)

## 3. 리허설 후 Go/No-go

- [ ] bootstrap 누락 기관 0건 (`V-06`)
- [ ] AFFILIATE 프로파일 오적용 0건 (`V-01`)
- [ ] super-admin null-org 중복 login 0건 (`V-02`)
- [ ] self-evaluation 0건 (`V-08`)
- [ ] RULE_BASED의 `resolved_question_group_code` null 0건 (`V-04`)
- [ ] 관계-배정 orphan 0건 (`V-09`)

## 4. 운영 결정 필요 항목 (추가 확인 필요)

- [ ] AFFILIATE_HOSPITAL을 병원 룰(7개)로 볼지, 계열사 룰(3개)로 볼지
- [ ] INACTIVE/LEAVE 직원의 과거 세션 포함 범위
- [ ] 초기 비밀번호 정책(현행 업로드는 `password123`)
- [ ] KPI 데이터 이관 방식(신규 도메인 부재)
