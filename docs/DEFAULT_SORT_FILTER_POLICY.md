# Default Sort & Filter Policy

기준일: 2026-03-12  
목적: 목록형 화면의 검색/필터/정렬/페이징 동작을 운영자 기준으로 일관화

## 1. 공통 원칙

- 검색 파라미터는 공백 trim 후 적용한다.
- 유효하지 않은 필터/정렬 값은 안전한 기본값으로 치환한다.
- 페이지 인덱스는 0 기반(`page=0`)으로 통일한다.
- `size`는 화면별 상한을 두고 서버에서 강제 보정한다.
- 정렬 방향은 `asc|desc`만 허용, 그 외는 기본값 사용.

## 2. 화면별 기본값

### 슈퍼 관리자

- 기관 목록(`/super-admin/organizations`)
  - 기본 정렬: `createdAt desc`
  - 기본 size: `10`
  - 필터: `keyword`, `status`

### 기관 관리자

- 부서 목록(`/admin/departments`)
  - 기본 정렬: `name asc`
  - 기본 size: `20`
  - 필터: `keyword`, `active`

- 사용자 목록(`/admin/employees`)
  - 기본 정렬: `name asc`
  - 기본 size: 화면 기본값 사용
  - 필터: `keyword`, `status`, `departmentId`

- 평가 템플릿 목록(`/admin/evaluation/templates`)
  - 기본 정렬: `createdAt desc`
  - 기본 size: `12`
  - 필터: `keyword`, `active`

- 평가 세션 목록(`/admin/evaluation/sessions`)
  - 기본 정렬: `createdAt desc`
  - 기본 size: `20`
  - 필터: `keyword`, `status`, `allowResubmit`

- 세션 상세 배정표(`/admin/evaluation/sessions/{id}`)
  - 기본 정렬: `submittedAt desc`
  - 기본 size: `20`
  - 필터: `assignmentKeyword`, `assignmentStatus`

- 평가 관계 목록(`/admin/evaluation/sessions/{id}/relationships`)
  - 기본 정렬: `relationType asc`
  - 기본 size: `50`
  - 필터: `keyword`, `relationType`, `source`, `active`

- 결과 조회(`/admin/evaluation/results`)
  - 기본 정렬: `evaluateeName asc`
  - 기본 size: `20`
  - 필터: `sessionId`, `keyword`, `departmentId`

- 업로드 이력(`/admin/uploads/history`)
  - 기본 정렬: `createdAt desc`
  - 기본 size: 운영 설정 기반(뷰 제한 내)
  - 필터: `uploadType`, `status`, `dateFrom`, `dateTo`, `keyword`

- 감사 로그(`/admin/audit`, `/super-admin/audit`)
  - 기본 정렬: `occurredAt desc`
  - 기본 size: `50` (최소 20, 최대 200)
  - 필터: `action`, `outcome`, `actorLoginId`, `targetType`, `keyword`, `requestId`, `fromDate`, `toDate`

## 3. CSV 내보내기 정렬 규칙

- 감사 로그 CSV는 화면 정렬(`sortBy/sortDir`)을 동일하게 적용한다.
- 결과 CSV(대상자/부서)는 화면 필터/정렬 파라미터를 동일하게 전달한다.

## 4. 운영 시 확인 포인트

- 화면 페이지 이동 시 필터/정렬 파라미터가 유지되는지 확인
- CSV 다운로드 시 화면과 동일한 정렬 결과인지 확인
- 비정상 파라미터 입력 시 500 없이 기본값으로 안전 처리되는지 확인
