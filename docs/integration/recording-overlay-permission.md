# 주행 기록 표시 권한: 실차 배포 확인 사항

> 코드: 경계 `vehicle-app/src/main/kotlin/kr/autolog/vehicle/overlay/` · 실구현 `vehicle-app/src/vehicle/kotlin/kr/autolog/vehicle/overlay/RecordingPill.kt` · 설정 카드 `ui/settings/SettingsSections.kt`(`RecordingPermission`).

차량용 빌드는 기록 중이고 **AutoLog 화면이 보이지 않을 때**(내비 등 다른 앱이 앞에 있을 때) `TYPE_APPLICATION_OVERLAY`로 기록 상태를 표시한다(스펙 6.12). AutoLog가 보이는 동안은 숨긴다 — 분할 화면에서 한 칸에 떠 있어도 숨긴다. 이를 위해 차량용 manifest에 `SYSTEM_ALERT_WINDOW`를 선언하고, 표시 직전 `Settings.canDrawOverlays()`를 확인한다. 허용되지 않았거나 표시가 실패하면 기존 운행 알림은 계속 동작한다.

## 앱에서 제공하는 허용 경로

1. 첫 실행에서 일반 런타임 권한 처리 후, 특수 권한이 없으면 시스템의 `ACTION_MANAGE_OVERLAY_PERMISSION` 화면을 한 번 연다.
2. 사용자가 허용하지 않았을 때는 **설정 → 차량 정보 → 화면 위 표시 권한 허용**에서 다시 열 수 있다. 화면으로 돌아오면 허용 상태를 다시 확인한다.
3. Android 11 이상은 시스템이 `package:` URI를 무시하므로 앱 목록에서 AutoLog를 직접 선택해야 할 수 있다. Android 10 이하에서는 패키지 URI를 전달한다.
4. 기기에 해당 설정 화면이 없으면 앱은 그 사실을 표시하고 운행 알림 사용을 안내한다. 앱 내부에서 특수 권한을 자동 부여할 수 없다.

## Pleos 실차 배포 전 확인

현재 Pleos 에뮬레이터의 사용자 프로필에서는 `ACTION_MANAGE_OVERLAY_PERMISSION`을 처리하는 시스템 화면이 없다. 따라서 앱의 허용 경로만으로 **현재 에뮬레이터나 실차에서 권한이 허용된다고 보장할 수 없다**. Pleos 측에 다음 사항을 확인하고, 대상 차량 펌웨어에서 직접 검증해야 한다.

- Pleos App Market 심사에서 `SYSTEM_ALERT_WINDOW` 사용 목적을 승인하는지
- 차량에서 운전자가 특수 권한을 허용할 수 있는 시스템 UI를 제공하는지
- UI가 없다면 제조사/플랫폼이 배포 시 해당 앱의 overlay AppOp를 허용할 수 있는지, 그 지원 범위와 절차
- 내비게이션 앱 전면 상태, 주행 중 상태 및 화면 분할 상태에서 overlay 표시를 허용하는지

ADB의 `appops set ... SYSTEM_ALERT_WINDOW allow` 또는 `pm grant ... SYSTEM_ALERT_WINDOW`는 **개발 기기의 동작 확인용**이다. 앱 코드나 일반 배포 APK에서 실행할 수 있는 사용자 권한 요청 방법이 아니다.

## 실차 수용 기준

- 신규 설치 후 시스템 UI 또는 Pleos의 공식 배포 절차로 권한을 허용할 수 있다.
- `Settings.canDrawOverlays()`가 허용 상태를 보고하고, 내비 전면에서 주행 중 알약이 표시된다.
- 권한을 거부하거나 나중에 취소해도 운행 기록과 기존 운행 알림은 계속 동작한다.
- AutoLog 화면으로 돌아오면 알약이 바로 사라지고, 다시 다른 앱으로 가면 1초 안에 나타난다.
- 기록 종료 후 알약이 사라지고, 화면 크기 변경 시 내비 영역의 가로 중앙에 남는다.

참고: [Android `SYSTEM_ALERT_WINDOW`](https://developer.android.com/reference/android/Manifest.permission#SYSTEM_ALERT_WINDOW), [Android overlay 권한 설정](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_OVERLAY_PERMISSION), [Android 11 권한 변경](https://developer.android.com/about/versions/11/privacy/permissions), [Pleos 앱 등록 안내](https://document.pleos.ai/en/docs/pleos-only/distribute/app-submission-guide).
