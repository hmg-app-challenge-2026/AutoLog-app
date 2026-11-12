package kr.autolog.vehicle.ui.design.motion

import androidx.compose.animation.EnterExitState
import kr.autolog.vehicle.nav.Route
import kr.autolog.vehicle.nav.TripResultMode
import org.junit.Assert.assertEquals
import org.junit.Test

class NavTransitionTest {
    private val afterTrip = Route.TripResult("t-1")
    private val review = Route.TripResult("t-1", TripResultMode.REVIEW)

    @Test
    fun `스택이 길어지면 앞으로 — 새 화면이 오른쪽에서`() {
        assertEquals(NavTransition.PUSH, navTransitionOf(listOf(Route.Home), listOf(Route.Home, Route.Settings)))
        assertEquals(
            NavTransition.PUSH,
            navTransitionOf(listOf(Route.Home, Route.History), listOf(Route.Home, Route.History, review)),
        )
    }

    @Test
    fun `짧아지고 새 맨 위가 원래 스택에 있었으면 뒤로 — 처음 화면으로 한꺼번에 돌아가는 것도 뒤로`() {
        assertEquals(NavTransition.POP, navTransitionOf(listOf(Route.Home, Route.Settings), listOf(Route.Home)))
        val deep = listOf(Route.Home, Route.ReceiptQr, Route.FuelEntry("r-1"))
        assertEquals(NavTransition.POP, navTransitionOf(deep, listOf(Route.Home))) // 주유 저장 → replaceAll(Home)
    }

    @Test
    fun `운행 직후 운행 결과는 아래에서 떠오르고 내려가며 사라진다 — 이력에서 연 확인은 보통 화면`() {
        assertEquals(NavTransition.PRESENT, navTransitionOf(listOf(Route.Home), listOf(Route.Home, afterTrip)))
        assertEquals(NavTransition.DISMISS, navTransitionOf(listOf(Route.Home, afterTrip), listOf(Route.Home)))
        assertEquals(
            NavTransition.POP,
            navTransitionOf(listOf(Route.Home, Route.History, review), listOf(Route.Home, Route.History)),
        )
    }

    @Test
    fun `같은 길이로 바뀌거나 처음 화면을 바꾸면 같은 자리 교체`() {
        val qr = listOf(Route.Home, Route.ReceiptQr)
        // replaceTop(QR → 입력 창), 첫 실행 운전자 선택 → Home
        assertEquals(NavTransition.FADE_THROUGH, navTransitionOf(qr, listOf(Route.Home, Route.FuelEntry("r-1"))))
        assertEquals(NavTransition.FADE_THROUGH, navTransitionOf(listOf(Route.ProfilePicker), listOf(Route.Home)))
    }

    @Test
    fun `맨 위가 같으면 움직이지 않는다`() {
        assertEquals(NavTransition.NONE, navTransitionOf(listOf(Route.Home), listOf(Route.Home)))
    }

    @Test
    fun `뒤 화면 덮개 — 앞으로면 나가는 화면, 뒤로면 돌아오는 화면만 어두웠다가 밝아진다`() {
        assertEquals(1f, behindDimTarget(NavTransition.PUSH, EnterExitState.PostExit))
        assertEquals(0f, behindDimTarget(NavTransition.PUSH, EnterExitState.PreEnter))
        assertEquals(1f, behindDimTarget(NavTransition.POP, EnterExitState.PreEnter))
        assertEquals(0f, behindDimTarget(NavTransition.POP, EnterExitState.PostExit))
        assertEquals(0f, behindDimTarget(NavTransition.PRESENT, EnterExitState.PostExit))
        assertEquals(0f, behindDimTarget(NavTransition.PUSH, EnterExitState.Visible))
    }
}
