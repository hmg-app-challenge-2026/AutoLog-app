# 프론트 ↔ 백엔드 연결 기록

> 기준: Backend `aedb43e`(2026-11-11 갱신 — 처음 연결은 `c3883db`, 브랜치 `feat/front-back-connect`). 갱신분은 3.1·3.2.
> **2026-11-11 리포 통합(AutoLog-app, 스펙 D-33):** 아래의 `backend/…` 경로·"submodule 갱신"·"백엔드 코드는 고치지 않는다"는 리포가 두 개이던 때의 기록이다. 지금 백엔드 코드는 같은 리포의 `vehicle-core/`·`shared/`·`relay/`(앱 A·B 소유 — CONVENTIONS 1.2).
> **지금 남아 있는 미연결·불일치·정책 논의는 [`open-issues.md`](open-issues.md)에 모았다**(I-01~I-17). 이 문서는 연결 기록과 결정 이력.
> 원칙(사용자 지시 2026-11-10): **백엔드 코드는 고치지 않는다.** 연결할 수 있는 것만 목업 없이 연결하고, 조금이라도 애매하거나
> 정책이 어긋나는 것은 고치지 않고 아래 4장에 기록만 한다. DB에 프론트 목업 데이터를 넣는 방법은 5장 계획.

---

## 1. 무엇을 읽었나

| 범위 | 파일 |
|---|---|
| 계약 | `vehicle-core/.../core/Contracts.kt` · `ContractTypes.kt` · `AutoLog.kt`(조립 지점) |
| 앱 A 실구현 | `session/TripSessionManager.kt`(상태기계) · `DrivingJudge` · `DistancePolicy` · `RecoveryDecider` · `TripRecordingService` · `BootReceiver` · `data/RoomLocalStore` · `TripDao` · `RoomDomainStore` · `DeviceRepository` · `DeviceStore` · `InboxSync` · `report/ReportBuilder` · `ReviewCalculator` · `MonthlySender` |
| 앱 B 실구현 | `domain/DefaultPurposeEngine` · `SettingsService`(+`RecipientBook`) · `ReceiptService` · `mail/MailService` · `speech/VoiceService` · `navi/NaviGateway` · `location/LocationGateway` |
| 서버 | `relay/` 엔드포인트 목록 · `admin/AdminReferenceControllers`·`AdminReferenceService`·`AdminVehicleController`·`AdminAuth` · `ops/docker-compose.yml` · `application.yml` |
| 문서 | `docs/AutoLog_DB스키마.md`(신규) · 기능명세·기술명세 변경분 · `vehicle-core/docs/sdk-findings.md` · `vehicle-core/src/main/AndroidManifest.xml` |

## 2. 연결 지도 — 화면 Repository → 계약 → 실구현

실제 앱(vehicle flavor)은 `di/RealAppGraph`가 `AutoLog.create(app)`의 구성 요소로 조립한다. 데모·테스트는 `di/FakeAppGraph`(프론트 `data/fake` + 백엔드 `fake/`).

