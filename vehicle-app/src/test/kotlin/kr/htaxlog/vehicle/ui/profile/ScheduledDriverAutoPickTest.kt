package kr.htaxlog.vehicle.ui.profile

import kotlinx.coroutines.test.runTest
import kr.htaxlog.vehicle.TestAppGraph
import kr.htaxlog.vehicle.nav.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduledDriverAutoPickTest {
    @Test
    fun `시동 직후 선택 화면이 Home으로 바뀐 뒤에도 배차 운전자를 고른다`() = runTest {
        val graph = TestAppGraph(backgroundScope)
        graph.navigator.replaceAll(Route.Home) // 주행 여부를 아직 몰라 Home만 남은 상태

        val picked = ScheduledDriverAutoPick(graph.session, graph.profile, graph.navigator).run()

        assertEquals("u-1", picked)
        assertEquals("u-1", graph.trip.currentDriver.value)
        assertEquals(listOf(Route.Home), graph.navigator.backStack.value)
    }

    @Test
    fun `이미 운전자가 있으면 그대로 둔다`() = runTest {
        val graph = TestAppGraph(backgroundScope)
        graph.session.selectDriver("u-2")

        val picked = ScheduledDriverAutoPick(graph.session, graph.profile, graph.navigator).run()

        assertNull(picked)
        assertEquals("u-2", graph.trip.currentDriver.value)
    }
}
