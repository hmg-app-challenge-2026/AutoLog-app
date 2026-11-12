package kr.autolog.vehicle.session

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kr.autolog.vehicle.core.InputMode
import kr.autolog.vehicle.core.NaviCurrentLocation
import kr.autolog.vehicle.core.PurposeDetail
import kr.autolog.vehicle.core.RecoveryChoice
import kr.autolog.vehicle.core.SessionState
import kr.autolog.vehicle.core.StartResult
import kr.autolog.vehicle.core.StopReason
import kr.autolog.vehicle.core.TripTiming
import kr.autolog.vehicle.core.VehicleMotion
import kr.autolog.vehicle.data.DistanceSource
import kr.autolog.vehicle.data.PlaceSource
import kr.autolog.vehicle.data.TripEntity
import kr.autolog.vehicle.data.TripStatus
import kr.autolog.vehicle.data.VehicleStateEntity
import kr.autolog.vehicle.fake.FakeContext
import kr.autolog.vehicle.fake.FakePurposeEngine
import kr.autolog.vehicle.vehicle.Gear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSessionManagerTest {
    private class Rig(scope: TestScope, setup: (InMemoryTripDao, FakeVehicle) -> Unit = { _, _ -> }) {
        val clock = scope.virtualClock()
        val car = FakeVehicle(clock)
        val dao = InMemoryTripDao()
        val ctx = FakeContext()
        val engine = FakePurposeEngine()
        var ids = 0
        val session: TripSessionManager

        init {
            setup(dao, car)
            session =
                TripSessionManager(car, dao, ctx, engine, scope.backgroundScope, clock, { "car-1" }, { "t-${++ids}" })
            session.start()
            scope.runCurrent()
        }

        val stateValue get() = session.state.value
    }

    private suspend fun TestScope.startedTrip(rig: Rig): String {
        rig.car.shift(Gear.DRIVE)
        runCurrent()
        return (rig.session.startSession() as StartResult.Started).tripId
    }

    @Test
    fun `운행을 시작하면 Room에 기록 중 행이 즉시 생긴다`() = runTest {
        val rig = Rig(this)

        val id = startedTrip(rig)

        val row = rig.dao.trip(id)!!
        assertEquals(TripStatus.RECORDING, row.status)
        assertEquals(18_420.5, row.startOdometerKm!!, 0.0)
        assertEquals("판교 테크노밸리 거래처", row.destinationLabel)
        assertEquals(0.0, (rig.stateValue as SessionState.Recording).liveKm!!, 0.0)
    }

    @Test
    fun `운행 시작 시 내비 현재 위치 주소를 출발지로 저장한다`() = runTest {
        val rig = Rig(this)
        rig.ctx.setNaviLocation(NaviCurrentLocation(37.4, 127.1, " 서울 강남구 테헤란로 1 "))

        val row = rig.dao.trip(startedTrip(rig))!!

        assertEquals("서울 강남구 테헤란로 1", row.originLabel)
        assertEquals(37.4, row.originLat!!, 0.0)
        assertEquals(127.1, row.originLng!!, 0.0)
        assertEquals(PlaceSource.NAVI_CURRENT, row.originSource)
        assertEquals("서울 강남구 테헤란로 1", (rig.stateValue as SessionState.Recording).trip.originLabel)
    }

    @Test
    fun `내비 현재 위치가 없으면 위치 SDK 좌표로 출발지를 저장한다`() = runTest {
        val rig = Rig(this)

        val row = rig.dao.trip(startedTrip(rig))!!

        assertNull(row.originLabel)
        assertEquals(FakeContext.SAMPLE_FIX.lat, row.originLat!!, 0.0)
        assertEquals(FakeContext.SAMPLE_FIX.lng, row.originLng!!, 0.0)
        assertEquals(PlaceSource.FUSED_LOCATION, row.originSource)
    }

    @Test
    fun `달리면 liveKm가 오르고 10초마다 체크포인트를 남긴다`() = runTest {
        val rig = Rig(this)
        val id = startedTrip(rig)

        advanceTimeBy(10_000)
        rig.car.drive(23.4)
        runCurrent()

        assertEquals(23.4, (rig.stateValue as SessionState.Recording).liveKm!!, 0.0)
        assertEquals(18_443.9, rig.dao.trip(id)!!.lastOdometerKm!!, 0.0)
        assertEquals(VehicleMotion.MOVING, rig.session.motion.value)
    }

    @Test
    fun `기어 P면 5초 카운트다운 뒤 목적 확인으로 가고 거리는 23점4 km다`() = runTest {
        val rig = Rig(this)
        val id = startedTrip(rig)
        advanceTimeBy(5_000)
        rig.car.drive(23.4)
        runCurrent()

        rig.car.shift(Gear.PARK)
        runCurrent()
        val candidate = rig.stateValue as SessionState.StopCandidate
        advanceTimeBy(TripTiming.T_GEAR_P_SEC * 1_000 + 1_000)

        assertEquals(StopReason.GEAR_P, candidate.reason)
        assertEquals(candidate.since + TripTiming.T_GEAR_P_SEC * 1_000, candidate.deadlineAt)
        val review = rig.stateValue as SessionState.PendingReview
        assertEquals(23.4, review.distanceKm!!, 0.0)
        assertEquals(PurposeDetail.CLIENT_VISIT, review.suggestion?.detail)
        val row = rig.dao.trip(id)!!
        assertEquals(TripStatus.PENDING_REVIEW, row.status)
        assertEquals(DistanceSource.VEHICLE_ODOMETER, row.distanceSource)
        assertEquals(18_443.9, rig.dao.state!!.lastKnownOdometerKm!!, 0.0)
    }

    @Test
    fun `기어 P 뒤 5초 안에 다시 D면 운행이 이어진다`() = runTest {
        val rig = Rig(this)
        startedTrip(rig)
        rig.car.drive(5.0)
        runCurrent()

        rig.car.shift(Gear.PARK)
        runCurrent()
        advanceTimeBy(2_000)
        rig.car.shift(Gear.DRIVE)
        advanceTimeBy(10_000)

        assertTrue(rig.stateValue !is SessionState.PendingReview)
    }

    @Test
    fun `D에서 10초 멈추면 180초 대기에 들어가고 다시 달리면 이어진다`() = runTest {
        val rig = Rig(this)
        val id = startedTrip(rig)
        rig.car.drive(3.0)
        runCurrent()

        advanceTimeBy(12_000)
        val candidate = rig.stateValue as SessionState.StopCandidate
        advanceTimeBy(70_000)
        rig.car.drive(0.5)
        runCurrent()

        assertEquals(StopReason.NO_MOVEMENT, candidate.reason)
        assertTrue(candidate.deadlineAt - candidate.since >= TripTiming.T_STOP_SEC * 1_000)
        assertTrue(rig.stateValue is SessionState.Recording)
        assertEquals(1, rig.dao.stops(id).size)
    }

    @Test
    fun `시동이 꺼지면 카운트다운 없이 바로 확정한다`() = runTest {
        val rig = Rig(this)
        startedTrip(rig)
        advanceTimeBy(5_000)
        rig.car.drive(23.4)
        runCurrent()

        rig.car.turnOff()
        runCurrent()

        assertEquals(23.4, (rig.stateValue as SessionState.PendingReview).distanceKm!!, 0.0)
    }

    @Test
    fun `종료 때 계기판을 못 읽으면 기어 P 때 읽어 둔 값으로 끝낸다`() = runTest {
        val rig = Rig(this)
        startedTrip(rig)
        rig.car.drive(23.4)
        runCurrent()
        rig.car.shift(Gear.PARK)
        runCurrent()

        rig.car.readable = false
        advanceTimeBy(6_000)

        assertEquals(23.4, (rig.stateValue as SessionState.PendingReview).distanceKm!!, 0.0)
    }

    @Test
    fun `시작 계기판을 못 읽었으면 거리는 미확정이고 추정하지 않는다`() = runTest {
        val rig = Rig(this)
        rig.car.readable = false
        val id = startedTrip(rig)
        assertNull((rig.stateValue as SessionState.Recording).liveKm)
        rig.car.readable = true
        advanceTimeBy(120_000)

        rig.session.finalize()

        assertNull((rig.stateValue as SessionState.PendingReview).distanceKm)
        assertEquals(DistanceSource.UNAVAILABLE, rig.dao.trip(id)!!.distanceSource)
    }

    @Test
    fun `시작하고 바로 끄면 이동 없음으로 버린다`() = runTest {
        val rig = Rig(this)
        val id = startedTrip(rig)

        rig.session.finalize()

        assertTrue(rig.stateValue is SessionState.Idle)
        assertEquals(TripStatus.DISCARDED, rig.dao.trip(id)!!.status)
    }

    @Test
    fun `사용자가 기록 종료를 누르면 5초 대기 후 0 km 운행도 결과로 남긴다`() = runTest {
        val rig = Rig(this)
        val id = startedTrip(rig)

        rig.session.requestStop()
        val candidate = rig.stateValue as SessionState.StopCandidate
        assertEquals(StopReason.USER_REQUEST, candidate.reason)
        assertEquals(candidate.since + 5_000, candidate.deadlineAt)

        advanceTimeBy(4_000)
        assertTrue(rig.stateValue is SessionState.StopCandidate)
        advanceTimeBy(2_000)

        assertEquals(0.0, (rig.stateValue as SessionState.PendingReview).distanceKm!!, 0.0)
        assertEquals(TripStatus.PENDING_REVIEW, rig.dao.trip(id)!!.status)
    }

    @Test
    fun `목적을 확정하면 확정 상태로 저장하고 Idle로 돌아간다`() = runTest {
        val rig = Rig(this)
        val id = startedTrip(rig)
        rig.car.drive(23.4)
        runCurrent()
        rig.session.finalize()

        rig.session.confirmPurpose(id, PurposeDetail.CLIENT_VISIT, "A고객사 미팅", InputMode.VOICE, "판교 A고객사")

        val row = rig.dao.trip(id)!!
        assertTrue(rig.stateValue is SessionState.Idle)
        assertEquals(TripStatus.CONFIRMED, row.status)
        assertEquals("CLIENT_VISIT", row.purposeDetail)
        assertEquals("판교 A고객사", row.destinationLabel)
        assertEquals("VOICE", row.purposeInputMode)
    }

    @Test
    fun `운전자 없이 끝나면 배차 운전자를 자동으로 붙인다`() = runTest {
        val rig = Rig(this)
        val id = startedTrip(rig)
        rig.car.drive(10.0)
        runCurrent()

        rig.session.finalize()

        assertEquals("u-1", rig.dao.trip(id)!!.userId)
    }

    @Test
    fun `차가 움직이면 운행을 자동으로 시작한다`() = runTest {
        val rig = Rig(this)
        rig.car.shift(Gear.DRIVE)
        runCurrent()

        rig.car.drive(0.2)
        runCurrent()

        assertTrue(rig.stateValue is SessionState.Recording)
    }

    @Test
    fun `앱이 죽었다 켜졌는데 계기판이 그대로면 묻지 않고 확정한다`() = runTest {
        val rig = Rig(this) { dao, car ->
            car.km = 18_443.9
            kotlinx.coroutines.runBlocking { dao.upsert(recording(lastKm = 18_443.9)) }
        }

        assertEquals(23.4, (rig.stateValue as SessionState.PendingReview).distanceKm!!, 0.0)
    }

    @Test
    fun `앱이 죽은 동안 더 달렸으면 복구를 묻고 기록하기를 고르면 전체 거리로 끝낸다`() = runTest {
        val rig = Rig(this) { dao, car ->
            car.km = 18_448.9
            kotlinx.coroutines.runBlocking { dao.upsert(recording(lastKm = 18_443.9)) }
        }
        assertEquals(5.0, (rig.stateValue as SessionState.NeedsRecovery).extraKm!!, 0.0)

        rig.session.resolveRecovery(RecoveryChoice.RECORD)

        assertEquals(28.4, (rig.stateValue as SessionState.PendingReview).distanceKm!!, 0.0)
    }

    @Test
    fun `앱 없이 15 km 달렸으면 놓친 운행을 묻고 추가하면 15 km 운행이 생긴다`() = runTest {
        val rig = Rig(this) { dao, car ->
            car.km = 18_458.9
            dao.state = VehicleStateEntity(lastKnownOdometerKm = 18_443.9, lastKnownAt = 0)
        }
        assertEquals(15.0, rig.session.missedTrip.value!!.gapKm, 0.0)

        rig.session.acceptMissedTrip()

        val review = rig.stateValue as SessionState.PendingReview
        assertEquals(15.0, review.distanceKm!!, 0.0)
        assertTrue(rig.dao.trip(review.trip.tripId)!!.timeEstimated)
        assertNull(rig.session.missedTrip.value)
    }

    @Test
    fun `놓친 운행 배너를 둔 채 운전해도 추가하면 놓친 15 km 그대로다`() = runTest {
        val rig = Rig(this) { dao, car ->
            car.km = 18_458.9
            dao.state = VehicleStateEntity(lastKnownOdometerKm = 18_443.9, lastKnownAt = 0)
        }
        // 배너를 누르지 않고 출발 → 자동 기록 + 체크포인트가 lastKnown을 덮어쓴다
        val current = startedTrip(rig)
        advanceTimeBy(10_000)
        rig.car.drive(7.0)
        runCurrent()

        rig.session.acceptMissedTrip()

        val missed = rig.dao.trips.value.values.single { it.timeEstimated }
        assertEquals(15.0, missed.distanceKm!!, 0.0)
        assertEquals(18_458.9, missed.endOdometerKm!!, 0.0)
        // 지금 기록 중인 운행과 lastKnown은 건드리지 않는다
        assertTrue(rig.stateValue is SessionState.Recording)
        assertEquals(18_465.9, rig.dao.state!!.lastKnownOdometerKm!!, 0.0)
        assertEquals(TripStatus.RECORDING, rig.dao.trip(current)!!.status)
    }

    @Test
    fun `시작 복구 중에는 새 운행을 만들지 않는다`() = runTest {
        val rig = Rig(this) { dao, car ->
            dao.trips.value = mapOf("t-old" to recording(18_420.5))
            car.readable = false // 계기판 재조회(최대 60초) 중
        }

        val r = rig.session.startSession()

        assertTrue(r is StartResult.Failed)
        assertEquals(listOf("t-old"), rig.dao.trips.value.keys.toList())
        advanceTimeBy(61_000)
        assertEquals("t-old", (rig.stateValue as SessionState.Recording).trip.tripId)
    }

    @Test
    fun `주행 중 재시작으로 기록을 이어 가면 운행 시작 시각부터 정차로 보지 않는다`() = runTest {
        val rig = Rig(this) { dao, car ->
            dao.trips.value = mapOf("t-old" to recording(18_420.5).copy(startTime = -3_600_000))
            car.currentGear = Gear.DRIVE // 신호 대기 중 재시작
        }
        assertTrue(rig.stateValue is SessionState.Recording)

        advanceTimeBy(5_000)
        assertTrue(rig.stateValue is SessionState.Recording)

        advanceTimeBy(10_000)
        val stop = rig.stateValue as SessionState.StopCandidate
        assertTrue("정차 시작은 재개 이후여야 한다: ${stop.since}", stop.since >= 0)
    }

    @Test
    fun `계기판이 그대로여도 차속이 있으면 자동으로 기록을 시작한다`() = runTest {
        val rig = Rig(this)
        rig.car.shift(Gear.DRIVE)
        runCurrent()

        rig.car.speed(30.0)
        runCurrent()

        assertEquals(VehicleMotion.MOVING, rig.session.motion.value)
        assertTrue(rig.stateValue is SessionState.Recording)
    }

    @Test
    fun `같은 속도로 달리는 동안은 계기판이 1분간 안 바뀌어도 정차로 보지 않는다`() = runTest {
        val rig = Rig(this)
        startedTrip(rig)
        rig.car.speed(20.0) // 이벤트는 한 번만 온다 — 값이 바뀔 때만 오는 차속 신호
        runCurrent()

        advanceTimeBy(60_000)

        assertTrue(rig.stateValue is SessionState.Recording)
        assertEquals(VehicleMotion.MOVING, rig.session.motion.value)
    }

    @Test
    fun `차속이 0이 되고 10초 지나면 정차 대기로 들어간다`() = runTest {
        val rig = Rig(this)
        startedTrip(rig)
        rig.car.speed(40.0)
        runCurrent()
        advanceTimeBy(5_000)

        rig.car.speed(0.0)
        runCurrent()
        advanceTimeBy(DrivingJudge.MOVING_WINDOW_MS + 1_500)

        assertEquals(StopReason.NO_MOVEMENT, (rig.stateValue as SessionState.StopCandidate).reason)
    }

    @Test
    fun `정차 대기 중에 다시 속도가 나면 기록을 이어 간다`() = runTest {
        val rig = Rig(this)
        startedTrip(rig)
        advanceTimeBy(DrivingJudge.MOVING_WINDOW_MS + 1_500)
        assertTrue(rig.stateValue is SessionState.StopCandidate)

        rig.car.speed(15.0)
        runCurrent()

        assertTrue(rig.stateValue is SessionState.Recording)
    }

    @Test
    fun `차속은 거리에 들어가지 않는다 — 거리는 계기판 차이뿐`() = runTest {
        val rig = Rig(this)
        startedTrip(rig)
        rig.car.speed(130.0)
        runCurrent()
        advanceTimeBy(30_000)

        assertEquals(0.0, (rig.stateValue as SessionState.Recording).liveKm!!, 0.0)

        rig.car.drive(2.5)
        runCurrent()
        assertEquals(2.5, (rig.stateValue as SessionState.Recording).liveKm!!, 0.0)
    }

    @Test
    fun `기어 P 대기 중에는 차속이 있어도 재개하지 않는다`() = runTest {
        val rig = Rig(this)
        startedTrip(rig)
        rig.car.shift(Gear.PARK)
        runCurrent()

        rig.car.speed(10.0)
        runCurrent()

        assertEquals(StopReason.GEAR_P, (rig.stateValue as SessionState.StopCandidate).reason)
    }

    @Test
    fun `달리는 중에 기록 종료를 바로 하면 끝나고, 계속 달려도 새 운행을 시작하지 않는다`() = runTest {
        val rig = Rig(this)
        val id = startedTrip(rig)
        rig.car.speed(40.0)
        runCurrent()
        rig.car.drive(1.0)
        runCurrent()

        rig.session.finalize()
        rig.car.speed(45.0)
        rig.car.drive(0.5)
        runCurrent()
        advanceTimeBy(5_000)

        assertTrue(rig.stateValue is SessionState.PendingReview)
        assertEquals(id, (rig.stateValue as SessionState.PendingReview).trip.tripId)
        assertEquals(1, rig.dao.trips.value.size)
    }

    @Test
    fun `기록 종료로 멈춘 자동 시작은 신호 대기로는 풀리지 않고 주차해야 풀린다`() = runTest {
        val rig = Rig(this)
        startedTrip(rig)
        rig.car.speed(40.0)
        runCurrent()
        rig.car.drive(1.0) // 짧은 운행 버리기(1분 미만·0.3 km 미만)에 걸리지 않게
        runCurrent()
        rig.session.finalize()

        rig.car.speed(0.0)
        runCurrent()
        advanceTimeBy(DrivingJudge.MOVING_WINDOW_MS + 1_500) // 신호 대기
        assertEquals(VehicleMotion.STOPPED, rig.session.motion.value)
        rig.car.speed(30.0)
        runCurrent()
        assertTrue(rig.stateValue is SessionState.PendingReview) // 끝낸 대로 끝나 있다
        assertEquals(1, rig.dao.trips.value.size)

        rig.car.speed(0.0)
        rig.car.shift(Gear.PARK) // 주차
        runCurrent()
        assertEquals(VehicleMotion.PARKED, rig.session.motion.value)
        rig.car.shift(Gear.DRIVE)
        rig.car.speed(20.0)
        runCurrent()

        assertTrue(rig.stateValue is SessionState.Recording) // 다음 출발은 새 운행
        assertEquals(2, rig.dao.trips.value.size)
    }

    @Test
    fun `정차 중에 기록 종료하면 출발해도 새 운행을 시작하지 않는다 — 다음 주차까지`() = runTest {
        val rig = Rig(this)
        startedTrip(rig)
        rig.car.speed(40.0)
        runCurrent()
        rig.car.drive(1.0) // 짧은 운행 버리기(1분 미만·0.3 km 미만)에 걸리지 않게
        runCurrent()
        rig.car.speed(0.0)
        runCurrent()
        advanceTimeBy(DrivingJudge.MOVING_WINDOW_MS + 1_500)
        assertEquals(VehicleMotion.STOPPED, rig.session.motion.value)

        rig.session.finalize()
        rig.car.speed(30.0)
        rig.car.drive(0.5)
        runCurrent()

        assertTrue(rig.stateValue is SessionState.PendingReview)
        assertEquals(1, rig.dao.trips.value.size)
    }

    @Test
    fun `사람이 끝낸 종료 대기는 차가 움직여도 되돌리지 않는다`() = runTest {
        val rig = Rig(this)
        startedTrip(rig)
        rig.session.requestStop()

        rig.car.speed(30.0)
        rig.car.drive(0.2)
        runCurrent()
        assertEquals(StopReason.USER_REQUEST, (rig.stateValue as SessionState.StopCandidate).reason)
        advanceTimeBy(5_500)

        assertTrue(rig.stateValue is SessionState.PendingReview) // 그대로 끝나고
        assertEquals(1, rig.dao.trips.value.size) // 계속 달려도 새 운행은 다음 주차 뒤
    }

    @Test
    fun `주차 중에 끝내면 자동 시작을 멈추지 않는다 — 다음 출발은 새 운행`() = runTest {
        val rig = Rig(this)
        startedTrip(rig)
        rig.car.shift(Gear.PARK)
        runCurrent()
        rig.session.finalize()

        rig.car.shift(Gear.DRIVE)
        rig.car.speed(20.0)
        runCurrent()

        assertTrue(rig.stateValue is SessionState.Recording)
    }

    @Test
    fun `놓친 운행을 무시하면 같은 계기판 값으로는 다시 묻지 않는다`() = runTest {
        val rig = Rig(this) { dao, car ->
            car.km = 18_458.9
            dao.state = VehicleStateEntity(lastKnownOdometerKm = 18_443.9, lastKnownAt = 0)
        }

        rig.session.ignoreMissedTrip()

        assertNull(rig.session.missedTrip.value)
        assertEquals(18_458.9, rig.dao.state!!.ignoredOdometerKm!!, 0.0)
    }

    @Test
    fun `운전자를 고르면 다음 운행에 쓰이고 최근 운전자로 남는다`() = runTest {
        val rig = Rig(this)

        rig.session.selectDriver("u-2")
        val id = startedTrip(rig)

        assertEquals("u-2", rig.dao.trip(id)!!.userId)
        assertEquals("u-2", rig.dao.state!!.currentDriverUserId)
        assertTrue("u-2" in rig.dao.recentUsers)
    }

    private companion object {
        fun recording(lastKm: Double) = TripEntity(
            tripId = "t-old",
            vehicleId = "car-1",
            userId = "u-1",
            startTime = 0,
            startOdometerKm = 18_420.5,
            lastOdometerKm = lastKm,
            lastSeenAt = 1_800_000,
        )
    }
}