| 화면 Repository | 계약 | 실구현(백엔드) | 상태 |
|---|---|---|---|
| `SessionRepository` | `TripSessionApi`([기록 종료] = 주차 중 `requestStop` 5초 대기 · 정차·주행 중 `finalize`(D-34), 운행 결과 [저장하기] = `finalize` — 3.1) | `TripSessionManager` | 연결 |
| (화면 보임) | `AppPresenceApi.setAppVisible`(2026-11-12 추가, D-37) — `AppGraph.setAppVisible`이 알약과 함께 알린다 | `NotificationPresence` → `TripRecordingService` | 보이는 동안 운행 알림 헤즈업 없음 |
| (운행 삭제) | `TripSessionApi.discardTrip(tripId)`(2026-11-13 추가, D-39) — `SessionRepository.discardTrip`, 운행 결과 카드 [삭제하기] | `TripSessionManager`(`DISCARDED`, 종료 대기면 먼저 끝냄, 지운 운행엔 `confirmPurpose` 무시) | 연결 |
| `VehicleUsageRepository`(사용 3단계) | `TripSessionApi.motion` + AAOS `CarUxRestrictions` | `DrivingJudge` + 프론트 `AndroidCarUxSource` | 연결 |
| `ProfileRepository` | `PurposeEngine.orderedUsers` · `SettingsApi.users/addUser` · `TripSessionApi.currentDriver` | `DefaultPurposeEngine` · `SettingsService` | 연결(배차 순서는 G-02) |
| `ContextRepository` | `ContextApi.destination` | `GatewayContext`(NaviGateway) | 연결 |
| 운행 기록 알약(스펙 6.12) | `TripSessionApi.state`(기록 중 · `liveKm`) | `TripSessionManager` | 연결(vehicle flavor `RecordingPill`이 세션을 직접 읽는다 — 화면 Repository가 아니라 앱 쪽 훅) |
| `TripRepository`(이력·최근·월 요약) | `LocalStore.trips/recent` · `SettingsApi.users` | `RoomLocalStore` | 연결(요약 검토 수는 G-03) |
| `TripResultRepository` | `TripSessionApi.state/confirmPurpose` · `LocalStore.trip` · `PurposeEngine.suggest` | `TripSessionManager` · `DefaultPurposeEngine` | 연결 |
| `ReceiptRepository` | `ReceiptApi` · `LocalStore.receiptInbox` · `VoiceApi` | `ReceiptService`(확정 안에서 ack) · `RoomLocalStore` · `VoiceService` | 연결 |
| `ReportRepository` | `ReportApi` · `MailApi` · `SettingsApi.recipients` | `ReportBuilder` · `MailService` · `SettingsService` | 연결 |
| `SettingsRepository` | `DeviceApi` · `SettingsApi` · `MailApi` · (업로드) | `DeviceRepository` · `SettingsService` · `MailService` · **없음** | 연결(업로드는 G-04) |
| 알림 딥링크 | `core.DeepLink` | `TripRecordingService` 알림 | 연결(R-3) |

앱 시작: `MainActivity`가 런타임 권한(알림 · `pleos.car.permission.FUSED_LOCATION` · `android.car.permission.CAR_SPEED`)을 받은 뒤 처음으로 그래프를 꺼내고, 그때 `AutoLog.create`가 불린다(백엔드 주석 "Activity onCreate에서 — 포그라운드 서비스 시작이 허용되는 시점"). 부팅 뒤에는 백엔드 `BootReceiver`가 먼저 조립해 두고 앱은 같은 인스턴스를 받는다. 데모 flavor 매니페스트에서는 백엔드 `TripRecordingService`·`BootReceiver`를 뺀다.

## 3. 맞는지 확인한 정책 (코드로 확인, 바꾼 것 없음)

| 프론트 가정 | 백엔드 실제 | 근거 |
|---|---|---|
| 확정 대기(PendingReview)에서도 새 운행 시작(목적을 안 골라도 다음 운행이 기록된다) | 같음 — Recording·StopCandidate·NeedsRecovery만 거절 | `TripSessionManager.startLocked` |
| 기어 P → 5초 뒤 자동 종료, 그 외 이유는 180초 | 같음 | `TripTiming`, `enterStopLocked` |
| [기록 이어가기] = `resume`(StopCandidate → Recording) | 같음. 60초 이상 정차만 Stop으로 남김 | `resumeLocked` |
| [저장하기](종료 대기 중) = `finalize` 뒤 확정 | `finalize`는 StopCandidate에서 `since` 시각으로 끝냄 → PendingReview | `finalizeLocked` |
| 목적 확정이면 대기 상태로 | PendingReview의 그 운행을 확정하면 Idle | `confirmPurpose` |
| 거리 추정 금지(null = 거리 미확정) | 같음. 못 읽으면 마지막 측정 체크포인트로 끝내고, 그것도 없으면 null | `finalizeLocked`, `DistancePolicy` |
| 놓친 운행 [추가]·[무시], 복구 [기록하기][계속][삭제] | 같은 모양 | `acceptMissedTrip`·`resolveRecovery` |
| 운행 기록 조회 = 끝난 운행만, 최신 순 | `observeFinishedBetween`(RECORDING·DISCARDED 제외) | `TripDao` |
| 영수증 확정 = 저장 + inbox ack | `ReceiptService.confirm`이 둘 다 함(그래서 실제 앱은 화면 쪽 ack 없음) | `ReceiptService` |
| 주유 직접 입력 = 금액 필수, 검산 | 같음. `source = OCR`이면 거절 | `ReceiptService.saveFuel` |
| 수신자: 서버 배포분 먼저, 서버 배포분은 못 지움 | 같음 | `RecipientBook.merge`, `removeRecipient` |
| 알림 탭 → 운행 결과 | `DeepLink.ROUTE_REVIEW` + tripId, 앱 런처 Intent | `TripRecordingService.openApp` |

