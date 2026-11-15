# `kr.htaxlog.vehicle.session`

[앱 A] 운행 세션 — `TripSessionApi` 실구현.

| 클래스 | 하는 일 |
|---|---|
| `TripSessionManager` | IDLE → RECORDING → STOP_CANDIDATE → FINALIZING → PENDING_REVIEW. 시작·10초 체크포인트·기어 P·종료를 Room에 즉시 커밋. 자동 시작(차가 움직이면), 자동 종료(기어 P 5초 / 정차·도착 180초 / 시동 OFF 즉시), 복구·놓친 운행 |
| `DistancePolicy` | 거리 = 계기판 차이(10진수). 실패·감소·1,500 km 초과 → 미확정(추정 금지), 60초·0.3 km 미만 → 이동 없음 |
| `DrivingJudge` | `VehicleMotion` 판정(P = 주차, 10초 안 증가 = 주행, 기어 없으면 60초 불변 = 주차, 모르면 null) |
| `RecoveryDecider` | 앱 시작 때: 자동 확정 / 복구 다이얼로그 / 놓친 운행 |
| `TripRecordingService` | specialUse 포그라운드 서비스. 상태 알림(기록 중, "기록을 마칠까요?" 카운트다운 + [계속 기록][기록 종료], 목적 확인 딥링크) |
| `TripNotificationSpec` | 상태 → 알림 내용(순수 함수) |
| `BootReceiver` | 부팅 뒤 `HTaxLog.create` (F-14) |

자동 종료 대기 상수는 `core/TripTiming`. 테스트: `SessionRulesTest`, `TripSessionManagerTest`(가상 시간), 에뮬레이터 `TripRecordingServiceTest`·`TripScenarioTest`.
