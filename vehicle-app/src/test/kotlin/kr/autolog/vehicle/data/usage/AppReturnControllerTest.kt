package kr.autolog.vehicle.data.usage

import kr.autolog.vehicle.core.VehicleMotion
import org.junit.Assert.assertEquals
import org.junit.Test

class AppReturnControllerTest {
    private class FakeGateway(var status: ReturnTargetStatus) : AppReturnGateway {
        override val supported = true

        override fun targetStatus() = status

        override fun hasUsageAccess() = true

        override fun openSettings() = true
    }

    @Test
    fun `최근 앱 후보가 확인되면 주행 때 물러난다`() {
        val gateway = FakeGateway(ReturnTargetStatus.RECENT_APP)
        var backgrounds = 0
        val controller = AppReturnController(gateway) { backgrounds++ }

        controller.onMotionConfirmed()

        assertEquals(1, backgrounds)
    }

    @Test
    fun `최근 앱이 없거나 판별 불가면 현재 화면을 유지한다`() {
        val gateway = FakeGateway(ReturnTargetStatus.NO_RECENT_APP)
        var backgrounds = 0
        val controller = AppReturnController(gateway) { backgrounds++ }

        controller.onMotionConfirmed()
        gateway.status = ReturnTargetStatus.UNKNOWN
        controller.onMotionConfirmed()

        assertEquals(0, backgrounds)
    }

    @Test
    fun `같은 주행 상태로 다시 전면에 오면 복귀 후보를 재평가한다`() {
        val gateway = FakeGateway(ReturnTargetStatus.NO_RECENT_APP)
        var backgrounds = 0
        val controller = AppReturnController(gateway) { backgrounds++ }

        controller.onResume(VehicleMotion.MOVING) // 최초 화면은 Root가 판단한다.
        gateway.status = ReturnTargetStatus.RECENT_APP
        controller.onResume(VehicleMotion.MOVING)
        controller.onResume(VehicleMotion.PARKED)

        assertEquals(1, backgrounds)
    }
}
