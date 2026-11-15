package kr.htaxlog.vehicle.di

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kr.htaxlog.vehicle.data.repository.ActiveTrip
import kr.htaxlog.vehicle.data.repository.AutoEndReason
import kr.htaxlog.vehicle.data.repository.TripSession
import kr.htaxlog.vehicle.data.usage.UsageTier
import kr.htaxlog.vehicle.nav.Navigator
import kr.htaxlog.vehicle.nav.Route
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripResultForegrounderTest {
    @Test
    fun `다른 앱 위에 알약이 있을 때 주차 종료가 시작되면 운행 결과를 한 번만 전면에 띄운다`() = runTest {
        val session = MutableStateFlow<TripSession>(TripSession.Recording(trip(), 1.2))
        val tier = MutableStateFlow(UsageTier.BACKGROUND)
        val navigator = Navigator(Route.Home)
        var launches = 0
        val foregrounder = TripResultForegrounder(session, tier, navigator, backgroundScope, { true }) {
            launches++
            true
        }
        foregrounder.setAppVisible(false)
        session.value = TripSession.AutoEnding(trip(), 1.2, AutoEndReason.GEAR_P, 5_000)
        runCurrent()
        assertEquals(0, launches) // 주행 중에는 내비를 가리지 않는다

        tier.value = UsageTier.FULL
        runCurrent()
        assertEquals(1, launches)
        assertEquals(Route.TripResult("trip-1"), navigator.current)

        session.value = TripSession.PendingReview("trip-1")
        runCurrent()
        assertEquals(1, launches)

        tier.value = UsageTier.BACKGROUND
        session.value = TripSession.Recording(trip(), 1.3)
        runCurrent()
        session.value = TripSession.AutoEnding(trip(), 1.3, AutoEndReason.GEAR_P, 10_000)
        tier.value = UsageTier.FULL
        runCurrent()
        assertEquals(2, launches) // [기록 이어가기] 뒤 같은 운행의 다음 주차
    }

    @Test
    fun `앱이 이미 보이거나 알약이 없으면 강제로 전환하지 않는다`() = runTest {
        val session = MutableStateFlow<TripSession>(TripSession.Recording(trip(), null))
        val tier = MutableStateFlow(UsageTier.FULL)
        val navigator = Navigator(Route.Home)
        var pillShowing = false
        var launches = 0
        val foregrounder = TripResultForegrounder(session, tier, navigator, backgroundScope, { pillShowing }) {
            launches++
            true
        }
        session.value = TripSession.PendingReview("trip-1")
        runCurrent()
        assertEquals(0, launches)

        foregrounder.setAppVisible(false)
        runCurrent()
        assertEquals(0, launches)
        pillShowing = true
        session.value = TripSession.Recording(trip("trip-2"), null)
        session.value = TripSession.PendingReview("trip-2")
        runCurrent()
        assertEquals(1, launches)
        assertEquals(Route.TripResult("trip-2"), navigator.current)
    }

    private fun trip(id: String = "trip-1") = ActiveTrip(id, 0, null)
}
