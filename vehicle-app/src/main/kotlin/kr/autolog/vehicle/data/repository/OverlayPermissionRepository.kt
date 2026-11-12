package kr.autolog.vehicle.data.repository

/**
 * 다른 앱 위 운행 기록 알약(스펙 6.11)에 필요한 Android 특수 권한(`SYSTEM_ALERT_WINDOW`). 설정 › 차량 정보가 상태를 보이고
 * 시스템 권한 화면을 연다. 데모 빌드는 [supported] = false — 알약도 권한 카드도 없다.
 */
interface OverlayPermissionRepository {
    val supported: Boolean

    /** 지금 허용돼 있는가. 사용자가 시스템 화면에서 바꾸고 돌아오므로 화면이 다시 보일 때마다 묻는다. */
    fun isAllowed(): Boolean

    /** 시스템 권한 화면을 연다. 이 기기에 그 화면이 없으면 false — 앱이 스스로 권한을 줄 수는 없다. */
    fun openSettings(): Boolean
}
