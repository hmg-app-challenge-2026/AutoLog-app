# AutoLog DB 스키마

> 차 안 로컬 DB(Room)와 중계 서버 DB(PostgreSQL)의 테이블·컬럼 정리.
> **원본은 코드다.** 이 문서와 코드가 다르면 코드를 따르고 이 문서를 고친다.
> - 차: `vehicle-core/src/main/kotlin/kr/autolog/vehicle/data/TripEntities.kt`, `ReferenceEntities.kt`, 자동 생성 스키마 `vehicle-core/schemas/kr.autolog.vehicle.data.AutoLogDb/2.json`
> - 서버: `relay/src/main/resources/db/migration/V1__init.sql`, `V2__reference_indexes.sql`
>
> 기준: Room v2, Flyway V2 (2026-11-09)

---

## 0. 한눈에 보기

```
  운영 웹(관리자)        폰(영수증 촬영)
        │                     │
        ▼                     ▼
 ┌────────────── 서버 PostgreSQL (우체통) ──────────────┐
 │ organizations · vehicles · connect_codes · qr_tokens │
 │ users · places · schedules · recipients   (원본)     │
 │ receipts (30일)  ·  inbox (30일)                     │
 └───────────────────────┬──────────────────────────────┘
                         │ inbox (서버 → 차, 단방향)
                         ▼
 ┌────────────── 차 Room(SQLite) (장부 원본) ───────────┐
 │ users · places · schedules · recipients  (사본+LOCAL)│
 │ receipt_inbox · inbox_seen                           │
 │ trips · stops · vehicle_state · recent_users  (원본) │
 │ expenses · trip_expense_links · reports      (원본)  │
 └──────────────────────────────────────────────────────┘
        운행·비용은 차 밖으로 나가지 않는다 (엑셀·메일은 차가 직접)
```

| 데이터 | 원본 위치 | 다른 쪽 |
|---|---|---|
| 운행·정차·비용·운행기록부 | 차 Room | 서버에 없음 |
| 직원·장소·배차·수신자 | 서버 (관리자 입력) | 차에 사본(`origin=SERVER`) + 차 안 직접 입력(`origin=LOCAL`) |
| 영수증 OCR 결과 | 서버 `receipts` (30일 뒤 삭제) | 차 `receipt_inbox` → 확정하면 `expenses` |

### 공통 규칙 (CONVENTIONS 6.3)

| 값 | 차 Room | 서버 PostgreSQL |
|---|---|---|
| 시각 | `INTEGER` epoch millis (UTC) | `TIMESTAMPTZ` |
| 서버 ↔ 차 주고받기 | ISO 8601 `+09:00` 문자열 (`shared` DTO) | ← |
| 거리 | `REAL` km, 계기판 값 그대로 | — |
| 금액 | `INTEGER` 원 | — |
| enum | 이름 문자열 `TEXT` | 이름 문자열 `TEXT` |
| id | UUID 문자열 `TEXT` (차 안 입력분은 `local-…`) | `UUID` |

---

## 1. 차 안 로컬 DB — Room `AutoLogDb` (앱 A)

- 파일: `data/AutoLogDb.kt`, 현재 **version 3**, `exportSchema = true`
- 마이그레이션: v1 → v2 `AutoMigration` (receipt_inbox에 `inboxId`·`confirmedAt` 추가), v2 → v3 `AutoMigration` (trips에 출발·도착 주소와 출처 4열 추가 + `MoveNaviOriginToAddress`: v2가 출발지 이름에 넣던 내비 주소를 주소 칸으로 옮김, 2026-11-13 에뮬레이터 업그레이드 확인)
- 운행 기록은 원본이라 **destructive migration을 쓰지 않는다.** 컬럼을 바꾸면 버전을 올리고 Migration을 단다.
- 아래 표의 "NULL"은 NULL 허용 여부다(Kotlin 타입이 `?`이면 허용).

