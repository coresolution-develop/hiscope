# Assignment Linking Policy (Draft)

기준 저장소: `hiscope`  
기준일: 2026-03-19  
목적: `evaluation_submissions`를 신규 `evaluation_assignments`에 연결하기 위한 확정 정책과 보강키 검토 항목 정리

## 1. 배경

- `evaluation_submissions`에는 신규 `evaluation_sessions.id` 또는 `evaluation_assignments.id` 직접 참조키가 없다.
- 따라서 제출 원천 행을 assignment로 연결하려면 복합키 정책과 충돌 검증이 필요하다.

## 2. 최소 복합키 후보

최소 후보(요청 기준):
- `eval_year + evaluator_id + target_id + data_ev + data_type`

현재 변환 SQL 연결 포인트:
- `scripts/migration/transform_legacy_submissions.sql`에서 `legacy_assignment_key`를 위 5개 조합으로 생성

의미 구분 포인트:
- `data_ev`는 “어떤 평가 관계인가”를 나타내는 관계 코드이므로, 동일 평가자/대상이라도 `data_ev`가 다르면 다른 assignment 후보로 취급해야 한다.
- `eval_type_code`는 평가쌍 의미 코드로, `admin_default_targets`/`admin_custom_targets` 해석 및 사후 검증에 필요하다.
  단, 현재 `evaluation_submissions` 복합키 후보에는 `eval_type_code` 컬럼이 없으므로 linking key 자체보다는 정합성 cross-check에 사용한다.

확정 코드표:
- `data_ev`
  - `A` = 진료팀장 -> 진료부
  - `B` = 진료부 -> 경혁팀
  - `C` = 경혁팀 -> 진료부
  - `D` = 경혁팀 -> 경혁팀
  - `E` = 부서장 -> 부서원
  - `F` = 부서원 -> 부서장
  - `G` = 부서원 -> 부서원
- `eval_type_code`
  - `GH_TO_GH` = 경혁팀 -> 경혁팀
  - `GH_TO_MEDICAL` = 경혁팀 -> 진료부
  - `SUB_HEAD_TO_MEMBER` = 부서장 -> 부서원
  - `SUB_MEMBER_TO_HEAD` = 부서원 -> 부서장
  - `SUB_MEMBER_TO_MEMBER` = 부서원 -> 부서원
  - `GH` = 평가대상: 경혁팀
  - `MEDICAL` = 평가대상: 진료부

## 3. 충돌 가능성

아래 경우 동일 복합키로 다건이 생길 수 있다.

1. 같은 연도에 동일 평가자/대상/관계/유형으로 재제출(`version` 증가)
2. `is_active` 이력이 비정상(동시 활성 다건)
3. `del_yn` 이력(`Y`/`N`)이 섞여 존재
4. 세션 분리 운영이 있었는데 session 식별 키가 제출 테이블에 없는 경우

## 4. 추가 키 필요성

- 최소 복합키만으로는 “세션 분리 운영”을 식별하지 못할 수 있다.
- 아래 키가 있으면 충돌 해소력이 올라간다.
  - session 관련 키(존재 시)
  - assignment 직접 식별 키(존재 시)
  - 운영 배치/폼 식별 키(`form_id` 등)

현재 상태:
- 제출 원천 테이블에서 session 직접 키는 미확정
- 따라서 파일럿에서는 최소 복합키 + 최신 활성 선택 정책(`del_yn='N'`, `is_active=1` 우선, `version` 최신)을 사용

## 5. 검증 SQL

아래 SQL은 파일럿 기관(`효사랑가족요양병원`) 기준으로 최소 복합키의
- 유일 건수
- 중복 그룹 건수
- 충돌 그룹(활성 다건) 건수
를 점검한다.

```sql
-- 상세 실행본은 scripts/migration/validate_assignment_linking_candidates.sql 참조
WITH pilot_users AS (
    SELECT DISTINCT eval_year, id AS user_id
    FROM users_2025
    WHERE btrim(COALESCE(c_name, '')) = '효사랑가족요양병원'
       OR btrim(COALESCE(c_name2, '')) = '효사랑가족요양병원'
), candidate AS (
    SELECT s.*
    FROM evaluation_submissions s
    JOIN pilot_users pe ON pe.eval_year = s.eval_year AND pe.user_id = s.evaluator_id
    JOIN pilot_users pt ON pt.eval_year = s.eval_year AND pt.user_id = s.target_id
), grouped AS (
    SELECT
        eval_year, evaluator_id, target_id, data_ev, data_type,
        COUNT(*) AS row_count,
        COUNT(*) FILTER (WHERE UPPER(COALESCE(del_yn, 'N')) = 'N') AS non_deleted_count,
        COUNT(*) FILTER (WHERE COALESCE(is_active, 0) = 1 AND UPPER(COALESCE(del_yn, 'N')) = 'N') AS active_non_deleted_count
    FROM candidate
    GROUP BY eval_year, evaluator_id, target_id, data_ev, data_type
)
SELECT
    COUNT(*) FILTER (WHERE row_count = 1) AS unique_groups,
    COUNT(*) FILTER (WHERE row_count > 1) AS duplicate_groups,
    COUNT(*) FILTER (WHERE active_non_deleted_count > 1) AS active_conflict_groups
FROM grouped;
```

## 6. 운영 결정 필요 항목

1. 세션 분리 운영 시 assignment 연결에 추가할 표준 보강키 채택 여부
