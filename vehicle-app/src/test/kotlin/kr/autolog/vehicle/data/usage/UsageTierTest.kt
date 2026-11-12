package kr.autolog.vehicle.data.usage

import kr.autolog.vehicle.core.VehicleMotion
import org.junit.Assert.assertEquals
import org.junit.Test

/** 스펙 6.9 표의 모든 조합. */
class UsageTierTest {
    private val free = CarUxState(requiresDistractionOptimization = false, noKeyboard = false)
    private val restricted = CarUxState(requiresDistractionOptimization = true, noKeyboard = true)

    @Test
    fun `주차이고 플랫폼 제한이 없으면 전체 앱`() {
        assertEquals(UsageTier.FULL, usageTierOf(VehicleMotion.PARKED, free))
    }

    @Test
    fun `정차면 Peek`() {
        assertEquals(UsageTier.PEEK, usageTierOf(VehicleMotion.STOPPED, free))
        assertEquals(UsageTier.PEEK, usageTierOf(VehicleMotion.STOPPED, restricted))
        assertEquals(UsageTier.PEEK, usageTierOf(VehicleMotion.STOPPED, null))
    }

    @Test
    fun `주행이나 판정 불가는 항상 백그라운드`() {
        listOf(free, restricted, null).forEach {
            assertEquals(UsageTier.BACKGROUND, usageTierOf(VehicleMotion.MOVING, it))
            assertEquals(UsageTier.BACKGROUND, usageTierOf(null, it))
        }
    }

    @Test
    fun `주차여도 플랫폼이 DO를 요구하거나 연결에 실패하면 Peek까지만`() {
        assertEquals(UsageTier.PEEK, usageTierOf(VehicleMotion.PARKED, restricted))
        assertEquals(UsageTier.PEEK, usageTierOf(VehicleMotion.PARKED, null))
    }
}