### 1.1 trips — 운행 한 건 (장부의 원본)

시작·10초 체크포인트·기어 P·종료 때마다 즉시 커밋한다. 인덱스: `startTime`, `status`.

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| `tripId` | TEXT **PK** | | UUID |
| `vehicleId` | TEXT | | 서버 차량 id. 등록 전이면 `unregistered` |
| `userId` | TEXT | ✓ | 운전자. null = 미확정(F-20) |
| `driverSource` | TEXT | ✓ | `DriverSource`: DRIVER_SELECTED · SCHEDULE · ASSIGNMENT · PREVIOUS_TRIP · SELF_CLAIMED · MANAGER_ASSIGNED |
| `startTime` | INTEGER | | 시작 시각 (UTC millis) |
| `endTime` | INTEGER | ✓ | 종료 시각. 기록 중이면 null |
| `timeEstimated` | INTEGER(0/1) | | 앱 미실행 중 운행(F-10)처럼 시각이 추정이면 1 |
| `startOdometerKm` | REAL | ✓ | 시작 계기판 |
| `endOdometerKm` | REAL | ✓ | 종료 계기판 |
| `lastOdometerKm` | REAL | ✓ | 마지막 체크포인트. 앱이 죽으면 여기서 복구 |
| `lastSeenAt` | INTEGER | ✓ | 마지막 체크포인트 시각 |
| `distanceKm` | REAL | ✓ | `end − start`. 조회 실패면 null (**추정 금지**) |
| `distanceSource` | TEXT | | `DistanceSource`: VEHICLE_ODOMETER · UNAVAILABLE |
| `editedDistanceKm` | REAL | ✓ | 사용자가 고친 거리(운행 결과 카드 수정 모드, 0 초과 1,500 이하). 원본(`distanceKm`)은 덮지 않는다. 목록·엑셀은 이 값을 쓰고 엑셀 비고에 계기판 값을 남긴다 |
| `originLabel` | TEXT | ✓ | 출발지 이름 — 등록 장소 > 건물 이름(카카오). 주소는 `originAddress` |
| `originLat`, `originLng` | REAL | ✓ | 출발 좌표 |
| `originSource` | TEXT | ✓ | `PlaceSource`: NAVI_HELPER · FUSED_LOCATION · NAVI_CURRENT · PREVIOUS_TRIP_END · USER_SELECTED |
| `destinationLabel` | TEXT | ✓ | 도착지 이름 — 사람이 고른 이름 > 등록 장소 > 실제로 도착한(300 m 안) 내비 목적지 > 건물 이름 |
| `destinationLat`, `destinationLng` | REAL | ✓ | 도착 좌표. 운행이 끝난 직후 차의 실제 위치(내비 현재 위치 → 위치 SDK)로 다시 정한다 |
| `destinationSource` | TEXT | ✓ | `PlaceSource` |
| `originAddress`, `destinationAddress` | TEXT | ✓ | v3. `광역 기초 읍면동 도로명 번호`(예: `경기 성남시 분당구 정자동 정자일로 1`). 시작·종료 직후 비동기로 채운다 |
| `originAddressSource`, `destinationAddressSource` | TEXT | ✓ | v3. `AddressSource`: KAKAO_RELAY(서버 `/v1/geo/reverse`) · NAVI_HELPER(서버 실패 때 내비 주소). NAVI_HELPER·NULL은 동기화 뒤 서버로 다시 채운다(최근 30일, 20건씩) |
| `purposeDetail` | TEXT | ✓ | 확정된 세부 목적 `PurposeDetail.name` (CLIENT_VISIT, COMMUTE_IN …) |
| `purposeNote` | TEXT | ✓ | 메모 또는 음성 원문(F-09) |
| `purposeInputMode` | TEXT | ✓ | TAP · VOICE |
| `purposeSource` | TEXT | | `PurposeSource`: UNSET · RULE_SUGGESTED · USER_CONFIRMED. **USER_CONFIRMED만 확정**으로 본다 |
| `suggestedPurpose` | TEXT | ✓ | 앱 B 추천 세부 목적 (확정과 따로 보관) |
| `suggestedConfidence` | REAL | ✓ | 추천 신뢰도 0~1 |
| `suggestedRule` | TEXT | ✓ | 추천한 규칙 `PurposeRuleId` (SCHEDULE, COMMUTE_IN …) |
| `status` | TEXT | | `TripStatus`: RECORDING · PENDING_REVIEW · CONFIRMED · NEEDS_RECOVERY · DISCARDED |
| `reviewFlags` | TEXT | ✓ | 검토 표시(F-23) 코드, 쉼표 구분. 운행기록부 생성 때 다시 계산 |
| `rawSnapshotJson` | TEXT | ✓ | SDK 원본 값(감사용) |
| `revision` | INTEGER | | 고칠 때마다 +1 |