### 3.1 백엔드 갱신분 `c3883db` → `078c199` (2026-11-10, 에뮬레이터 QA 중 백엔드에 직접 반영된 것)

| 바뀐 것 | 내용 | 프론트 |
|---|---|---|
| `TripSessionApi.requestStop()` 추가 (`078c199`) | 기록 중 → 5초 종료 대기(`StopCandidate`, 이유 `USER_REQUEST`). 이 이유로 끝낸 운행은 **0 km여도 0.0 km로 남긴다**. 대기 중 기어 P가 들어와도 다시 세지 않는다 | **쓴다** — Home [기록 종료] = `requestStop`. 자동 종료와 같은 5초 대기 + 운행 결과 카드(2026-11-11 사용자 결정). 프론트 가짜(`data/fake/FakeTripSession`)도 같은 5초 대기 |
| `finalize()` 주석 | "종료 대기 화면·알림: 기다리지 않고 바로 끝낸다" | 운행 결과 [저장하기](종료 대기 중 바로 끝내기) = `finalize`. 알림 [기록 종료]도 `finalize`(앱 A) |
| 0 km 운행 | `finishLocked`: 이동 없음(`DistancePolicy.NoMovement` = **1분 미만 그리고 0.3 km 미만**)이면 `DISCARDED`. 단 `USER_REQUEST` 종료 대기로 끝낸 것은 남김 | **앱 A 정책을 그대로 따른다**(2026-11-11 사용자 결정): 자동 종료의 이동 없음은 버림, [기록 종료]로 끝낸 0 km는 결과 화면에서 볼 수 있게 남김. 1분 넘게 서 있다 끝낸 0.0 km 자동 종료 운행도 남는다 — G-09 |
| `ContextApi.currentNaviLocation()` 추가 (`6add996`) | 운행 시작 때 내비 현재 위치를 출발지로: `originLabel` = 내비 주소(공백이면 null), 좌표 출처 `NAVI_CURRENT`. 내비 위치가 없으면 Fused 좌표(`freshFix`) | 화면은 그대로 — 출발지 이름은 `LiveTrip.originLabel`/`TripRow.originLabel`로 이미 받는다(R-9). 이제 등록 장소 이름 대신 **내비 주소**가 올 수 있어 길다 — 출발지는 Home 경로·목록 줄 모두 한 줄 말줄임(`RouteFlow`·`TripListRow`)이라 깨지지는 않지만 앞부분만 보인다(에뮬레이터에서 확인 필요) |

### 3.2 백엔드 갱신분 `078c199` → `aedb43e` (PR #34, 앱 B, 2026-11-10) — 차속으로 주행 판단

| 바뀐 것 | 내용 | 프론트 |
|---|---|---|
| 차속 신호 | `CarPropertyGateway.speedKph` — AAOS `PERF_VEHICLE_SPEED`(m/s → km/h, 1 Hz). Pleos SDK에 차속 공개 API가 없어서 | — |
| 권한 `android.car.permission.CAR_SPEED` | `vehicle-core` 매니페스트에 선언, **런타임 권한** — 주석 "프론트가 요청한다". 없으면 차속 없이 계기판만으로 판단 | **연결** — vehicle `runtimePermissions()`에 추가. 조립(`AutoLog.create`) 때 리스너를 등록하므로 그 전에 받는다 |
| `DrivingJudge` | 기어 P가 아니고 차속 > 3 km/h(`MOVING_SPEED_KPH`)면 바로 `MOVING` | 사용 3단계(정차·주차)가 더 빨리 주행으로 바뀐다 — 화면 코드는 그대로(`TripSessionApi.motion`) |
| `TripSessionManager.onSpeed` | 움직이는 속도면 `lastMovedAt` 갱신 · 운행이 없으면 자동 시작 · 종료 대기 중이면 **기어 P 대기만 빼고** 기록 재개(`resume`). 같은 속도로 달리는 동안은 틱마다 움직임 유지 | [기록 종료](`USER_REQUEST`) 5초 대기 중에 차가 3 km/h를 넘게 움직이면 기록이 이어진다 — 화면은 전체 앱이 주차에서만 보이므로 보통은 일어나지 않는다. 그때는 사용 단계가 주행으로 내려가 운행 결과 카드도 닫힌다(스펙 1.1·6.9) |
| 거리 | 차속은 거리에 쓰지 않음(F-05) | 그대로 |
| `scripts/emu.sh` | Pleos 에뮬레이터 가상 콘솔 도우미(setup·gear·speed·odo·state·log). setup이 `pm grant --user 10`으로 위치·알림·차속 권한을 준다 | 에뮬레이터 QA 때 쓸 수 있다(맥 경로 기본값 `~/Library/Android/sdk`) |
| `sdk-findings.md` | 차속 에뮬레이터 확인 결과 | — |

