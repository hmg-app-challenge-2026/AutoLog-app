package kr.htaxlog.vehicle.vehicle

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleSignalsTest {
    @Test
    fun `Float 계기판 값은 10진수 표기 그대로 km가 된다`() {
        assertEquals(18_443.9, 18_443.9f.toOdometerKm()!!, 0.0)
        assertEquals(18_420.5, 18_420.5f.toOdometerKm()!!, 0.0)
        assertEquals(0.0, 0f.toOdometerKm()!!, 0.0)
    }

    @Test
    fun `두 Float의 차이도 소수 1자리로 정확하다`() {
        val start = 18_420.5f.toOdometerKm()!!
        val end = 18_443.9f.toOdometerKm()!!

        assertEquals("23.4", "%.1f".format(end - start))
    }

    @Test
    fun `NaN·무한대·음수 계기판 값은 버린다`() {
        assertNull(Float.NaN.toOdometerKm())
        assertNull(Float.POSITIVE_INFINITY.toOdometerKm())
        assertNull((-1f).toOdometerKm())
    }

    @Test
    fun `수동 1~9단은 전진으로 본다`() {
        assertEquals(Gear.PARK, VehicleGear.GEAR_PARK.toGear())
        assertEquals(Gear.DRIVE, VehicleGear.GEAR_DRIVE.toGear())
        assertEquals(Gear.DRIVE, VehicleGear.GEAR_3.toGear())
        assertEquals(Gear.REVERSE, VehicleGear.GEAR_REVERSE.toGear())
        assertEquals(Gear.NEUTRAL, VehicleGear.GEAR_NEUTRAL.toGear())
        assertEquals(Gear.UNKNOWN, VehicleGear.GEAR_UNKNOWN.toGear())
    }

    @Test
    fun `서비스 연결 전 실패는 값이 나올 때까지 다시 시도한다`() = runTest {
        var calls = 0

        val r = retryUntilValue(attempts = 12, intervalMs = 5_000) { if (++calls < 3) null else "ok" }

        assertEquals("ok", r)
        assertEquals(3, calls)
        assertEquals(10_000, currentTime)
    }

    @Test
    fun `끝내 실패하면 null이고 마지막 시도 뒤에는 기다리지 않는다`() = runTest {
        var calls = 0

        val r = retryUntilValue<String>(attempts = 12, intervalMs = 5_000) {
            calls++
            null
        }

        assertNull(r)
        assertEquals(12, calls)
        assertEquals(55_000, currentTime)
    }
}
