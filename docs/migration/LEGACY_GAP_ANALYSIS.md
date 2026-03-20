# 레거시 실측 Gap 분석 (파일럿 이관 기준)

기준 저장소: `hiscope`  
기준일: 2026-03-19  
입력 근거: 사용자 제공 실측 DDL/샘플 + 현재 저장소 스키마

## 1. 핵심 결론

- 파일럿 기관 `효사랑가족요양병원` 기준으로 조직/직원/관계/제출 원천 추출 경로는 확정할 수 있다.
- `evaluation_submissions.answers_json` 전개는 `evaluation(idx,d2,d3)` 기반 문항키 파싱 규칙을 적용해야 하며, 키를 신규 `question_id`로 즉시 직결하면 안 된다.
- `data_ev`/`eval_type_code`는 더 이상 예시가 아니라 확정 코드표로 사용한다.
- KPI/공지/AI 요약 캐시는 1차 핵심 트랜잭션 이관이 아니라 archive/separate track가 맞다.

## 2. Gap 목록 (확정/가정/미확정)

| 항목 | 상태 | 영향도 | 현재 확정 근거 | 추가 실측 필요 |
|---|---|---|---|---|
| 파일럿 기관 식별값 | 확정 | P0 | 파일럿 기관은 `효사랑가족요양병원`, 식별 기준은 `hospital_name`/`c_name`/`c_name2` | 없음 |
| 문항 마스터 구조 | 확정 | P0 | `evaluation.idx`(문항번호), `d2`(AA/AB), `d3`(구분) | 연도별 문항 변경 이력(선택) |
| 문항키 파싱 규칙 | 확정 | P0 | `나39`, `목41`, `t43`, `t64`는 prefix + idx 구조 | prefix 예외 케이스 존재 여부(선택) |
| `evaluation_submissions` 활성/버전 정책 | 확정(정책 반영) | P0 | `del_yn`, `is_active`, `version` 컬럼 기반 최신 활성 우선 정책 반영 | 없음 |
| `evaluation_submissions` -> `sessions/assignments` 최소 복원키 | 확정 | P0 | 최소 복합키: `eval_year+evaluator_id+target_id+data_ev+data_type` | 없음 |
| `evaluation_submissions` -> `sessions/assignments` 추가 보강키 | 미확정 | P0 | 최소 복합키는 확정되어 운영 가능 | session 분리 운영 시 추가 표준키 채택 여부 |
| `data_ev=A~G` 코드표 | 확정 | P0 | `A=진료팀장->진료부`, `B=진료부->경혁팀`, `C=경혁팀->진료부`, `D=경혁팀->경혁팀`, `E=부서장->부서원`, `F=부서원->부서장`, `G=부서원->부서원` | 없음 |
| `eval_type_code` 코드표 | 확정 | P0 | `GH_TO_GH`, `GH_TO_MEDICAL`, `SUB_HEAD_TO_MEMBER`, `SUB_MEMBER_TO_HEAD`, `SUB_MEMBER_TO_MEMBER`, `GH`, `MEDICAL` | 없음 |
| `user_roles_2025.role` 의미 | 확정 | P0 | `team_head/team_member/sub_head/sub_member/one_person_sub/medical_leader` 의미 확정 | 없음 |
| `user_roles_2025.role` -> 신규 속성키 완전 매핑 | 미확정 | P0 | role 의미는 확정 | 각 role의 최종 `attribute_key`/우선순위 |
| `users_2025.pwd` 직접 이관 | 가정 | P0 | bcrypt 해시 문자열 실측 | 깨진 해시/비표준 prefix 분포(선택) |
| `admin_default_targets`/`admin_custom_targets` 처리 | 확정 | P0 | default=기본 자동 매핑 원천, custom=수동 override 원천(`reason/is_active` 보존) | 없음 |
| `evaluation_comment_summary` 핵심 이관 필요성 | 확정(핵심 제외) | P1 | 캐시/파생 테이블, `kind/model/status/error_msg` 보존 필요 | 없음 |
| KPI 3개 테이블 핵심 도메인 편입 | 확정(제외) | P1 | 신규 핵심 도메인에 KPI 전용 엔티티/DDL 부재 | 없음 |
| `notice_v2` 핵심 도메인 편입 | 확정(제외) | P1 | 신규 notice 엔티티/DDL 부재 | 없음 |

## 3. P0 잔여 해소 항목

### 3.1 역할 속성키 최종 확정

- 확보 대상
  - `team_head`, `team_member`, `sub_head`, `sub_member`, `one_person_sub`, `medical_leader`의 최종 `attribute_key` 매핑표
  - 다중 role 보유 시 우선순위 규칙
- 미해소 리스크
  - RULE_BASED 매처 결과 변동
  - 관계 자동 생성 시 role 해석 불일치

### 3.2 assignment linking 보강키 채택 여부

- 확보 대상
  - session 분리 운영 시 추가로 사용할 표준 보강키(session/폼/배치 식별자)
- 미해소 리스크
  - 최소 복합키 충돌 그룹에서 운영 해석 차이 발생

## 4. 분리 트랙(확정)

### 4.1 AI 요약 캐시 (`evaluation_comment_summary`)

- 1차: archive-only
- 보존 필드: `kind`, `model`, `status`, `error_msg`, `summary`
- 주의: `TOTAL`, `SCORE_KY`는 KPI 결합 결과 가능성이 있어 핵심 다면평가 원천으로 취급하지 않음

### 4.2 KPI 3개 테이블

- `kpi_eval_clinic`(진료부/의료진), `kpi_personal_2025`(일반직원), `kpi_info_general_2025`(경혁팀)
- 1차: archive-only
- 2차: KPI 도메인 설계 후 별도 이관

### 4.3 `notice_v2`

- 1차: archive-only
- 2차: notice 도메인/API/UI 설계 후 별도 이관

## 5. 확인용 SQL 체크 포인트

```sql
-- G-01: data_ev 코드 분포(파일럿)
SELECT data_ev, COUNT(*)
FROM evaluation_submissions
GROUP BY data_ev
ORDER BY data_ev;

-- G-02: eval_type_code 분포(default/custom)
SELECT eval_type_code, COUNT(*)
FROM admin_default_targets
GROUP BY eval_type_code
UNION ALL
SELECT eval_type_code, COUNT(*)
FROM admin_custom_targets
GROUP BY eval_type_code;

-- G-03: answers_json 키 분포(radios)
SELECT key AS question_key, COUNT(*) AS cnt
FROM evaluation_submissions es,
     jsonb_each_text(COALESCE(es.answers_json::jsonb -> 'radios', '{}'::jsonb))
GROUP BY key
ORDER BY cnt DESC;

-- G-04: answers_json 키 분포(essays)
SELECT key AS question_key, COUNT(*) AS cnt
FROM evaluation_submissions es,
     jsonb_each_text(COALESCE(es.answers_json::jsonb -> 'essays', '{}'::jsonb))
GROUP BY key
ORDER BY cnt DESC;
```

## 6. 연결 파일

- 실측 매핑: `docs/migration/LEGACY_REAL_MAPPING.md`
- 파일럿 추출: `scripts/migration/extract_pilot_org_from_legacy.sql`
- 제출 변환: `scripts/migration/transform_legacy_submissions.sql`
- assignment 검증: `scripts/migration/validate_assignment_linking_candidates.sql`
- AI 요약 아카이브: `scripts/migration/archive_legacy_ai_summary.sql`
- KPI 아카이브: `scripts/migration/archive_legacy_kpi.sql`