## 4. 연결하지 못했거나 정책이 어긋나는 것 — 기록만 (고치지 않음)

**결정이 필요한 순서로 정렬했다.** "바꾼 것"이 있으면 그 칸에 적었다.

> 2026-11-10 사용자 결정: G-01 = 해결(①키패드 → 같은 날 ②키보드 입력으로 변경), G-02 = R-10 요청, G-03~G-08 = 보류(기록만 유지).

### G-01 차량 연결 코드 형식이 다르다 — ✅ 해결 (2026-11-10, 키보드 입력)
- **현상(이전):** 서버 연결 코드는 대문자·숫자 6자리(0·1·I·O 제외, `[2-9A-HJ-NP-Z]{6}` — `DeviceRepository.CODE`, relay `Tokens.newConnectCode`, DB 스키마 2.4). 프론트 설정의 연결 코드 팝업은 숫자판(0~9)만 받아서 실제 앱에서 차량 등록(F-01)을 할 수 없었다(등록이 전제인 영수증 QR F-30·inbox F-11·직원·수신자 배포도 막힘).
- **결정:** 처음엔 ① 글자+숫자 키패드(`CodeKeypad`)로 구현했다가, 같은 날 **② 키보드 입력**으로 바꿨다(사용자 지시 "키보드로 바꿔줘, 소문자도 대문자로"). 이유: 같은 팝업의 서버 주소가 이미 시스템 키보드라 입력 방식이 둘이 되고, 연결 코드는 차 한 대에 한 번 넣는 값이라 전용 키패드를 둘 만큼 자주 쓰지 않는다. 주행 중 키보드 잠금(`LocalKeyboardLocked`)은 입력 칸에 이미 있다. ③(서버에 숫자 코드 요청)은 백엔드 변경이라 기각.
- **바꾼 것:**
  - `data/repository/ConnectCode.kt` — 서버와 같은 규칙 한곳(길이 6, 글자표 `23456789ABCDEFGHJKLMNPQRSTUVWXYZ`). `normalize`: 소문자 → 대문자, 표에 없는 글자(0·1·I·O·공백·기호·한글)는 버리고 6자에서 자름 — 붙여 넣은 "7kq3-mz"도 "7KQ3MZ". ViewModel·데모 가짜가 함께 쓴다.
  - 설정 팝업: 연결 코드 = `CodeField` — 보이는 모양은 처음의 6칸 상자 그대로(사용자 지시), 칸을 누르면 대문자 키보드(`KeyboardCapitalization.Characters`)·자동 수정 끔·`KeyboardType.Ascii`. 대문자로 바꾸는 일은 키보드마다 다르므로 `normalize`가 책임진다.
  - 데모 가짜 `FakeDeviceApi`는 숫자 6자리 대신 같은 규칙으로 검사한다.
  - (키패드 단계에서 바꾼 숫자판 키 바탕 `surface3`은 유지 — 주유 입력 시트에서도 어두운 면 위에 키가 보이게.)
- **남은 확인:** 맥 에뮬레이터에서 ① Pleos 키보드가 대문자로 뜨는지(안 떠도 값은 대문자로 들어감) ② 키보드가 올라올 때 팝업이 가려지지 않는지 ③ relay가 발급한 실제 코드로 등록(5.3 A 스크립트와 함께).

