# Pleos SDK 검증 결과

> 기술명세 6장 순서대로 채운다. 에뮬레이터 값인지 실차 값인지 반드시 표기.

| # | 항목 | 결과 | 환경 | 날짜 | 비고 |
|---|---|---|---|---|---|
| 1 | Vehicle Service 연결 | `initialize()` 직후 첫 조회가 10–22 ms 안에 성공. capability `READ` | 에뮬레이터 Pleos_Connect_v2.1.0 (API 34, arm64) | 2026-11-09 | 재시도는 실패 대비로 유지(F-01 5초×12). `VehicleProbeTest.connectsAndReadsOdometer` |
| 2 | Odometer 단위·소수점·갱신 주기 | 콘솔 입력값 그대로(18443.9 → `Float` 18443.9). 리스너 지연 4–5 ms. 23.4 km 차이 정확. **감소 불가**(콘솔이 거부) | 에뮬레이터 Pleos_Connect_v2.1.0 (API 34, arm64) | 2026-11-09 | 에뮬레이터는 값을 바꿀 때만 콜백 → 실차 갱신 주기 [검증 필요]. `odometerListenerReceivesChangeAndDistanceIsExact` |
| 3 | NaviHelper 목적지 콜백 | | | | |
| 4 | Fused Location 콜백·accuracy | **앱에 오지 않음:** 위치 서비스(`ai.pleos.playground.service.location.fused`)는 `adb emu geo fix` 값을 1초마다 만들지만(confidence 5.0) AutoLog 프로세스 로그엔 콜백이 없고 `freshFix()`가 null — 출발 좌표가 비었다. 등록 시점(`initialize` 직후 바인딩 전 등록?)·권한 [검증 필요] | 에뮬레이터 Pleos_Connect_v2.1.0 | 2026-11-13 | 지금은 내비 현재 위치가 좌표 출처. `LocationGateway.start` 확인 필요 |
| 4c | NaviHelper 현재 위치·address | `getCurrentLocationInfo` → 내비 앱이 한 번 열려 준비되기 전엔 `navi not ready`(응답 없음, 3초 시간 초과). 준비 뒤엔 좌표 + `address` = **`수정구 시흥동`(구·동만, 광역·기초시·도로명·번호 없음)**. 내비 위치는 `geo fix`와 무관하게 내비 자체 위치 | 에뮬레이터 Pleos_Connect_v2.1.0 | 2026-11-13 | 그래서 주소는 relay(카카오) 우선, 내비 주소는 폴백일 뿐(F-07, I-05) |
| 4a | 내비 전환 후 백그라운드 콜백 | **부분 확인:** 앱 화면 없이 포그라운드 서비스만 있는 프로세스에서 Odometer·Gear 콜백 수신, 자동 시작 → 23.4 km → 기어 P 5초 확정까지 동작 | 에뮬레이터 Pleos_Connect_v2.1.0 | 2026-11-09 | 내비 앱을 전면에 띄운 상태·10분 생존은 [검증 필요]. `TripScenarioTest` |
| 4b | 절전 후 프로세스 생존 | | | | |
| 5 | CarInfo·Gear 제공 범위 | Gear P/D 조회·리스너 동작(지연 13–16 ms). VIN 제공(9자 — 에뮬레이터 더미로 보임). DrivingMode는 기어가 아님(주행 모드) | 에뮬레이터 Pleos_Connect_v2.1.0 (API 34, arm64) | 2026-11-09 | Gear API는 **deprecated**("use the Google GEAR_SELECTION property") → AAOS `GEAR_SELECTION` 폴백 준비. `gearParkAndDriveArriveByReadAndListener`, `recordsVinAvailability` |
| 7 | XLSX 생성 속도·SMTP 587 | | | | |
| 8 | STT | | | | |

## AAR 정적 분석 (앱 B, 2026-11-08)

> 에뮬레이터 실행 전, Gradle 캐시의 AAR을 `javap`로 열어 확인한 **시그니처**다. 실제 동작(콜백이 오는지, 값 범위)은 에뮬레이터에서 다시 확인한다.

### NaviHelper 3.0.5 (`ai.pleos.playground.navi.helper.NaviHelper`)

