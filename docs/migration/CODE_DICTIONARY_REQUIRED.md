# 코드 사전 확정 반영 및 잔여 결정 항목

기준 저장소: `hiscope`  
기준일: 2026-03-19  
목적: 실행 전 코드/정책 사전의 확정값과 잔여 미확정 항목을 분리 관리

## 정리 표

| 항목 | 확정 필요 | 현재 아는 것 | 미확정 | 결정 시 반영 대상 SQL |
|---|---|---|---|---|
| `data_ev` (`A~G`) | 확정 완료 | `data_ev`는 **평가 관계 코드**. 확정 코드표: `A=진료팀장->진료부`, `B=진료부->경혁팀`, `C=경혁팀->진료부`, `D=경혁팀->경혁팀`, `E=부서장->부서원`, `F=부서원->부서장`, `G=부서원->부서원` | 없음 | `scripts/migration/transform_legacy_submissions.sql`, `scripts/migration/validate_assignment_linking_candidates.sql`, `docs/migration/LEGACY_REAL_MAPPING.md` |
| `eval_type_code` (전체) | 확정 완료 | `eval_type_code`는 **평가자->평가대상자 관계 의미 코드**. 확정 코드표: `GH_TO_GH=경혁팀->경혁팀`, `GH_TO_MEDICAL=경혁팀->진료부`, `SUB_HEAD_TO_MEMBER=부서장->부서원`, `SUB_MEMBER_TO_HEAD=부서원->부서장`, `SUB_MEMBER_TO_MEMBER=부서원->부서원`, `GH=평가대상: 경혁팀`, `MEDICAL=평가대상: 진료부` | 없음 | `scripts/migration/validate_assignment_linking_candidates.sql`, `docs/migration/LEGACY_REAL_MAPPING.md`, `docs/migration/LEGACY_GAP_ANALYSIS.md` |
| `data_type` (`AA`, `AB`) | 확정 완료 | 확정: `AA=10문항 세트`, `AB=20문항 세트`; 사용 대상 확정(`AA: 부서장/진료부/경혁팀`, `AB: 부서원`) | 없음 | `scripts/migration/transform_legacy_submissions.sql`, `docs/migration/LEGACY_REAL_MAPPING.md` |
| Radio label -> raw score | 확정 완료 | 확정: `매우우수=5`, `우수=4`, `보통=3`, `미흡=2`, `매우미흡=1`; `AA/AB` 환산은 별도 단계 처리 | 없음 | `scripts/migration/create_radio_score_mapping_template.sql`, `scripts/migration/transform_legacy_submissions.sql` |
| `user_roles_2025.role` 의미 | role 의미 확정 완료 | 확정: `team_head=경혁팀장`, `team_member=경혁팀원`, `sub_head=부서장`, `sub_member=부서원`, `one_person_sub=1인부서`, `medical_leader`(의료 리더 역할 원천값) | role -> 신규 `attribute_key` 최종 매핑/우선순위 | (후속) role attribute 적재 SQL, `docs/migration/LEGACY_REAL_MAPPING.md`, `docs/migration/LEGACY_GAP_ANALYSIS.md` |
| assignment linking 보강키 | 최소키 확정, 보강키 정책 미확정 | 최소 복합키 확정: `eval_year + evaluator_id + target_id + data_ev + data_type` | session 분리 운영 시 추가 보강키 채택 여부 | `scripts/migration/validate_assignment_linking_candidates.sql`, `docs/migration/ASSIGNMENT_LINKING_POLICY.md` |

## 잔여 운영 결정 체크리스트

1. role -> attribute_key 최종 매핑표 및 우선순위 확정
2. assignment linking 추가 보강키(session/폼/배치 식별자) 채택 여부 확정

## 참고

- `data_ev`/`eval_type_code`는 더 이상 예시나 참고값이 아니라 확정 코드표로 반영한다.
- `transform_legacy_submissions.sql`은 라디오 라벨을 확정 라벨표로 raw score 적재하고, AA/AB 환산은 파생값으로만 다룬다.