### G-02 차량 id를 계약으로 받을 수 없다 — 📨 R-10 요청함 (2026-11-10)
- **현상:** `PurposeEngine.orderedUsers(vehicleId, now)`·`suggest(TripFacts(vehicleId=…))`·`unassignedTrips(vehicleId, …)`는 차량 id가 필요한데, 화면이 받는 `DeviceApi`·`ConnectionState`에는 번호판 라벨(`vehicleLabel`)만 있다. 실제 id는 `DeviceRepository.vehicleId`(data 패키지 — 화면 import 금지)에만 있다.
- **바꾼 것:** `RealAppGraph`는 등록 전 운행과 같은 `"unregistered"`를 넘긴다.
- **영향:** 배차 기반 운전자 순서·"배차" 배지(F-15)와 배차 규칙 목적 추천(F-08 a)이 실제 앱에서 비어 있다(배차는 `schedules.vehicleId`로 거른다). 최근 운전자·이름순·다른 추천 규칙은 그대로.
- **요청(R-10, 2026-11-10 요청함 — 사용자 결정):** `DeviceApi`에 `val vehicleId: StateFlow<String?>` 추가(추천) 또는 `ConnectionState.Registered`·`Unreachable`에 `vehicleId` 필드. 요청서: [`requests/R-10-vehicle-id.md`](requests/R-10-vehicle-id.md). 백엔드 조립(`AutoLog.create`)은 이미 `device.vehicleId`를 세션·보고서에 넘기고 있어 **화면 쪽 길만 없다**.
- **받으면 바꿀 곳:** `RealAppGraph.vehicleId()` 한 줄(지금 `"unregistered"` 고정) — 화면·Repository는 그대로.

### G-03 "검토 필요 N건"이 화면과 엑셀에서 다를 수 있다 — ⏸ 보류 (2026-11-10 사용자 결정)
- **현상:** 운행기록부 내보내기 요약의 검토 수는 프론트가 계산한다(목적·운전자·거리 미확정 운행 수 — `DefaultTripRepository.summary`). 엑셀·메일 본문의 검토 수는 백엔드 `ReviewCalculator`의 7종(목적 미확정 7일, 운전자 미확정, 거리 미확정, 배차–운행 불일치, 증빙 없음, 중복 영수증, 연료 이상치)이다.
- **영향:** 같은 달인데 화면 "2건", 엑셀 "4건"처럼 다를 수 있다.
- **결정 필요:** ① 만든 뒤에는 `ReportFile.reviewCount`로 바꿔 보여 주기(만들기 전 숫자는 "미확정 N건"처럼 이름을 바꿈) ② 백엔드에 미리보기 계약 요청 ③ 그대로 두고 설명만.

### G-04 사내 서버 업로드(기능명세 ②) 계약이 없다 — ⏸ 보류 (2026-11-10 사용자 결정)
- **현상:** 백엔드에 차 → 서버 운행 사본 전송이 없다(core `UploadState` 주석 "2차 합의 전이라 지금은 항상 null", R-4).
- **바꾼 것:** 실제 앱은 `UnavailableUploadApi`(꺼짐 · 대기 0건 · [지금 전송]은 "사내 서버 전송은 아직 준비 중이에요"로 실패). 내보내기 화면의 "사내 서버로도 자동 전송돼요"는 업로드가 **켜져 있을 때만** 보이게 했다 — 연결만 됐을 때 이 문장은 사실이 아니다. 업로드 자리표시 계약은 `data/upload/UploadApi.kt`(프론트 소유, 계약이 오면 삭제).
- **결정 필요:** 설정 "회사 정보"(옛 "사내 서버") 섹션을 업로드가 꺼져 있을 때 어떻게 보일지 — 지금은 전송 대기 0건·마지막 전송 없음·[지금 전송](누르면 실패 문구). 숨기기 / "준비 중" 한 줄 / 그대로 중 선택. 운행 기록 줄의 전송 표시는 `TripRow.upload`가 늘 null이라 저절로 안 보인다.

### G-05 권한을 나중에 허용하면 좌표가 다시 붙지 않을 수 있다 — ⏸ 보류 (2026-11-10 사용자 결정)
- **현상:** `AutoLog.create`는 프로세스당 한 번이고, `LocationGateway.start()`는 조립 때 한 번만 `initialize()`한다. 부팅 직후 `BootReceiver`가 권한 없이 먼저 조립했거나 사용자가 처음에 거절했다가 설정에서 허용하면, 앱을 다시 시작할 때까지 좌표(F-07 출발·도착)가 없을 수 있다.
- **영향:** 운행 기록·거리는 그대로, 출발·도착 좌표와 그에 기댄 추천(거래처·장소 매칭)만 빠진다.
- **확인 필요:** 맥 에뮬레이터에서 거절 → 허용 순서로 재현. 필요하면 앱 B에 "권한 허용 뒤 다시 시작" 요청.

