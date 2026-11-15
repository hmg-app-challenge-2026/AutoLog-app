package kr.htaxlog.vehicle.di

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kr.htaxlog.vehicle.data.repository.ActiveTrip
import kr.htaxlog.vehicle.data.repository.TripSession
import kr.htaxlog.vehicle.data.usage.UsageTier
import kr.htaxlog.vehicle.nav.Navigator
import kr.htaxlog.vehicle.nav.Route
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GasStopForegrounderTest {
    private fun trip() = ActiveTrip("trip-1", 0, null)

    @Test
    fun `운행 중 주유소에 주차하면 주유 기록 추가를 한 번 열고 앱을 앞으로 가져온다`() = runTest {
        val session = MutableStateFlow<TripSession>(TripSession.Recording(trip(), 2.0))
        val tier = MutableStateFlow(UsageTier.BACKGROUND)
        val navigator = Navigator(Route.Home)
        var atStation = true
        var launches = 0
        val foregrounder = GasStopForegrounder(session, tier, navigator, backgroundScope, { atStation }) {
            launches++
            true
        }
        foregrounder.setAppVisible(false)
        runCurrent()
        assertEquals(0, launches) // 달리는 중에는 열지 않는다

        tier.value = UsageTier.FULL
        navigator.onTierChanged(UsageTier.FULL)
        runCurrent()
        assertEquals(1, launches)
        assertEquals(Route.ReceiptQr, navigator.current)

        session.value = TripSession.Recording(trip(), 2.1) // 같은 정차 중 값이 바뀌어도 다시 열지 않는다
        runCurrent()
        assertEquals(1, launches)
    }

    @Test
    fun `주유소가 아닌 곳에 주차하면 열지 않는다`() = runTest {
        val session = MutableStateFlow<TripSession>(TripSession.Recording(trip(), 2.0))
        val tier = MutableStateFlow(UsageTier.FULL)
        val navigator = Navigator(Route.Home).also { it.onTierChanged(UsageTier.FULL) }

        GasStopForegrounder(session, tier, navigator, backgroundScope, { false })
        runCurrent()

        assertEquals(Route.Home, navigator.current)
    }
}
