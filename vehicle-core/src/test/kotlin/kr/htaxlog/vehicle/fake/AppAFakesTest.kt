package kr.htaxlog.vehicle.fake

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kr.htaxlog.vehicle.core.ConnectionState
import kr.htaxlog.vehicle.core.InputMode
import kr.htaxlog.vehicle.core.PurposeDetail
import kr.htaxlog.vehicle.core.RecoveryChoice
import kr.htaxlog.vehicle.core.RegisterResult
import kr.htaxlog.vehicle.core.ReportBuildResult
import kr.htaxlog.vehicle.core.SessionState
import kr.htaxlog.vehicle.core.StartResult
import kr.htaxlog.vehicle.core.StopReason
import kr.htaxlog.vehicle.core.TripTiming
import kr.htaxlog.vehicle.core.VehicleMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/** 앱 A 계약의 Fake로 프론트가 화면 흐름을 돌릴 수 있는지 확인한다. */
class AppAFakesTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-09T01:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `운행을 시작하고 거리가 늘면 liveKm가 오른다`() = runTest {
        val session = FakeTripSession(backgroundScope, clock)

        val started = session.startSession()
        session.addKm(23.4)

        assertTrue(started is StartResult.Started)
        assertEquals(23.4, (session.state.value as SessionState.Recording).liveKm!!, 1e-9)
        assertEquals(18_443.9, session.totalOdometerKm.value!!, 1e-9)
    }

    @Test
    fun `기록 중이면 다시 시작할 수 없다`() = runTest {
        val session = FakeTripSession(backgroundScope, clock)
        session.startSession()

        val second = session.startSession()

        assertTrue(second is StartResult.Failed)
    }

    @Test
    fun `기어 P면 5초 카운트다운 뒤 목적 확인으로 간다`() = runTest {
        val store = FakeLocalStore(clock, seed = false)
        val session = FakeTripSession(backgroundScope, clock, store = store)
        session.startSession()
        session.addKm(23.4)

        session.setMotion(VehicleMotion.PARKED)
        val candidate = session.state.value as SessionState.StopCandidate
        advanceTimeBy(TripTiming.T_GEAR_P_SEC * 1_000 + 1)
        runCurrent()

        assertEquals(StopReason.GEAR_P, candidate.reason)
        assertEquals(candidate.since + TripTiming.T_GEAR_P_SEC * 1_000, candidate.deadlineAt)
        assertTrue(session.state.value is SessionState.PendingReview)
        assertEquals(1, store.pendingReviews.first().size)
    }

    @Test
    fun `카운트다운 중 계속 기록을 누르면 운행이 이어진다`() = runTest {
        val session = FakeTripSession(backgroundScope, clock)
        session.startSession()
        session.setMotion(VehicleMotion.PARKED)

        session.resume()
        advanceTimeBy(TripTiming.T_GEAR_P_SEC * 1_000 + 1)
        runCurrent()

        assertTrue(session.state.value is SessionState.Recording)
    }

    @Test
    fun `목적을 확정하면 Idle로 돌아가고 도착지 이름이 저장된다`() = runTest {
        val store = FakeLocalStore(clock, seed = false)
        val session = FakeTripSession(backgroundScope, clock, store = store)
        val id = (session.startSession() as StartResult.Started).tripId
        session.finalize()

        session.confirmPurpose(id, PurposeDetail.CLIENT_VISIT, null, InputMode.TAP, destinationLabel = "강남 고객사")

        assertTrue(session.state.value is SessionState.Idle)
        assertEquals("강남 고객사", store.trip(id)!!.destinationLabel)
        assertEquals(PurposeDetail.CLIENT_VISIT, store.trip(id)!!.purpose)
    }

    @Test
    fun `복구 다이얼로그에서 삭제하면 Idle`() = runTest {
        val session = FakeTripSession(backgroundScope, clock)
        session.enterRecovery(extraKm = 5.0)

        session.resolveRecovery(RecoveryChoice.DELETE)

        assertTrue(session.state.value is SessionState.Idle)
    }

    @Test
    fun `샘플 저장소는 목적 미확정 1건과 영수증 1건으로 시작한다`() = runTest {
        val store = FakeLocalStore(clock)

        assertEquals(1, store.pendingReviews.first().size)
        assertEquals(1, store.receiptInbox.first().size)
        assertEquals(3, store.recent(3).first().size)
    }

    @Test
    fun `운행기록부 요약은 거리 미확정을 합계에서 빼고 검토로 센다`() = runTest {
        val store = FakeLocalStore(clock)
        val month = YearMonth.of(2026, 11)
        val rows = store.trips(month).first()

        val built = FakeReportApi(store).build(month) as ReportBuildResult.Built

        assertEquals(rows.size, built.file.tripCount)
        assertEquals(rows.mapNotNull { it.distanceKm }.sum(), built.file.totalKm, 1e-9)
        assertTrue(built.file.reviewCount >= 3)
    }

    @Test
    fun `연결 코드는 relay 형식만 받는다`() = runTest {
        val device = FakeDeviceApi(clock, registered = false)

        val bad = device.register("https://htaxlog.acme.co.kr", "12345")
        val ok = device.register("https://htaxlog.acme.co.kr/", "k7mq2x")

        assertTrue(bad is RegisterResult.Invalid)
        assertEquals(RegisterResult.Ok, ok)
        assertEquals("https://htaxlog.acme.co.kr", (device.connection.value as ConnectionState.Registered).serverUrl)
        assertNull((device.connection.value as? ConnectionState.Unreachable))
    }
}
