package kr.htaxlog.vehicle.navi

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class NaviTrackerTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC)
    private val pangyo = DestinationSnapshot("판교 A고객사", "성남시 분당구", 37.40, 127.10, "poi-1", 23_400)
    private val gangnam = DestinationSnapshot("강남 본사", "서울 강남구", 37.50, 127.03, "poi-2", 5_000)

    @Test
    fun `목적지 응답이 오면 현재 목적지와 이력에 반영된다`() {
        val tracker = NaviTracker(clock)

        tracker.onEvent(NaviEvent.DestinationInfoReceived(pangyo))

        assertEquals(pangyo, tracker.destination.value)
        assertEquals(listOf(DestinationChange(pangyo, clock.millis())), tracker.history.value)
    }

    @Test
    fun `남은 거리만 바뀐 같은 목적지는 이력에 다시 쌓이지 않는다`() {
        val tracker = NaviTracker(clock)
        tracker.onEvent(NaviEvent.DestinationInfoReceived(pangyo))

        tracker.onEvent(NaviEvent.DestinationInfoReceived(pangyo.copy(remainDistanceM = 1_000)))

        assertEquals(1, tracker.history.value.size)
        assertEquals(1_000, tracker.destination.value?.remainDistanceM)
    }

    @Test
    fun `운행 중 목적지 변경은 최종값과 변경 이력 2건으로 남는다`() {
        val tracker = NaviTracker(clock)

        tracker.onEvent(NaviEvent.DestinationInfoReceived(pangyo))
        tracker.onEvent(NaviEvent.DestinationInfoReceived(gangnam))

        assertEquals(gangnam, tracker.destination.value)
        assertEquals(listOf(pangyo, gangnam), tracker.history.value.map { it.destination })
    }

    @Test
    fun `경로가 끝나면 목적지가 비고 경로 안내 상태가 꺼진다`() {
        val tracker = NaviTracker(clock)
        tracker.onEvent(NaviEvent.DestinationInfoReceived(pangyo))
        tracker.onEvent(NaviEvent.RoutingStateReceived(true))

        tracker.onEvent(NaviEvent.RouteCleared)

        assertNull(tracker.destination.value)
        assertFalse(tracker.isRouting.value)
        assertNull(tracker.history.value.last().destination)
    }

    @Test
    fun `이력은 최근 N건만 보관한다`() {
        val tracker = NaviTracker(clock, historyLimit = 2)

        listOf(pangyo, gangnam, pangyo).forEach { tracker.onEvent(NaviEvent.DestinationInfoReceived(it)) }

        assertEquals(listOf(gangnam, pangyo), tracker.history.value.map { it.destination })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `도착 이벤트는 arrivals로 흘러간다`() = runTest(UnconfinedTestDispatcher()) {
        val tracker = NaviTracker(clock)
        val received = mutableListOf<ArrivalEvent>()
        val job = launch { tracker.arrivals.collect { received += it } }

        tracker.onEvent(NaviEvent.Arrived("판교 A고객사", null))

        assertEquals(listOf(ArrivalEvent("판교 A고객사", null, clock.millis())), received)
        job.cancel()
    }

    @Test
    fun `요청 직후 오는 응답을 기다려 돌려준다`() = runTest {
        val tracker = NaviTracker(clock)
        val location = NaviLocation(37.4, 127.1, "판교")

        val result =
            tracker.requestAndAwait<NaviEvent.CurrentLocationReceived> {
                tracker.onEvent(NaviEvent.CurrentLocationReceived(location)) // SDK가 바로 응답하는 경우
            }

        assertEquals(location, result?.location)
    }

    @Test
    fun `응답이 시간 안에 안 오면 null`() = runTest {
        val tracker = NaviTracker(clock)

        val result = tracker.requestAndAwait<NaviEvent.CurrentLocationReceived>(timeoutMs = 100) { }

        assertNull(result)
    }

    @Test
    fun `다른 종류 응답은 기다리는 응답으로 착각하지 않는다`() = runTest {
        val tracker = NaviTracker(clock)

        val result =
            tracker.requestAndAwait<NaviEvent.CurrentLocationReceived>(timeoutMs = 100) {
                tracker.onEvent(NaviEvent.RecentDestinationsReceived(emptyList()))
            }

        assertNull(result)
        assertTrue(tracker.history.value.isEmpty())
    }
}
