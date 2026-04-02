# ORG Reset Runbook (DEV/STAGING 전용)

## 1. 목적
- 일반 운영 화면의 "직원 전체 삭제" 대신, 개발/스테이징에서만 org 단위 데이터 초기화를 안전하게 수행한다.
- 물리 삭제 없이 기존 org를 archive 처리하고, 기존 org code를 재사용 가능 상태로 만든다.

## 2. 범위/비범위
- 범위:
  - 대상 org의 `organizations.code/status` archive 전환
  - 대상 org 소속 `accounts.status = INACTIVE`
  - 대상 org 소속 `employees.status = INACTIVE`
- 비범위:
  - 물리 삭제 (`DELETE`) 금지
  - 일반 운영 화면 기능 추가 금지
  - SUPER_ADMIN API/엔드포인트 추가 금지

## 3. 관련 파일
- Precheck: `scripts/ops/reset_org_precheck.sql`
- Archive 실행: `scripts/ops/reset_org_archive.sql`
- 문서(본 문서): `docs/ops/ORG_RESET_RUNBOOK.md`

## 4. 실행 전 필수 조건
1. 대상 환경이 `dev` 또는 `staging`임을 확인한다.
2. 대상 DB 이름에 `prod`/`production` 문자열이 없는지 확인한다.
3. 대상 org code를 allowlist에 등록한다.
4. 사전 백업을 완료한다.

## 5. 백업 권장 절차
1. DB 스냅샷 또는 덤프를 선행한다.
2. 최소 백업 범위:
   - `organizations`
   - `accounts`, `employees`, `user_accounts`
   - `evaluation_*`, `session_*`
   - `relationship_*`, `employee_attribute_*`, `organization_settings`
3. 백업 파일/스냅샷 식별자를 작업 티켓에 기록한다.

## 6. 실행 절차

### Step A) Precheck 실행
1. `scripts/ops/reset_org_precheck.sql`의 Section 0 파라미터를 수정한다.
2. `target_env`는 `dev`/`staging`만 사용한다.
3. `target_org_code`를 지정한다.
4. allowlist에 동일 org code를 등록한다.
5. 스크립트를 실행하고 아래 항목을 확인한다.

필수 확인:
- `P-01`: `env_allowed = true`, `db_name_not_prod = true`, `allowlisted = true`, `target_org_count = 1`
- `P-08`: `can_execute_reset_precheck = true`
- `P-04`: 열려 있는 세션(`PENDING`, `IN_PROGRESS`) 존재 여부
- `P-03`: 테이블별 데이터 볼륨(영향 범위)

### Step B) Archive Dry-run
1. `scripts/ops/reset_org_archive.sql`에서 `dry_run = TRUE`로 실행한다.
2. 출력에서 다음을 확인한다.
   - `archive_org_code` 생성값
   - `*_before` 지표
   - 각 update step의 `affected_rows = 0` (dry-run)

### Step C) 실제 적용
1. 동일 스크립트에서 `dry_run = FALSE`로 변경한다.
2. allowlist/target_org_code 재확인 후 실행한다.
3. Post-check를 확인한다.
   - `current_org_code = <OLD_CODE>__ARCHIVE_<timestamp>`
   - `current_org_status = INACTIVE`
   - `accounts_active_after = 0`
   - `employees_active_after = 0`

### Step D) 신규 org 재생성
1. 기존 생성 경로(서비스/UI)를 사용해 원래 org code로 새 org를 생성한다.
2. 새 org에서 기본 bootstrap(설정/룰셋 등) 및 로그인 흐름을 점검한다.

## 7. 보호장치
- SQL 레벨 가드:
  - `target_env`가 `dev/staging`가 아니면 실패
  - DB명이 `prod|production` 패턴이면 실패
  - allowlist 미등록 org면 실패
  - 대상 org가 정확히 1건이 아니면 실패
  - archive code 충돌 시 실패
- 정책 가드:
  - 운영(production) 실행 금지
  - 물리 삭제 금지

## 8. 검증 체크리스트 (실행 후)
1. 기존 org의 code/status가 archive 상태인지 확인
2. 기존 org 소속 admin/employee가 모두 INACTIVE인지 확인
3. 기존 org code로 신규 org 생성 가능한지 확인
4. 신규 org 로그인/기본 설정/기본 화면 smoke test

## 9. 복구/롤백 주의사항
- 스크립트는 물리 삭제를 수행하지 않으므로 데이터는 보존된다.
- 다만 code/status 변경이 커밋된 뒤에는 단순 undo가 어려울 수 있으므로 백업 복구 계획이 필요하다.
- 복구 우선순위:
  1. 스냅샷/덤프 복원
  2. 필요 시 수동으로 org code/status를 원복(신규 org code 충돌 여부 선확인)

## 10. 운영 주의
- 이 절차는 개발 편의를 위한 org reset 용도다.
- 운영 기능로 일반화하지 않는다.
- 이관 데이터가 포함된 org는 더 보수적으로 적용하고, 반드시 명시적 승인/allowlist 하에 실행한다.
