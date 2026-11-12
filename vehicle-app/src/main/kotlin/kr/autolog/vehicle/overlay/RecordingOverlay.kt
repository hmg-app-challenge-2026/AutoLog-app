package kr.autolog.vehicle.overlay

import kr.autolog.vehicle.data.repository.OverlayPermissionRepository

/**
 * 운행 기록 알약(스펙 6.11) — 기록 중이고 **AutoLog 화면이 보이지 않을 때만** 다른 앱(내비) 위에 "12.3 km · 15분"을 띄운다.
 * AutoLog가 보이는 동안은 Home 상태 줄이 같은 것을 보여 주므로 겹쳐 띄우지 않는다(2026-11-11 사용자 지시).
 * `MainActivity`가 보임·창 위치를 알린다. 구현은 vehicle flavor(`RecordingPill`), 데모는 [None].
 */
interface RecordingOverlay : OverlayPermissionRepository {
    /** AutoLog 화면이 보이는가(`onStart` = true, `onStop` = false). 분할 화면에서 한 칸에 떠 있어도 보이는 것이다. */
    fun setAppVisible(visible: Boolean)

    /** AutoLog 창의 가로 가운데(화면 좌표 px). 알약을 같은 앱 영역(AutoLog가 내려가면 내비가 뜨는 자리) 가운데에 둔다. */
    fun setAppAreaCenter(centerXPx: Int)

    /** 첫 실행에 한 번 시스템 권한 화면을 연다. 거절해도 설정 › 차량 정보에서 다시 열 수 있다. */
    fun askPermissionOnce()

    /** 데모·테스트: 알약 없음. */
    object None : RecordingOverlay {
        override val supported = false

        override fun isAllowed() = false

        override fun openSettings() = false

        override fun setAppVisible(visible: Boolean) = Unit

        override fun setAppAreaCenter(centerXPx: Int) = Unit

        override fun askPermissionOnce() = Unit
    }
}