| 항목 | 확인한 사실 | 명세와 차이 |
|---|---|---|
| 초기화 | `initialize()` / `initialize(onSuccess: () -> Unit, onError: (Throwable) -> Unit)`, `release()` | — |
| 조회 | `getDestinationInfo()`, `getRouteStateInfo()`, `getCurrentLocationInfo()`, `getRecentDestinationInfo()` 모두 **반환값 없음(void)** | 명세 2.3은 onComplete/onFailed 콜백 쌍을 가정. 실제는 **결과가 `NaviHelperEventListener`로 온다** → `NaviTracker.requestAndAwait` 로 처리 |
| 리스너 | `addListener/removeListener(NaviHelperEventListener)`. 전부 default 메서드라 필요한 것만 override | — |
| 목적지 | `onDestinationInfo(DestinationInfo)`: name, address, latitude, longitude, remainDistance(m), remainTime, poiId | — |
| 변경 알림 | `onDestinationChanged()` 는 **인자 없음** → 받으면 `getDestinationInfo()` 재호출 | — |
| 도착 | `onDestinationArrived(DestinationArrivedInfo)`: name, address, duration, distance | F-04 도착 신호로 사용 |
| 경로 상태 | `onRouteStateInfo(RouteStateInfo)`: isRouting, naviMode, routeOption … / `onRouteStarted`, `onRouteEnded`, `onRouteCancelled` | — |
| 현재 위치 | `onCurrentLocationInfo(CurrentLocationInfo)`: latitude, longitude, heading, address | F-07 폴백 |
| 상태·오류 | `onNavigationStatus(NavigationStatus: INACTIVE/INITIALIZING/FREE_DRIVE/ROUTE_DRIVE)`, `onError(NaviErrorCode)` | — |
| 매니페스트 | `<queries>` 에 `ai.pleos.playground.intent.action.NAVI_START` (SDK가 자동 병합), 서비스 최소 버전 2.0.1 | 별도 권한 선언 없음 |

### FusedLocation 1.0.4 (`ai.pleos.playground.location.fused.FusedLocation`)

| 항목 | 확인한 사실 | 명세와 차이 |
|---|---|---|
| 초기화 | `initialize()`, `release()` (콜백 없음) | — |
| 콜백 | `registerFusedLocationCallback / unregisterFusedLocationCallback(FusedLocationListener)`, `onChanged(LocationData)` 하나 | — |
| 값 | `LocationData(latitude, longitude, altitude, heading: Float, confidence: Float, timestampMillis)` | **accuracy(m) 필드 없음, confidence만 있음** → 명세 6장 대체안 "반경 매칭에 고정 여유 50 m" 적용 예정. confidence 범위는 [검증 필요] |
| 권한 | `PleosPermission.FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"` → vehicle-core 매니페스트에 선언 | 런타임 요청 필요 여부 [검증 필요] |

## Gleo AI SpeechToText 3.3.1 — AAR 정적 분석 (앱 B, 2026-11-09)

에뮬레이터 실행 전, Pleos Nexus의 `ai.pleos.playground:SpeechToText:3.3.1` AAR을 javap로 확인한 결과다.

| 항목 | 내용 |
|---|---|
| 좌표 | `ai.pleos.playground:SpeechToText:3.3.1` (의존: `ai.pleos.playground.security:universal-access:0.6.0`, gson, core-ktx). TextToSpeech 3.3.1도 같은 저장소에 있음 |
| 생성 | `SpeechToText(context, Mode)` — Mode: `HYBRID` / `SERVER` / `ON_DEVICE` |
| 순서 | `initialize(OnInitializeListener{onSuccess, onFailure(ErrorType)})` → `addListener(ResultListener)` → `request()` → (결과) → `stop()` → 앱 종료 때 `release()` |
| 결과 | `ResultListener.onUpdated(text: String, isFinal: Boolean)`, 그 밖에 `onReady`, `onStartedRecognition`, `onEndedRecognition`, `onUpdatedEpdData`, `onUpdatedRms` |
| 오류 | `onError(ErrorType)` — AUDIO_ERROR, MIC_ERROR, NETWORK_ERROR, TIMEOUT, UNAUTHENTICATED, PERMISSION_DENIED, UNAVAILABLE_LANGUAGE, NOT_SUPPORTED_API 등 |
| 서비스 | 매니페스트에 `ai.pleos.playground.caas.intent.action.SPEECH_START` queries, CAAS 최소 버전 4.1.29. 마이크는 CAAS 서비스가 쓰는 구조로 보임 |
| 기타 | `registerApp(...)`(서버 연결용으로 보임), `setLocale`, `sendAudio` 존재 |