### G-06 앱 시작 시 메인 스레드 작업 — ⏸ 보류 (2026-11-10 사용자 결정)
- **현상:** `AutoLog.create`는 호출 스레드에서 `EncryptedSharedPreferences`(키스토어)·Room 빌더·게이트웨이 시작을 한다. 앱은 이를 `MainActivity`(메인 스레드)에서 부른다(백엔드가 정한 시점).
- **영향:** 첫 화면이 조금 늦게 뜰 수 있다(키스토어 첫 접근 수백 ms). 맥 에뮬레이터에서 체감 확인.

### G-09 0 km 운행이 남을 수 있다 — ✅ 앱 A 정책을 따른다 (2026-11-11 사용자 결정)
- **현상:** 앱 A는 1분 미만 **그리고** 0.3 km 미만인 자동 종료 운행만 버린다(`DistancePolicy.NoMovement`). 1분 넘게 서 있다 끝낸 0.0 km 운행과 [기록 종료](`requestStop`)로 끝낸 0 km 운행은 남는다.
- **결정:** 한때 "0 km는 모두 버린다"(R-12)를 검토했지만 앱 A 정책을 그대로 따르기로 했다. R-12 철회. 프론트는 바꾼 것 없음.
- **참고:** 프론트 데모 가짜는 이동 없음 규칙을 흉내 내지 않는다(데모에서는 자동 종료든 [기록 종료]든 늘 결과 카드가 뜬다).

### G-07 계약은 있는데 화면이 일부러 쓰지 않는 것 (의도, 참고) — ⏸ 보류
- `PurposeEngine.unassignedTrips`·`shouldProposePlace`·`registerClientPlace`(F-20 선택 화면 질문, F-17 거래처 등록), `VoiceApi.listenPurpose`·`listenFuel`(F-09·F-18 음성) — D-19·D-27·D-20 결정으로 화면에서 뺐다. 계약·구현은 남아 있어 되살리기 쉽다.

### G-08 계약이 없는 화면 기능 (참고) — ⏸ 보류
- 직원 지우기(설정) — `SettingsApi`에 없음. 운행기록부 보관함(F-24) — Room `reports` 테이블은 있으나 조회 계약이 없음.

## 5. DB에 프론트 목업 데이터 넣기 — 계획 (구현 전, 결정 필요)

### 5.1 왜 한 가지 방법으로 안 되나
데이터가 두 곳에 나뉘어 있다(DB 스키마 0장): **직원·장소·배차·수신자의 원본은 서버**(PostgreSQL, 관리자 입력 → inbox로 차에 사본), **운행·정차·비용·운행기록부의 원본은 차 Room**(서버에 없음). 그래서 넣는 길도 둘이다.

| 목업 데이터(프론트 데모) | 원본 위치 | 넣는 길 |
|---|---|---|
| 직원(김영업·이현장·박관리…) · 수신자(회계팀…) · 장소(본사·판교 테크노밸리 거래처·수원 물류센터·SK 판교주유소) · 배차 | 서버 | **A. 관리자 API** → inbox → 차 Room(실제 경로) |
| 운행 이력(최근 운행·운행 기록 조회·운행기록부) · 비용(주유) | 차 Room | **B. 시드 DB 파일** 또는 C. 앱 A 시드 계약 |
| 영수증 OCR 대기(주유 입력 창) | 서버 `receipts` → 차 `receipt_inbox` | A'. 카메라 페이지 업로드(실제 OCR) 또는 B에 함께 |

### 5.2 목업 데이터를 한곳에
- `tools/seed/demo-fixture.json` 하나에 지금 데모가 보여 주는 값을 모은다(프론트 `data/fake`·백엔드 `fake/`의 샘플: 직원 3명, 수신자 2명, 장소 4곳, 배차 1건, 이번 달 운행 6건(목적 미입력·운전자 미확정·거리 미확정 포함), 주유 1건, 영수증 1건).
- 시각은 "지금 기준 상대값"(예: `-2h`)으로 적어 언제 넣어도 "오늘·어제"가 맞게 한다.
- 이후 프론트 가짜도 이 파일을 읽게 하면 데모·시연 DB·스크린샷의 숫자가 갈라지지 않는다(2단계, 선택).

