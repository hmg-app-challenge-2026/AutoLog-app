# `kr.htaxlog.vehicle.data`

[앱 A] 차 안 원본 저장소(Room)와 서버 우체통 수신. **차 → 서버로 운행·비용을 보내지 않는다.**

| 클래스 | 하는 일 |
|---|---|
| `HTaxLogDb` (v2) | trips, stops, vehicle_state, users, recent_users, places, schedules, recipients, expenses, trip_expense_links, receipt_inbox, inbox_seen, reports. 스키마 JSON은 `vehicle-core/schemas/`, 변경은 Migration으로 |
| `TripDao`·`ReferenceDao`·`ExpenseDao`·`InboxDao` | 쿼리. 서버 배포분 교체는 트랜잭션(LOCAL 보존) |
| `RoomDomainStore` | 앱 B용 `DomainStore` |
| `RoomLocalStore` | 화면용 `LocalStore`(Asia/Seoul 월 구분) |
| `RoomReportSource` | 운행기록부 재료 |
| `RelayHttp` | relay 차량 API(OkHttp + `shared` DTO). 서버 주소는 런타임 |
| `DeviceRepository` | `DeviceApi`: 연결 코드 등록, 기기 토큰, 연결 상태, QR 토큰 |
| `EncryptedDeviceStore` | 기기 토큰 암호화 저장 |
| `InboxSync` | 우체통 → Room + `RelayClient`(QR·지금 동기화). 영수증은 확정 때 ack |
| `InboxSyncWorker` | 15분 주기 + 즉시 1회 |

**실제 서버 확인(수동):** `cd ops && docker compose up -d relay postgres minio` → 운영 API(`X-Admin-Token`)로 차량·직원·연결 코드 생성 →
`./gradlew :vehicle-core:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=kr.htaxlog.vehicle.data.RelayLiveTest -Pandroid.testInstrumentationRunnerArguments.relayCode=<코드>`
(에뮬레이터에서 맥은 `10.0.2.2:8080`). 인자가 없으면 이 테스트는 건너뛴다.