**[검증 필요]** HYBRID가 `registerApp` 없이 동작하는지, `isFinal=true`가 한 번만 오는지, 앱에 RECORD_AUDIO 권한이 필요한지. 확인 전까지 `SpeechGateway.start()`가 실패하면 `available=false`로 두고 마이크 버튼을 숨긴다(F-09 예외).

## Vehicle 2.3.2 — AAR 정적 분석 (앱 A, 2026-11-09)

Gradle 캐시의 `ai.pleos.playground:Vehicle:2.3.2`(+ `vehicle-data:1.1.11`, `vehicle-aidl:1.1.1`)를 `javap`로 연 **시그니처**다. 값 범위·콜백 주기·에뮬레이터 동작은 아직 확인하지 않았다.

### 진입점·서비스

| 항목 | 확인한 사실 | 명세와 차이 |
|---|---|---|
| 생성·초기화 | `Vehicle(context)` → `initialize()` / `release()`. **둘 다 콜백 없음(void)** | 연결 완료 시점을 알 수 없다 → 첫 조회가 `onFailed`면 재시도(F-01 "5초×12") [검증 필요] |
| 도메인 객체 | `getOdometer()`, `getGear()`, `getCarInfo()`, `getFuel()`, `getDrivingMode()`, `getBrake()` 등 | — |
| 조회 형태 | `getXxx(onSuccess: (T) -> Unit, onFailed: (Exception) -> Unit)` | `sdkCall` 그대로 사용 가능 |
| 리스너 형태 | `registerXxx(listener)` / `unregisterXxx(listener)`. 모든 리스너는 `VehicleListener.onFailed(Exception)`을 상속 | `sdkListenerFlow` 그대로 사용 가능 |
| capability | `checkXxxCapabilities(onSuccess: (CapabilityConfig) -> Unit, onFailed)`. `CapabilityConfig.accessType`: `NONE / READ / WRITE / READ_WRITE` | 읽기 가능 = `READ` 또는 `READ_WRITE` |
| 매니페스트(SDK가 병합) | `ai.pleos.playground.service.vehicle.VEHICLE_BINDING` 권한, `queries`에 `ai.pleos.playground.vehicle.VEHICLE_START`, 서비스 최소 버전 2.3.13 | 앱이 따로 선언할 필요 없음 |

### AutoLog가 쓰는 API

| 용도 | API | 값 타입 | 권한(`@RequiresPermission`) |
|---|---|---|---|
| 누적 주행거리(F-05) | `Odometer.checkCurrentDistanceDrivenCapabilities` · `getCurrentDistanceDriven` · `registerDistanceDriven(DistanceDrivenListener)` → `onDistanceDrivenUpdated(Float)` | **`Float`** | `pleos.car.permission.CAR_DRIVE` |
| 기어(F-04, F-13) | `Gear.getCurrentGearSelectionState` · `registerGearSelection(GearSelectionListener)` → `onSelected(VehicleGear)` | `VehicleGear`: `GEAR_UNKNOWN, NEUTRAL, REVERSE, PARK, DRIVE, 1~9` | `CAR_DRIVE` |
| 차량 식별(F-01) | `CarInfo.checkVINInfoCapabilities` · `getVINInfo` · `getCRNInfo` | `String` | `CAR_INFO` |
| 연료량(F-18 보조) | `Fuel.checkFuelPercentCapabilities` · `getFuelPercent` · `registerFuelPercent` → `onFuelPercentUpdated(Integer)` | `Integer`(%) | `CAR_ENERGY` |

내부 구현(`OdometerImpl`)은 `PleosPropertyIds.PERF_ODOMETER`를 읽는다. 에뮬레이터에서 VHAL `PERF_ODOMETER`(0x11600204)를 주입하면 값이 바뀔 것으로 보인다 [검증 필요].

