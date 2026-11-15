package kr.htaxlog.vehicle.domain

import kr.htaxlog.vehicle.core.DriverSource
import kr.htaxlog.vehicle.core.ScheduleInfo
import kr.htaxlog.vehicle.core.TripFacts
import kr.htaxlog.vehicle.core.TripSummary
import kr.htaxlog.vehicle.core.UserInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriverResolverTest {
    private val users = listOf(UserInfo("u-kim", "김영업"), UserInfo("u-lee", "이현장"))
    private val trip = TripFacts("t-2", "car-1", kst(10))

    private fun schedule(user: String, start: Long, end: Long = start + HOUR) =
        ScheduleInfo("s-$user", user, start, end)

    private fun prev(user: String?, end: Long) = TripSummary("t-1", "car-1", user, end - 30 * MINUTE, end)

    @Test
    fun `사용자가 고른 운전자가 있으면 그대로 1_0`() {
        val c = DriverResolver.resolve(
            trip.copy(driverUserId = "u-lee"),
            listOf(schedule("u-kim", kst(10))),
            null,
            users,
        )!!

        assertEquals("u-lee", c.userId)
        assertEquals(DriverSource.DRIVER_SELECTED, c.source)
    }

    @Test
    fun `배차 1시간 안에 운전자가 1명이면 배차로 0_95`() {
        val c = DriverResolver.resolve(trip, listOf(schedule("u-kim", kst(10, 50))), null, users)!!

        assertEquals("김영업", c.name)
        assertEquals(DriverSource.SCHEDULE, c.source)
        assertEquals(0.95, c.confidence, 0.0)
    }

    @Test
    fun `배차 운전자가 2명이면 고르지 않고 직전 운행으로 넘어간다`() {
        val schedules = listOf(schedule("u-kim", kst(10)), schedule("u-lee", kst(10, 30)))

        val c = DriverResolver.resolve(trip, schedules, prev("u-lee", kst(9)), users)!!

        assertEquals(DriverSource.PREVIOUS_TRIP, c.source)
    }

    @Test
    fun `2시간 안에 끝난 직전 운행의 운전자는 0_60 후보`() {
        val c = DriverResolver.resolve(trip, emptyList(), prev("u-lee", kst(8, 30)), users)!!

        assertEquals("u-lee", c.userId)
        assertEquals(0.60, c.confidence, 0.0)
    }

    @Test
    fun `직전 운행이 2시간보다 오래되면 미확정`() {
        assertNull(DriverResolver.resolve(trip, emptyList(), prev("u-lee", kst(7, 59)), users))
    }

    @Test
    fun `직전 운행도 운전자가 없으면 미확정`() {
        assertNull(DriverResolver.resolve(trip, emptyList(), prev(null, kst(9, 30)), users))
    }

    @Test
    fun `1시간보다 먼 배차는 보지 않는다`() {
        assertNull(DriverResolver.resolve(trip, listOf(schedule("u-kim", kst(11, 30))), null, users))
    }
}
