# `kr.autolog.vehicle.fake`

`core/Contracts.kt` 의 Fake 구현. 프론트가 화면을 만들 때, A·B가 서로를 기다리지 않고 테스트할 때 쓴다. main 소스셋에 둔다.

| Fake | 인터페이스 | 만든 사람 | 화면에서 바꿔 볼 것 |
|---|---|---|---|
| `FakeContext` | ContextApi | B | `setDestination(null)` 목적지 없음, `arrive()` 도착 |
| `FakePurposeEngine` | PurposeEngine | B | `suggestion = UNSURE` 낮은 신뢰도, `driver = null` 운전자 미확정 |
| `FakeVoiceApi` | VoiceApi | B | `purpose = VoiceResult.NotUnderstood("…")` 인식 실패 |
| `FakeReceiptApi` | ReceiptApi | B | `SAMPLE_RECEIPT` / `SAMPLE_UNSURE` / `SAMPLE_FAILED` 세 가지 영수증 |
| `FakeMailApi` | MailApi | B | `configured = false` 설정 전, `nextResult = Failed(…)` 발송 실패 |
| `FakeSettingsApi` | SettingsApi | B | `nextSync = SyncResult.Failed("…")` 동기화 실패 |
| `InMemoryDomainStore` | DomainStore | B | B 테스트용. 앱 A가 Room 구현을 만들 때 동작 기준 |
| `FakeRelayClient` | RelayClient | B | `qr = QrResult.Failed("…")` QR 발급 실패 |
| `FakeTripSession` | TripSessionApi | A | `setMotion(PARKED)` 기어 P 5초 자동 종료, `addKm()` 거리 증가, `setMissedTrip()` 놓친 운행, `enterRecovery()` 복구 |
| `FakeLocalStore` | LocalStore | A | `seed = false` 빈 기록, `pushReceipt()` 영수증 도착 |
| `FakeReportApi` | ReportApi | A | `nextFailure = "…"` 생성 실패 |
| `FakeDeviceApi` | DeviceApi | A | `registered = false` 미등록, `setUnreachable()` 서버 끊김 |
| `FakeGeocode` | GeocodeApi | A | `serverDown = true` 서버 실패 → 지금 위치면 내비 주소, 지난 좌표면 null |