### 명세와 다른 점 (기술명세 2.2·2.4·2.8, 기능명세 F-04에 반영함)

1. **`DrivingMode`는 기어가 아니다.** `DrivingModeType`은 `NORMAL / ECO / SPORT`(주행 모드)다. 기어 P/D는 **`Gear` API**로 읽는다. 기술명세 2.4·2.8의 "DrivingMode=P"는 "Gear=PARK"로 고쳐야 한다. 프론트 스펙 1.1.1의 1순위 "Pleos DrivingMode"도 같은 이유로 "Pleos Gear"가 된다.
2. **Odometer 값은 `Float`다.** 18,443.9를 `Float`로 받아 그대로 `toDouble()`하면 18443.900390625가 된다. 저장할 때는 `Float.toString()` 기준 10진수(`BigDecimal`)로 바꿔 원본 표기를 지킨다. 10만 km대에서 `Float` 간격은 약 0.008 km라 0.1 km 표시에는 영향이 없다.
3. **거리 단위 API가 없다.** `DistanceDisplayUnit(KILOMETER, MILE)` enum은 있지만 이것을 돌려주는 공개 메서드가 없다. km로 간주한다 [검증 필요: 에뮬레이터 단위 설정을 바꿔도 값이 km인지].
4. **시동(Ignition)·주차 브레이크 공개 API가 없다.** `IgnitionState` enum과 `PleosPropertyIds.IGNITION_STATE`·`PARKING_BRAKE_ON`은 있지만 공개 메서드가 없다. 필요하면 AAOS `CarPropertyManager`(`android.car`, 권한 `android.car.permission.CAR_POWERTRAIN`)로 읽는다.
5. **권한 이름:** 명세 2.2의 `CAR_MILEAGE`, `CAR_DRIVING_MODE`는 없다. Odometer·Gear 모두 **`CAR_DRIVE`**다.

### 에뮬레이터에서 확인할 것 (표 #1·#2·#5)

- `initialize()` 직후 조회가 실패하는지, 몇 초 뒤 성공하는지
- `getCurrentDistanceDriven` 소수 자릿수, VHAL 주입 → 리스너 지연
- `registerGearSelection`이 VHAL `GEAR_SELECTION`(0x11400400) 주입에 반응하는지
- `getVINInfo`가 에뮬레이터에서 값을 주는지

### Kotlin에서 쓸 때 (컴파일로 확인)

- 도메인 객체는 프로퍼티가 아니라 **함수**다: `vehicle.getOdometer()`, `getGear()`, `getCarInfo()`, `getFuel()` (같은 이름의 `val`은 private).
- 콜백 값은 **전부 nullable**이다: `getCurrentDistanceDriven((Float?) -> Unit, …)`, `onDistanceDrivenUpdated(Float?)`, `onSelected(VehicleGear?)`, `getVINInfo((String?) -> Unit, …)`. null은 "못 읽음"으로 다룬다.
- `Gear.getCurrentGearSelectionState`·`registerGearSelection`은 `@Deprecated("This API is deprecated, use the Google GEAR_SELECTION property.")`. 2.3.2에서는 동작한다.

## 에뮬레이터 탐침 방법 (앱 A, 2026-11-09)

- 이 이미지는 **user 빌드**라 `adb shell cmd car_service inject-vhal-event`·`get-property-value`가 막혀 있다("requires non-user build").
- 대신 에뮬레이터 안 **Pleos Connect Virtual Console**(포트 8088)로 차량 값을 바꾼다.
  - 맥에서 보기: `adb forward tcp:8088 tcp:8088` → 브라우저 http://localhost:8088
  - 콘솔은 브라우저 세션이 살아 있을 때만 값을 받는다: `POST /browser/session?client=<id>`를 1초마다(타임아웃 3초). 없으면 `{"error":"emulator_inactive"}`
  - 계기판: `PUT /vehicle/odometer?traveled_distance_km=18443.9` (감소하면 `traveled_distance_must_not_decrease`)
  - 기어: `PUT /vehicle/gear?gear=Park|Reverse|Neutral|Drive`
  - 상태: `GET /vehicle/state`, 기능 목록·VHAL 매핑: `GET /vehicle/catalog` (계기판 = `PERF_ODOMETER` 0x11600204, 기어 = `VENDOR_GEAR_SELECTION` 0x21400400)
