package kr.autolog.vehicle.nav

import kr.autolog.vehicle.core.DeepLink
import kr.autolog.vehicle.data.usage.UsageTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {
    @Test
    fun `알림 extra — 운행 결과는 운행 id가 있어야 하고 모르는 경로는 무시`() {
        assertEquals(Route.TripResult("t-1"), deepLinkRoute(DeepLink.ROUTE_REVIEW, "t-1"))
        assertNull(deepLinkRoute(DeepLink.ROUTE_REVIEW, null))
        assertNull(deepLinkRoute(DeepLink.ROUTE_REVIEW, " "))
        assertNull(deepLinkRoute("unknown", "t-1"))
        assertNull(deepLinkRoute(null, null))
    }

    @Test
    fun `주차 중 알림을 누르면 Home 위에 운행 결과가 열린다`() {
        val nav = Navigator(Route.Home)
        nav.onTierChanged(UsageTier.FULL)
        nav.push(Route.Settings)

        nav.handleDeepLink(Route.TripResult("t-1"))

        assertEquals(listOf(Route.Home, Route.TripResult("t-1")), nav.backStack.value)
    }

    @Test
    fun `주행 중 알림은 기억했다가 주차하면 연다 — 운전 중엔 화면이 없다`() {
        val nav = Navigator(Route.Home)
        nav.onTierChanged(UsageTier.BACKGROUND)

        nav.handleDeepLink(Route.TripResult("t-1"))
        assertEquals(listOf(Route.Home), nav.backStack.value)
        nav.onTierChanged(UsageTier.PEEK)
        assertEquals(listOf(Route.Home), nav.backStack.value)
        nav.onTierChanged(UsageTier.FULL)

        assertEquals(listOf(Route.Home, Route.TripResult("t-1")), nav.backStack.value)
    }

    @Test
    fun `앱 시작 직후(단계 모름) 들어온 알림도 주차가 확인되면 연다`() {
        val nav = Navigator(Route.ProfilePicker)

        nav.handleDeepLink(Route.TripResult("t-1"))
        assertEquals(listOf(Route.ProfilePicker), nav.backStack.value)
        nav.onTierChanged(UsageTier.FULL)

        assertEquals(listOf(Route.Home, Route.TripResult("t-1")), nav.backStack.value)
    }
}
