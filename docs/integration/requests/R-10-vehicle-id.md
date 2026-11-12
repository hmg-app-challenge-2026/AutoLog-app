# R-10 화면이 차량 id를 받을 수 있게 해 주세요 (앱 A, `DeviceApi`)

> 상태: **요청함** (2026-11-10, 프론트 → 앱 A). 기록: `docs/integration/backend-link.md` G-02, 스펙 12장 R-10.
> 백엔드 기준 커밋: `c3883db`. 이 리포는 백엔드 코드를 고치지 않는다 — 아래는 제안이고, 모양은 앱 A가 정한다.

## 1. 무엇이 안 되나

`PurposeEngine`의 세 함수가 **차량 id**를 받는다.

| 함수 | 쓰는 곳(화면) | 차량 id로 하는 일 |
|---|---|---|
| `orderedUsers(vehicleId, now)` | 운전자 선택(F-15) | 지금 ±창 안의 **배차**(`schedules.vehicleId`)로 맨 앞 운전자와 "배차" 배지 |
| `suggest(TripFacts(vehicleId = …))` | 운행 결과 목적 추천(F-08 a) | 운행 시각 근처 배차의 목적 |
| `unassignedTrips(vehicleId, now)` | (화면에서 쓰지 않음, D-19) | 이 차의 운행만 고름 |

그런데 화면이 받는 계약에는 차량 id가 없다.

- `DeviceApi.connection` → `ConnectionState.Registered(vehicleLabel, serverUrl, lastSyncAt)` — 번호판 **라벨**만 있다.
- 실제 id는 `DeviceRepository.vehicleId`(`data` 패키지)에만 있다. 화면은 `core`·`fake`만 import한다(CONVENTIONS 1.3).
- 백엔드 조립(`AutoLog.create`)은 이미 `device.vehicleId`를 `TripSessionManager`·`RoomReportSource`에 넘긴다. **화면 쪽 길만 없다.**

지금 프론트(`RealAppGraph.vehicleId()`)는 등록 전 운행과 같은 `"unregistered"`를 넘긴다. 그래서 실제 앱에서는:

- 배차가 있어도 운전자 목록 맨 앞에 오지 않고 "배차" 배지가 없다.
- 배차 규칙 목적 추천이 나오지 않는다.

최근 운전자·이름순, 다른 추천 규칙은 그대로 동작한다.

## 2. 제안 (둘 중 하나, 앱 A가 고름)

**안 1 — `DeviceApi`에 흐름 하나 추가 (추천)**

```kotlin
interface DeviceApi {
    val connection: StateFlow<ConnectionState>

    /** 프론트: 배차 기반 운전자 순서·목적 추천에 넘기는 차량 id. 미등록이면 null. */
    val vehicleId: StateFlow<String?>

    suspend fun register(serverUrl: String, code: String): RegisterResult
}
```

- 연결 상태(화면 표시)와 식별자(로직 입력)를 나눈다. `Unreachable`이어도 id는 그대로라 오프라인 배차 순서가 유지된다.
- `DeviceRepository`는 `_connection`을 바꾸는 곳(등록 성공·등록 해제·동기화 결과)마다 같이 갱신하면 된다 — 또는 `store.load()?.vehicleId`에서 파생.

**안 2 — `ConnectionState`에 필드 추가**

```kotlin
data class Registered(val vehicleId: String, val vehicleLabel: String, val serverUrl: String, val lastSyncAt: Long?)
data class Unreachable(val vehicleId: String, val vehicleLabel: String, val serverUrl: String)
```

- 바뀌는 곳이 적다. 대신 화면이 `when`으로 꺼내야 하고, 두 상태 모두에 넣어야 오프라인에서 끊기지 않는다.

어느 쪽이든 CONVENTIONS 5장대로 `contract/` 브랜치에서 **`fake/FakeDeviceApi`도 같이** 고쳐 주세요(데모 값은 아무 고정 id면 된다).

## 3. 함께 확인하고 싶은 것

- **등록 전에 기록한 운행:** 운행의 `vehicleId`는 기록 순간의 값이라 등록 전 운행은 `"unregistered"`로 남는다. 등록 뒤 `unassignedTrips(진짜 id)`는 이 운행들을 거른다. 의도라면 그대로, 아니면 등록 때 다시 적을지 앱 A가 정해 주세요(화면은 지금 이 함수를 쓰지 않음).
- **미등록일 때 넘길 값:** 프론트는 `vehicleId == null`이면 지금처럼 `TripSessionManager.UNREGISTERED_VEHICLE`과 같은 `"unregistered"`를 넘기려 한다. 이 상수를 `core`로 옮겨 주면 문자열을 복사하지 않아도 된다(선택).

## 4. 받으면 프론트가 바꿀 곳

- `vehicle-app/.../di/RealAppGraph.kt`의 `vehicleId()` 한 줄 — `components.device.vehicleId.value ?: "unregistered"`.
- 화면·Repository·ViewModel은 그대로(이미 함수로 받아 쓴다).
- `backend-link.md` G-02를 해결로, 스펙 12장 R-10을 ✅로.

---

## 부록: 그대로 올릴 이슈 본문

> 제목: `[contract] DeviceApi에 차량 id 노출 (R-10, F-15·F-08)`

```markdown
## 무엇을
화면이 `PurposeEngine.orderedUsers(vehicleId, …)`·`suggest(TripFacts(vehicleId=…))`에 넘길 차량 id를 계약으로 받을 수 없습니다.
`DeviceApi`/`ConnectionState`에는 번호판 라벨만 있고, id는 `data.DeviceRepository.vehicleId`에만 있어 화면이 import할 수 없습니다(CONVENTIONS 1.3).
지금 프론트는 "unregistered"를 넘겨서, 실제 앱에서 배차 기반 운전자 순서·"배차" 배지(F-15)와 배차 목적 추천(F-08 a)이 비어 있습니다.

## 제안
- 안 1(추천): `DeviceApi`에 `val vehicleId: StateFlow<String?>` (미등록 null)
- 안 2: `ConnectionState.Registered`·`Unreachable`에 `vehicleId` 필드
- 같은 PR에서 `fake/FakeDeviceApi`도 함께 (CONVENTIONS 5장)

## 같이 정해 주세요
- 등록 전 운행은 vehicleId가 "unregistered"로 남아 등록 뒤 `unassignedTrips(진짜 id)`에서 빠집니다. 의도인지요?
- (선택) `UNREGISTERED_VEHICLE` 상수를 `core`로.

## 프론트 영향
`RealAppGraph.vehicleId()` 한 줄만 바뀝니다. 화면 변경 없음.
상세: AutoLog-Frontend `docs/integration/requests/R-10-vehicle-id.md`
```