- 자동 탐침: `./gradlew :vehicle-core:connectedDebugAndroidTest` → `adb logcat -s AutoLog/probe AutoLog/vehicle`. 테스트(`VirtualConsole`)가 에뮬레이터 안 `127.0.0.1:8088`로 직접 하트비트·값 변경을 보낸다.

## 에뮬레이터에서 확인한 앱 실행 조건 (앱 A, 2026-11-09)

| 항목 | 결과 | 영향 |
|---|---|---|
| 앱 사용자 | Pleos(AAOS)는 앱이 **사용자 10**으로 돈다. `adb shell pm grant`는 `--user 10`이 필요 | 테스트·수동 권한 부여 |
| `pleos.car.permission.FUSED_LOCATION` | **런타임 권한**. 없으면 콜백 등록이 `SecurityException` — 잡지 않으면 앱이 죽는다 | 프론트가 `AutoLog.create` 전에 요청. 조립 범위에 예외 처리기(#27) |
| `pleos.car.permission.NAVI_ROUTE` | 선언 안 하면 NaviHelper initialize 실패 | vehicle-core 매니페스트에 선언(#27) |
| `POST_NOTIFICATIONS` | targetSdk 35에서 런타임 권한. 없으면 운행 알림이 안 보인다 | 프론트가 요청 |
| 포그라운드 서비스 알림 | Android 12+는 최대 10초 늦게 보인다("Deferring FGS notification") | `FOREGROUND_SERVICE_IMMEDIATE`(#21) |
| `foregroundServiceType=specialUse` | 시작 허용, 알림 표시 확인 | 기록 서비스(#21) |
| AAOS `IGNITION_STATE` | 콘솔 전원 시뮬레이터(Off/On)를 바꿔도 **값이 오지 않음**(초기값도 없음) | 시동 OFF 즉시 확정은 실차 [검증 필요]. 없으면 기어 P 5초·복구 경로로 끝난다 |
| Fuel | 콘솔 기능 목록에 연료가 없음(EV 구성) | 주유 감지(F-18 보조)는 실차 [검증 필요] |

## 차속 PERF_VEHICLE_SPEED — 에뮬레이터 확인 (앱 B, 2026-11-10)

Pleos Vehicle SDK 2.3.2에는 차속 공개 API가 없다(내부 상수 `PleosPropertyIds.PERF_VEHICLE_SPEED`만 있음). 그래서 AAOS `CarPropertyManager`의 `VehiclePropertyIds.PERF_VEHICLE_SPEED`(m/s, Float)를 읽는다(`CarPropertyGateway.speedKph`).

| 항목 | 결과 | 환경 |
|---|---|---|
| 권한 | `android.car.permission.CAR_SPEED` **런타임 권한**. 매니페스트에 없으면 `pm grant`가 "has not requested permission"으로 거부 | Pleos_Connect_v2.1.0 (API 34, arm64) |
| 등록 | 권한이 있으면 `registerCallback(…, 1 Hz)` 성공(`speed listener registered`) | 〃 |
| 값 | 가상 콘솔 `PUT /vehicle/controls?speed_kph=40` → 앱이 받음. **계기판은 그대로**(콘솔은 속도로 누적거리를 늘리지 않음) | 〃 |
| 세션 동작 | 기어 D + 차속 40, 계기판 0 → 자동 시작, 45초 뒤에도 "기록 중" 유지 · 차속 0 → 약 10초 뒤 정차 대기("기록을 마칠까요?") · 차속 25 → 기록 재개 · 계기판 12.3 → 거리 12.3 km · 기어 P → 확정, `trips.distanceKm = 12.3`, `VEHICLE_ODOMETER` | 〃 |

- 차속은 **움직임 판단에만** 쓰고 거리에는 쓰지 않는다(F-05). 권한·신호가 없으면 계기판만으로 판단한다.
- [검증 필요] 실차의 차속 갱신 주기, 계기판 갱신 단위(0.1 km 추정).
- 손 시험 도우미: `scripts/emu.sh` (setup · gear · speed · odo · state · log).