### 1.2 stops — 운행 중 정차

자동 종료 대기 시간 안에 다시 출발한 정차(60초 이상만). 인덱스: `tripId`.

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| `id` | INTEGER **PK** (자동 증가) | | |
| `tripId` | TEXT | | → trips |
| `startTime` | INTEGER | | 정차 시작 |
| `endTime` | INTEGER | ✓ | 다시 출발한 시각 |
| `odometerKm` | REAL | ✓ | 정차 때 계기판 |
| `label` | TEXT | ✓ | 등록 장소 이름(예: 주유소). 증빙 없음 검토(F-23)에 쓴다 |
| `reason` | TEXT | ✓ | `StopReason`: GEAR_P · NO_MOVEMENT · NAVI_ARRIVED · USER_REQUEST |

### 1.3 vehicle_state — 차량 상태 (1행, `id = 0`)

놓친 운행 판정의 기준값. 운행 종료·앱 시작·체크포인트마다 갱신한다.

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| `id` | INTEGER **PK** | | 항상 0 |
| `lastKnownOdometerKm` | REAL | ✓ | 마지막으로 안 계기판 값 |
| `lastKnownAt` | INTEGER | ✓ | 그 시각 |
| `ignoredOdometerKm` | REAL | ✓ | [무시]한 놓친 운행의 계기판 값. 같은 값으로는 다시 묻지 않음 |
| `currentDriverUserId` | TEXT | ✓ | 다음 운행 운전자. null = 나중에 선택 |
| `vin` | TEXT | ✓ | 차대번호 |

### 1.4 users — 직원

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| `userId` | TEXT **PK** | | 서버 UUID 또는 `local-…` |
| `name` | TEXT | | 이름 |
| `dept` | TEXT | ✓ | 부서 |
| `email` | TEXT | ✓ | 이메일 |
| `origin` | TEXT | | `Origin`: SERVER(inbox로 받음, 통째로 교체) · LOCAL(차 안 입력, 보존) |

### 1.5 recent_users — 최근 운전자

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| `userId` | TEXT **PK** | | → users |
| `lastUsedAt` | INTEGER | | 마지막으로 고른 시각. 운전자 선택 화면 정렬(F-15) |

### 1.6 places — 등록 장소

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| `placeId` | TEXT **PK** | | 서버 UUID 또는 `local-…` |
| `label` | TEXT | | 이름 |
| `kind` | TEXT | | `PlaceKind`: OFFICE · BRANCH · CLIENT · GAS_STATION · HOME |
| `lat`, `lng` | REAL | | 좌표 |
| `radiusM` | INTEGER | | 매칭 반경(m), 기본 150 (+ 여유 50 m) |
| `defaultPurpose` | TEXT | ✓ | 이 장소의 기본 세부 목적 |
| `origin` | TEXT | | SERVER · LOCAL (F-17 차 안 거래처 등록은 LOCAL) |

### 1.7 schedules — 배차 (이 차 것만)

