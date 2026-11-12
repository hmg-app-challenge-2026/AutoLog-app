package kr.autolog.vehicle.nav

import kr.autolog.vehicle.data.usage.UsageTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NavigatorTest {
    @Test
    fun `주차에서 화면을 쌓고 뒤로 갈 수 있다`() {
        val nav = Navigator(Route.Home)

        nav.push(Route.ProfilePicker)
        nav.pop()

        assertEquals(listOf(Route.Home), nav.backStack.value)
    }

    @Test
    fun `마지막 화면은 뒤로 가지 않는다`() {
        val nav = Navigator(Route.Home)

        assertFalse(nav.pop())
        assertEquals(Route.Home, nav.current)
    }

    @Test
    fun `영수증이 오면 QR을 입력 화면으로 교체한다`() {
        val nav = Navigator(Route.Home)
        nav.push(Route.ReceiptQr)

        nav.replaceTop(Route.FuelEntry("r-1"))

        assertEquals(listOf(Route.Home, Route.FuelEntry("r-1")), nav.backStack.value)
    }

    @Test
    fun `주차에서 정차로 내려가면 백스택이 Home 하나로 줄어든다`() {
        val nav = Navigator(Route.Home)
        nav.push(Route.ProfilePicker)

        nav.onTierChanged(UsageTier.PEEK)

        assertEquals(listOf(Route.Home), nav.backStack.value)
    }

    @Test
    fun `주차가 아니면 화면을 열 수 없다`() {
        val nav = Navigator(Route.Home)
        nav.onTierChanged(UsageTier.BACKGROUND)

        assertFalse(nav.push(Route.ProfilePicker))
        assertFalse(nav.replaceAll(Route.ProfilePicker))
        assertEquals(listOf(Route.Home), nav.backStack.value)
    }

    @Test
    fun `다시 주차해도 닫힌 화면을 되살리지 않는다`() {
        val nav = Navigator(Route.ProfilePicker)
        nav.onTierChanged(UsageTier.PEEK)

        nav.onTierChanged(UsageTier.FULL)

        assertEquals(listOf(Route.Home), nav.backStack.value)
    }

    @Test
    fun `운행 결과 카드는 정차·주행 중에도 바로 열고, 이미 맨 위면 두 번 쌓지 않는다`() {
        val nav = Navigator(Route.Home)
        nav.onTierChanged(UsageTier.BACKGROUND)

        nav.showTripResult(Route.TripResult("t-1"))
        nav.showTripResult(Route.TripResult("t-1"))

        assertEquals(listOf(Route.Home, Route.TripResult("t-1")), nav.backStack.value)
    }

    @Test
    fun `주차에서 내려가도 운행 직후 결과 카드는 Home 위에 남는다 — 다른 화면은 닫힌다`() {
        val nav = Navigator(Route.Home)
        nav.push(Route.Settings)
        nav.showTripResult(Route.TripResult("t-1"))

        nav.onTierChanged(UsageTier.PEEK)
        nav.onTierChanged(UsageTier.BACKGROUND)

        assertEquals(listOf(Route.Home, Route.TripResult("t-1")), nav.backStack.value)
    }

    @Test
    fun `사후 확인 카드는 주차 전용 — 내려가면 닫힌다`() {
        val nav = Navigator(Route.Home)
        nav.push(Route.History)
        nav.push(Route.TripResult("t-1", TripResultMode.REVIEW))

        nav.onTierChanged(UsageTier.PEEK)

        assertEquals(listOf(Route.Home), nav.backStack.value)
    }

    @Test
    fun `운행 직후 카드는 한 장만 — 다른 운행 카드가 뒤에 있으면 치운다`() {
        val nav = Navigator(Route.Home)
        nav.showTripResult(Route.TripResult("t-1"))
        nav.push(Route.Settings)

        nav.showTripResult(Route.TripResult("t-2"))

        assertEquals(listOf(Route.Home, Route.Settings, Route.TripResult("t-2")), nav.backStack.value)
    }

    @Test
    fun `기록 중이 되면 운행 직후 카드만 닫고 다른 화면은 그대로`() {
        val nav = Navigator(Route.Home)
        nav.push(Route.History)
        nav.push(Route.TripResult("t-0", TripResultMode.REVIEW))
        nav.showTripResult(Route.TripResult("t-1"))

        nav.closeTripResults()

        assertEquals(
            listOf(Route.Home, Route.History, Route.TripResult("t-0", TripResultMode.REVIEW)),
            nav.backStack.value,
        )
    }

    @Test
    fun `화면이 스스로 떠날 땐 자기만 빠진다 — 이미 치워졌으면 아무것도 닫지 않는다`() {
        val nav = Navigator(Route.Home)
        nav.push(Route.Settings)
        nav.showTripResult(Route.TripResult("t-1"))
        nav.closeTripResults()

        nav.close(Route.TripResult("t-1"))

        assertEquals(listOf(Route.Home, Route.Settings), nav.backStack.value)
    }
}
