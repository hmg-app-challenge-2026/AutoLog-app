# `kr.htaxlog.vehicle.vehicle`

[앱 A] 차량 신호 게이트웨이. `ai.pleos.*`와 `android.car.*` import는 이 패키지에만 둔다.

| 클래스 | 하는 일 |
|---|---|
| `VehicleSignals` | 세션이 쓰는 경계: 계기판(`readOdometer`, `odometer`), 기어(`readGear`, `gear`), 시동 OFF(`ignitionOff`). 실패는 null·빈 흐름 |
| `VehicleGateway` | Pleos Vehicle SDK 2.3.2: Odometer(`getCurrentDistanceDriven`, Float km → 10진수 그대로), Gear(`registerGearSelection`), CarInfo(VIN), Fuel(%). `awaitReady()` = 연결 대기(5초×12) |
| `CarPropertyGateway` | AAOS `IGNITION_STATE`(시동). Pleos 공개 API가 없어서 |

권한: `pleos.car.permission.CAR_DRIVE`(Odometer·Gear), `CAR_INFO`, `CAR_ENERGY`, `android.car.permission.CAR_POWERTRAIN`.
SDK 사실·에뮬레이터 결과: [`docs/sdk-findings.md`](../../../../../../../docs/sdk-findings.md). 탐침: `VehicleProbeTest`.