서버에서 받은 것만 있다(차 안 입력 없음). inbox 수신 때 통째로 교체. 인덱스: (`vehicleId`, `startsAt`).

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| `scheduleId` | TEXT **PK** | | 서버 UUID |
| `vehicleId` | TEXT | | 차량 |
| `userId` | TEXT | ✓ | 배차 운전자. null = 미정 |
| `startsAt`, `endsAt` | INTEGER | | 배차 시간 (UTC millis) |
| `destPlaceId` | TEXT | ✓ | → places |
| `purposeDetail` | TEXT | ✓ | 배차 목적 |

### 1.8 recipients — 운행기록부 수신자

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| `email` | TEXT **PK** | | 소문자 |
| `name` | TEXT | ✓ | 표시 이름 |
| `origin` | TEXT | | SERVER · LOCAL. 같은 이메일이면 서버 배포분이 남는다 |

### 1.9 expenses — 비용 (주유)

인덱스: `occurredAt`, `receiptSha256`. 차 밖으로 업로드하지 않는다.

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| `expenseId` | TEXT **PK** | | UUID |
| `kind` | TEXT | | 현재 FUEL만 |
| `occurredAt` | INTEGER | | 거래 시각 |
| `amountWon` | INTEGER | | 결제 금액(원) |
| `liters` | REAL | ✓ | 주유량(L) |
| `unitPrice` | INTEGER | ✓ | 리터당 단가(원) |
| `discountWon` | INTEGER | | 할인(원), 없으면 0 |
| `fuelType` | TEXT | ✓ | 유종 |
| `merchant` | TEXT | ✓ | 상호 |
| `cardLast4` | TEXT | ✓ | **카드 끝 4자리 숫자만** |
| `source` | TEXT | | `ExpenseSource`: OCR · VOICE · TAP |
| `receiptId` | TEXT | ✓ | 서버 영수증 id (OCR일 때) |
| `receiptSha256` | TEXT | ✓ | 사진 해시. 중복 영수증 거부·검토 |
| `thumbUrl` | TEXT | ✓ | 썸네일 서명 URL (7일) |
| `ocrConfidenceJson` | TEXT | ✓ | OCR 필드별 확신도 JSON |
| `editedFields` | TEXT | ✓ | 사용자가 고친 필드 이름, 쉼표 구분. 원본 OCR 값은 영수증 payload에 남는다 |
| `createdAt` | INTEGER | | 저장 시각 |

### 1.10 trip_expense_links — 운행 ↔ 비용

PK: (`expenseId`, `tripId`). 인덱스: `tripId`. 운행 없이 저장한 비용은 행이 없다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `expenseId` | TEXT | → expenses |
| `tripId` | TEXT | → trips |

### 1.11 receipt_inbox — 확인 대기 영수증

서버 inbox로 받은 RECEIPT. 사용자가 확정하면 `confirmedAt`을 찍고 화면에서 사라진다. 서버 ack가 성공하면 행을 지운다.

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| `receiptId` | TEXT **PK** | | 서버 영수증 id |
| `payloadJson` | TEXT | | `shared` `ReceiptPayload` JSON 그대로 (OCR 원본 값) |
| `receivedAt` | INTEGER | | 처음 받은 시각 |
| `inboxId` | INTEGER | ✓ | 서버 inbox 편지 id (ack용). v2 추가 |
| `confirmedAt` | INTEGER | ✓ | 확정 시각. null = 확인 전. v2 추가 |

### 1.12 inbox_seen — 이미 반영한 inbox 편지

ack가 실패해 같은 편지가 다시 와도 두 번 반영하지 않기 위한 표.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `inboxId` | INTEGER **PK** | 서버 inbox 편지 id |
| `seenAt` | INTEGER | 반영한 시각 |

