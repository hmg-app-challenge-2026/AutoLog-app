# `kr.autolog.vehicle.domain`

[앱 B] 차 안 판단 로직. Android 의존 없이 JVM 테스트로 돈다(CONVENTIONS 7.4). 데이터는 `core/DomainStore`(앱 A가 Room으로 구현)로 읽고 쓴다.

| 파일 | 기능 | 역할 |
|---|---|---|
| `PurposeRules`, `PlaceMatcher` | F-08 | 목적 추천 규칙 a–h(+ a2·a3 출발→도착 목적 규칙), 좌표 → 등록 장소 |
| `PurposeBook` | F-08, F-24 | 목적 목록·출발→도착 규칙. 회사 지정(inbox PURPOSES)과 이 차 것을 따로, 이 차에서 뺀 회사 지정은 숨김 기억 |
| `UserOrdering` | F-15 | 운전자 카드 순서: 배차 ±1h → 직전 운전자 → 최근 → 가나다 |
| `DriverResolver` | F-15, F-20 | 운전자 후보: 선택 1.0 → 배차 1명 0.95 → 2h 내 직전 운행 0.60 → 미확정 |
| `DefaultPurposeEngine` | F-08, F-15, F-17, F-20 | `PurposeEngine` 구현. 저장소에서 읽어 위 규칙에 넘김, 거래처 등록 제안·저장 |
| `ReceiptMatcher`, `ReceiptArithmetic` | F-19 | 영수증 ±2h 운행 후보(주유소 정차 우선, 최대 3), 금액 검산 |
| `ReceiptService` | F-19, F-30 | `ReceiptApi` 구현. QR 발급, 후보, 확정(검산·중복·카드 4자리·고친 필드 기록) |
| `SettingsService`, `RecipientBook` | F-24 | `SettingsApi` 구현. 수신자·직원 직접 입력(회사 지정 받는 사람 숨기기), 목적·규칙·새 장소(검색·지금 위치·저장), 월말 자동 발송 스위치, 지금 동기화 |