### 5.3 A. 서버 기준 정보 — 관리자 API (추천, 백엔드 변경 없음)
- 로컬 relay를 띄운다: `ops/docker-compose.yml`(relay·postgres·minio — 리포 통합 전엔 `backend/ops/`). **실험은 WSL 로컬에서만** — 외부로 나가지 않게 docker 내부 네트워크, `ADMIN_TOKEN`은 로컬 값.
- `tools/seed/seed-relay.sh`(curl, 헤더 `X-Admin-Token`): `POST /admin/vehicles`(번호판 12가3456) → `POST /admin/users`·`/admin/places`(좌표 직접)·`/admin/recipients`·`/admin/schedules`(오늘 ±1h, 김영업) → `POST /admin/vehicles/{id}/connect-code`로 연결 코드 출력.
- 차에서: 설정 → 차량 연결(서버 주소 + 코드) → 등록 직후 inbox 1회 수신으로 직원·장소·배차·수신자가 Room에 `origin=SERVER`로 들어온다. G-01(연결 코드 글자)은 해결됐다(2026-11-10).
- 장점: 실제 경로 그대로라 F-01·F-11·F-15·F-24가 같이 검증된다. 반복 실행 가능(같은 이름이 있으면 건너뛰기 — 스크립트가 `GET`으로 확인).

### 5.4 B. 차 운행·비용 이력 — 시드 DB 파일 (추천, 백엔드 변경 없음)
- `tools/seed/build-room-db.py`가 `demo-fixture.json`과 백엔드 **스키마 JSON**(`vehicle-core/schemas/kr.autolog.vehicle.data.AutoLogDb/2.json`)으로 SQLite 파일을 만든다: 테이블 생성 SQL·`room_master_table`(identity hash)·`PRAGMA user_version = 2`를 스키마 JSON에서 그대로 가져와 Room이 "같은 버전"으로 연다.
- 넣는 데이터: `trips`(status = CONFIRMED/PENDING_REVIEW, `purposeSource = USER_CONFIRMED`인 것만 확정, 거리 미확정은 `distanceKm = null`·`distanceSource = UNAVAILABLE`, `revision = 1`), `expenses`·`trip_expense_links`, `vehicle_state`(`lastKnownOdometerKm` = 마지막 운행 끝 — 놓친 운행이 뜨지 않게), `recent_users`, (선택) `receipt_inbox`.
- 설치: 디버그 빌드에서 `adb shell am force-stop kr.autolog.vehicle` → `adb push autolog.db /data/local/tmp/` → `adb shell run-as kr.autolog.vehicle cp /data/local/tmp/autolog.db databases/autolog.db`(WAL·SHM 파일 삭제) → 앱 실행. `tools/seed/install-room-db.sh` 하나로.
- 안전장치: 스키마 JSON의 version이 2가 아니면 스크립트가 멈춘다(앱 A가 스키마를 올리면 다시 맞춘다). 릴리스 빌드에는 `run-as`가 안 되므로 실차 배포본에 섞일 수 없다.
- 단점: 앱 A 소유 스키마에 직접 쓴다 — **앱 A 동의 필요**(코드는 안 고치지만 데이터 규칙 — `revision`·`rawSnapshotJson`·`reviewFlags` — 을 우리가 채운다). `rawSnapshotJson`은 `{"seed":true}`로 표시해 감사 때 구분.

### 5.5 C. 대안 — 앱 A에 시드 계약 요청 (장기)
- core에 디버그 전용 `DebugSeedApi.importTrips(List<SeedTrip>)` 같은 계약을 요청(R-11 제안). 앱 A가 자기 규칙으로 쓰므로 가장 안전하지만 백엔드 변경이 필요해 이번 범위 밖.

### 5.6 A'. 영수증
- 실제 경로: 차 QR → 폰 카메라 페이지(F-30, `web/camera` — 아직 프론트에 없음)로 영수증 사진 업로드 → 서버 Gemini OCR(`GEMINI_API_KEY` 필요, 외부 호출) → inbox. 로컬 실험 원칙상 외부 OCR 호출은 별도 허락이 필요하다.
- 대안: 5.4의 시드 DB에 `receipt_inbox` 한 건(데모와 같은 OCR 값)을 함께 넣는다.

### 5.7 추천 순서
1. ~~G-01 결정·구현(연결 코드)~~ ✅ 2026-11-10.
2. `demo-fixture.json` + A(관리자 API 스크립트) — 실제 경로, 백엔드 변경 없음.
3. B(시드 DB) — 앱 A 동의 뒤.
4. (선택) 프론트 가짜도 fixture를 읽게.