### 1.13 reports — 만든 운행기록부

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `reportId` | TEXT **PK** | |
| `yearMonth` | TEXT | "2026-11" |
| `filePath` | TEXT | 앱 내부 저장소의 XLSX 경로 |
| `createdAt` | INTEGER | 만든 시각 |
| `dataHash` | TEXT | 같은 데이터로 다시 만들지 않기 위한 해시 |

---

## 2. 중계 서버 DB — PostgreSQL (앱 B)

- 마이그레이션: Flyway. 서버가 시작할 때 `V1__init.sql` → `V2__reference_indexes.sql` 순서로 적용
- **한 번 main에 들어간 마이그레이션 파일은 고치지 않는다.** 바꿀 것은 `V3__…sql`로 추가 (CONVENTIONS 8장)
- 운행·비용 테이블은 없다. 모든 조회는 `org_id`(조직) 범위 안에서만 한다.

### 2.1 organizations — 조직

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `org_id` | UUID **PK** | |
| `name` | TEXT NOT NULL | 조직 이름 (지금은 단일 조직, 환경변수 `ADMIN_ORG_NAME`) |
| `created_at` | TIMESTAMPTZ | 기본 now() |

### 2.2 admins — 관리자 (JWT 도입 시 사용, 현재 미사용)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `admin_id` | UUID **PK** | |
| `org_id` | UUID → organizations | |
| `email` | TEXT UNIQUE | |
| `password_hash` | TEXT | |

### 2.3 vehicles — 차량

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `vehicle_id` | UUID **PK** | |
| `org_id` | UUID | |
| `plate_no` | TEXT | 번호판 (CSV 배차 업로드는 공백 무시하고 매칭) |
| `model` | TEXT | 차종 |
| `vehicle_code` | TEXT UNIQUE | 8자리 소문자. QR URL `/r/{vehicle_code}`에 쓴다 |
| `device_token_hash` | TEXT | 기기 토큰의 sha256. **원문은 저장하지 않음**. null = 미등록·회수 |
| `registered_at` | TIMESTAMPTZ | 기기 등록 시각 |
| `last_inbox_at` | TIMESTAMPTZ | 차가 마지막으로 inbox를 가져간 시각 (운영 웹 연결 상태) |

### 2.4 connect_codes — 기기 연결 코드 (F-40, F-50)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `code` | CHAR(6) **PK** | 대문자·숫자 6자리 (0·1·I·O 제외) |
| `vehicle_id` | UUID | |
| `expires_at` | TIMESTAMPTZ | 발급 + 24시간 |
| `used_at` | TIMESTAMPTZ | 사용 시각. 1회용 |

### 2.5 qr_tokens — 영수증 QR 1회 토큰 (F-30)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `token` | TEXT **PK** | 토큰의 sha256 (원문은 QR URL에만) |
| `vehicle_id` | UUID | |
| `expires_at` | TIMESTAMPTZ | 발급 + 10분 |
| `used_at` | TIMESTAMPTZ | 사용 시각. 1회용 |

### 2.6 users — 직원 (F-51)

인덱스: `users_org (org_id)`.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `user_id` | UUID **PK** | |
| `org_id` | UUID | |
| `name` | TEXT | 1~50자 |
| `dept` | TEXT | |
| `email` | TEXT | 소문자 |

### 2.7 places — 장소 (F-52)

인덱스: `places_org (org_id)`. 자택(HOME)은 운영 웹에서 등록할 수 없다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `place_id` | UUID **PK** | |
| `org_id` | UUID | |
| `label` | TEXT | 이름 |
| `kind` | TEXT | OFFICE · BRANCH · CLIENT · GAS_STATION |
| `lat`, `lng` | DOUBLE PRECISION | 좌표 (없으면 주소로 카카오 지오코딩) |
| `radius_m` | INT | 기본 150, 30~2000 |
| `default_purpose` | TEXT | 기본 세부 목적 코드 |

### 2.8 schedules — 배차 (F-53)

인덱스: `schedules_vehicle_time (vehicle_id, starts_at)`.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `schedule_id` | UUID **PK** | |
| `org_id` | UUID | |
| `vehicle_id` | UUID | |
| `user_id` | UUID | 운전자. 직원을 지우면 NULL |
| `starts_at`, `ends_at` | TIMESTAMPTZ | 종료 > 시작, 최대 7일 |
| `dest_place_id` | UUID | 목적지. 장소를 지우면 NULL |
| `purpose_category` | TEXT | |
| `purpose_detail` | TEXT | |

### 2.9 recipients — 수신자 (F-54)

인덱스: `recipients_org_email UNIQUE (org_id, lower(email))` — 같은 이메일(대소문자 무시) 중복 불가.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGSERIAL **PK** | |
| `org_id` | UUID | |
| `email` | TEXT | 소문자 |
| `name` | TEXT | |

### 2.10 receipts — 영수증 (F-41, 30일 보관)

인덱스: `UNIQUE (vehicle_id, sha256)` — 같은 차에 같은 사진 중복 업로드 거부(409).

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `receipt_id` | UUID **PK** | |
| `vehicle_id` | UUID | |
| `sha256` | CHAR(64) | 원본 사진 해시 |
| `storage_key` | TEXT | MinIO 원본 키 |
| `thumb_key` | TEXT | MinIO 썸네일 키 (못 읽는 형식이면 원본 키) |
| `taken_at` | TIMESTAMPTZ | 업로드 시각 |
| `ocr_status` | TEXT | OK · FAILED |
| `ocr_fields` | JSONB | 상호·일시·유종·L·단가·할인·금액·카드 끝 4자리 (카드번호 마스킹 후) |
| `ocr_confidence` | JSONB | 필드별 확신도 0~1 |
| `arith_ok` | BOOLEAN | 검산 결과. 값이 모자라면 NULL |
| `created_at` | TIMESTAMPTZ | 기본 now() |
| `expires_at` | TIMESTAMPTZ | +30일. 매일 04:30 KST에 파일·행 삭제 |

### 2.11 inbox — 서버 → 차 우체통 (F-42, F-43)

인덱스: `(vehicle_id, created_at)`.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGSERIAL **PK** | 차의 `inbox_seen.inboxId`·`receipt_inbox.inboxId` |
| `vehicle_id` | UUID | 받는 차 |
| `kind` | TEXT | RECEIPT · USERS · PLACES · SCHEDULES · RECIPIENTS |
| `payload` | JSONB | `shared` `InboxPayload` JSON |
| `created_at` | TIMESTAMPTZ | 기본 now() |
| `expires_at` | TIMESTAMPTZ | +30일. ack 없이 지나면 매일 04:00 KST에 삭제 |

- RECEIPT는 영수증마다 한 통씩 쌓인다.
- USERS·PLACES·SCHEDULES·RECIPIENTS는 항상 전체 목록이라, 차가 아직 안 가져간 이전 편지는 지우고 최신 한 통만 둔다.
- 차가 ack하면 행을 지운다.

---

## 3. 서버 → 차 매핑 (inbox 수신, `data/InboxSync`)

| 서버 테이블 | inbox kind | 차 테이블 | 반영 방식 |
|---|---|---|---|
| users | USERS | users | `origin=SERVER` 행을 지우고 새로 넣음, LOCAL 보존 |
| places | PLACES | places | 같음 |
| schedules (이 차, 어제~30일 뒤) | SCHEDULES | schedules | 전부 지우고 새로 넣음 |
| recipients | RECIPIENTS | recipients | SERVER 교체. 같은 이메일이면 서버 배포분이 남음 |
| receipts | RECEIPT | receipt_inbox | 쌓음 → 사용자 확정 시 `expenses`(+`trip_expense_links`) 저장 후 ack |

서버 시각(`TIMESTAMPTZ`)은 ISO 8601 `+09:00` 문자열로 내려오고, 차에서 epoch millis로 바꿔 저장한다.
