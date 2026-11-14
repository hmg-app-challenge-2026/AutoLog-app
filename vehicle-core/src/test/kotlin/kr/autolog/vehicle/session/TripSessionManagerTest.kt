package kr.autolog.vehicle.session

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kr.autolog.shared.PlaceKind
import kr.autolog.vehicle.core.AddressSource
import kr.autolog.vehicle.core.DestinationInfo
import kr.autolog.vehicle.core.GeocodeResult
import kr.autolog.vehicle.core.InputMode
import kr.autolog.vehicle.core.NaviCurrentLocation
import kr.autolog.vehicle.core.PlaceInfo
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
import kr.autolog.vehicle.fake.FakeGeocode
import kr.autolog.vehicle.fake.FakePurposeEngine
import kr.autolog.vehicle.vehicle.Gear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSessionManagerTest {
    private class Rig(
        scope: TestScope,
        val geo: FakeGeocode? = null,
        setup: (InMemoryTripDao, FakeVehicle) -> Unit = { _, _ -> },
    ) {
        val clock = scope.virtualClock()
        val car = FakeVehicle(clock)
        val dao = InMemoryTripDao()

        // 기본은 내비 목적지(판교 거래처) 바로 옆 — P를 넣으면 지금처럼 끝난다. 목적지 전 정차는 setFix로 옮긴다
        val ctx = FakeContext(fix = FakeContext.SAMPLE_FIX.copy(lat = 37.4012, lng = 127.1081))
        val engine = FakePurposeEngine()
        var ids = 0
        val session: TripSessionManager

        init {
            setup(dao, car)
            session =
                TripSessionManager(
                    car,
                    dao,
                    ctx,
                    engine,
                    scope.backgroundScope,
                    clock,
                    { "car-1" },
                    { "t-${++ids}" },
                    geocode = geo,
                )
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
    fun `운행 시작 시 내비 현재 위치 주소를 출발지 주소로 저장한다 — 서버가 없으면 그대로 남는다`() = runTest {
        val rig = Rig(this)
        rig.ctx.setNaviLocation(NaviCurrentLocation(37.4, 127.1, " 서울특별시 강남구 테헤란로 1 "))

        val row = rig.dao.trip(startedTrip(rig))!!

        assertNull(row.originLabel)
        assertEquals("서울 강남구 테헤란로 1", row.originAddress)
        assertEquals(AddressSource.NAVI_HELPER, row.originAddressSource)
        assertEquals(37.4, row.originLat!!, 0.0)
        assertEquals(127.1, row.originLng!!, 0.0)
        assertEquals(PlaceSource.NAVI_CURRENT, row.originSource)
        assertEquals("서울 강남구 테헤란로 1", (rig.stateValue as SessionState.Recording).trip.originAddress)
    }

    @Test
    fun `운행을 시작하면 서버 주소와 건물 이름이 곧 출발지에 붙는다`() = runTest {
        val rig = Rig(this, geo = FakeGeocode())
        rig.ctx.setNaviLocation(NaviCurrentLocation(37.566, 126.93, "서울특별시 서대문구 연희로 11-1"))

        val id = startedTrip(rig)
        runCurrent()

        val row = rig.dao.trip(id)!!
        assertEquals("서울 서대문구 연희동 연희로 11-1", row.originAddress)
        assertEquals(AddressSource.KAKAO_RELAY, row.originAddressSource)
        assertEquals("연희빌딩", row.originLabel)
        val live = (rig.stateValue as SessionState.Recording).trip
        assertEquals("연희빌딩", live.originLabel)
        assertEquals("서울 서대문구 연희동 연희로 11-1", live.originAddress)
    }

    @Test
    fun `등록 장소 안에서 출발하면 장소 이름이 건물 이름보다 먼저다`() = runTest {
        val rig = Rig(this, geo = FakeGeocode())
        rig.engine.place = PlaceInfo("office", "본사", PlaceKind.OFFICE, 37.395, 127.111)

        val id = startedTrip(rig)
        runCurrent()

        assertEquals("본사", rig.dao.trip(id)!!.originLabel)
    }

    @Test
    fun `목적지 없이 끝나면 지금 위치로 도착지 주소를 채운다 — 서버가 꺼져 있으면 내비 주소`() = runTest {
        val geo = FakeGeocode(serverDown = true, naviAddress = "경기 성남시 분당구 정자일로 1")
        val rig = Rig(this, geo = geo)
        rig.ctx.setDestination(null)
        val id = startedTrip(rig)
        rig.car.drive(5.0)
        runCurrent()

        rig.session.finalize()
        runCurrent()

        val row = rig.dao.trip(id)!!
        assertNull(row.destinationLabel)
        assertEquals("경기 성남시 분당구 정자일로 1", row.destinationAddress)
        assertEquals(AddressSource.NAVI_HELPER, row.destinationAddressSource)
        assertEquals(rig.ctx.latestFix.value!!.lat, row.destinationLat!!, 0.0)
        assertEquals(PlaceSource.FUSED_LOCATION, row.destinationSource)
        assertEquals("경기 성남시 분당구 정자일로 1", (rig.stateValue as SessionState.PendingReview).trip.destinationAddress)
    }

    @Test
    fun `내비 목적지 근처에서 끝나면 목적지 이름을 남기고 주소는 서버 주소로`() = runTest {
        val dest = FakeContext.SAMPLE_DESTINATION
        val geo = FakeGeocode(result = GeocodeResult("경기 성남시 분당구 삼평동 판교역로 1", null, AddressSource.KAKAO_RELAY))
        val rig = Rig(this, geo = geo)
        val id = startedTrip(rig)
        rig.car.drive(5.0)
        runCurrent()
        rig.ctx.setNaviLocation(NaviCurrentLocation(dest.lat + 0.001, dest.lng, null))

        rig.session.finalize()
        runCurrent()

        val row = rig.dao.trip(id)!!
        assertEquals("판교 테크노밸리 거래처", row.destinationLabel)
        assertEquals("경기 성남시 분당구 삼평동 판교역로 1", row.destinationAddress)
        assertEquals(PlaceSource.NAVI_CURRENT, row.destinationSource)
    }

    @Test
    fun `내비 목적지와 먼 곳에서 끝나면 목적지 이름을 버리고 실제 위치를 쓴다`() = runTest {
        val rig =
            Rig(
                this,
                geo = FakeGeocode(result = GeocodeResult("서울 중구 태평로1가 세종대로 110", "서울시청", AddressSource.KAKAO_RELAY)),
            )
        val id = startedTrip(rig)
        rig.car.drive(30.0)
        runCurrent()
        rig.ctx.setNaviLocation(NaviCurrentLocation(37.5663, 126.9779, null))

        rig.session.finalize()
        runCurrent()

        val row = rig.dao.trip(id)!!
        assertEquals("서울시청", row.destinationLabel)
        assertEquals("서울 중구 태평로1가 세종대로 110", row.destinationAddress)
        assertEquals(37.5663, row.destinationLat!!, 0.0)
    }

    @Test
    fun `종료 대기 화면부터 저장될 도착지를 보이고 끝나면 같은 도착지로 저장한다`() = runTest {
        val rig =
            Rig(
                this,
                geo = FakeGeocode(result = GeocodeResult("서울 중구 태평로1가 세종대로 110", "서울시청", AddressSource.KAKAO_RELAY)),
            )
        val id = startedTrip(rig)
        rig.car.drive(30.0)
        runCurrent()
        rig.ctx.setNaviLocation(NaviCurrentLocation(37.5663, 126.9779, null))

        rig.car.shift(Gear.PARK)
        runCurrent()
        val waiting = rig.stateValue as SessionState.StopCandidate
        advanceTimeBy(TripTiming.T_GEAR_P_SEC * 1_000 + 1_000)

        // 종료 대기(결과 화면 [기록 이어가기] 단계)에 이미 실제 도착지 — 내비 목적지(판교)가 아니다
        assertEquals("서울시청", waiting.trip.destinationLabel)
        assertEquals("서울 중구 태평로1가 세종대로 110", waiting.trip.destinationAddress)
        val review = rig.stateValue as SessionState.PendingReview
        assertEquals("서울시청", review.trip.destinationLabel)
        val row = rig.dao.trip(id)!!
        assertEquals("서울시청", row.destinationLabel)
        assertEquals("서울 중구 태평로1가 세종대로 110", row.destinationAddress)
    }

    @Test
    fun `종료 대기에서 기록을 이어가면 미리 보인 도착지를 버리고 내비 목적지로 돌아간다`() = runTest {
        val rig =
            Rig(
                this,
                geo = FakeGeocode(result = GeocodeResult("서울 중구 태평로1가 세종대로 110", "서울시청", AddressSource.KAKAO_RELAY)),
            )
        val id = startedTrip(rig)
        rig.car.drive(5.0)
        runCurrent()
        rig.ctx.setNaviLocation(NaviCurrentLocation(37.5663, 126.9779, null))
        rig.car.shift(Gear.PARK)
        runCurrent()
        assertEquals("서울시청", (rig.stateValue as SessionState.StopCandidate).trip.destinationLabel)

        rig.car.shift(Gear.DRIVE)
        runCurrent()

        val recording = rig.stateValue as SessionState.Recording
        assertEquals("판교 테크노밸리 거래처", recording.trip.destinationLabel)
        assertEquals("판교 테크노밸리 거래처", rig.dao.trip(id)!!.destinationLabel)
    }

    @Test
    fun `경로가 없을 때 내비가 보내는 빈 목적지(이름 없음, 0·0)는 목적지로 저장하지 않는다`() = runTest {
        val rig = Rig(this)
        rig.ctx.setDestination(DestinationInfo("", null, 0.0, 0.0))

        val id = startedTrip(rig)
        rig.ctx.setDestination(DestinationInfo(" ", null, 0.0, 0.0))
        runCurrent()

        val row = rig.dao.trip(id)!!
        assertNull(row.destinationLabel)
        assertNull(row.destinationLat)
        assertNull(row.destinationSource)
    }

    @Test
    fun `사람이 고른 도착지 이름은 늦게 온 주소가 덮지 않는다`() = runTest {
        val rig = Rig(this, geo = FakeGeocode())
        rig.ctx.setDestination(null)
        val id = startedTrip(rig)
        rig.car.drive(5.0)
        runCurrent()
        rig.session.finalize()
        rig.session.confirmPurpose(id, PurposeDetail.CLIENT_VISIT, null, InputMode.TAP, "판교 A고객사")

        runCurrent()

        val row = rig.dao.trip(id)!!
        assertEquals("판교 A고객사", row.destinationLabel)
        assertEquals(PlaceSource.USER_SELECTED, row.destinationSource)
        assertEquals(FakeGeocode.SAMPLE.address, row.destinationAddress)
    }

    @Test
    fun `서버가 꺼졌던 운행은 동기화 때 서버 주소로 다시 채운다 — 지난 좌표라 내비는 쓰지 않는다`() = runTest {
        val geo = FakeGeocode(serverDown = true, naviAddress = "내비 주소")
        val rig = Rig(this, geo = geo)
        rig.ctx.setDestination(null)
        val id = startedTrip(rig)
        rig.car.drive(5.0)
        runCurrent()
        rig.session.finalize()
        runCurrent()
        assertEquals(AddressSource.NAVI_HELPER, rig.dao.trip(id)!!.destinationAddressSource)

        geo.serverDown = false
        val filled = rig.session.fillMissingAddresses()

        val row = rig.dao.trip(id)!!
        assertEquals(1, filled)
        assertEquals(AddressSource.KAKAO_RELAY, row.originAddressSource)
        assertEquals(AddressSource.KAKAO_RELAY, row.destinationAddressSource)
        assertEquals(FakeGeocode.SAMPLE.address, row.destinationAddress)
        assertEquals(0, rig.session.fillMissingAddresses())
    }

    @Test
    fun `서버가 계속 답하지 않으면 나중 채우기를 멈춘다`() = runTest {
        val geo = FakeGeocode(serverDown = true)
        val rig = Rig(this, geo = geo) { dao, _ ->
            dao.trips.value = (0 until 5).associate { i ->
                "old-$i" to TripEntity(
                    tripId = "old-$i",
                    vehicleId = "car-1",
                    userId = null,
                    startTime = 1_000L + i,
                    startOdometerKm = 1.0,
                    originLat = 37.0,
                    originLng = 127.0,
                    status = TripStatus.CONFIRMED,
                )
            }
        }
        geo.calls.clear()

        assertEquals(0, rig.session.fillMissingAddresses())
        assertEquals(TripSessionManager.BACKFILL_MAX_MISSES, geo.calls.size)
    }

    @Test
    fun `내비 현재 위치가 없으면 위치 SDK 좌표로 출발지를 저장한다`() = runTest {
        val rig = Rig(this)

        val row = rig.dao.trip(startedTrip(rig))!!

        assertNull(row.originLabel)
        assertEquals(rig.ctx.latestFix.value!!.lat, row.originLat!!, 0.0)
        assertEquals(rig.ctx.latestFix.value!!.lng, row.originLng!!, 0.0)
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
    fun `내비 목적지에서 500m 밖에서 P를 넣으면 기록을 끊지 않는다`() = runTest {
        val rig = Rig(this)
        val id = startedTrip(rig)
        rig.car.drive(5.0)
        runCurrent()
        rig.ctx.setFix(FakeContext.SAMPLE_FIX.copy(lat = 37.380, lng = 127.108)) // 목적지에서 약 2.3km

        rig.car.shift(Gear.PARK)
        advanceTimeBy(TripTiming.T_GEAR_P_SEC * 1_000 + 60_000)

        assertTrue(rig.stateValue is SessionState.Recording)
        assertEquals(TripStatus.RECORDING, rig.dao.trip(id)!!.status)
    }

    @Test
    fun `목적지 전 P로 이어 가다 목적지 근처에서 P를 넣으면 지금처럼 끝난다`() = runTest {
        val rig = Rig(this)
        startedTrip(rig)
        rig.car.drive(5.0)
        runCurrent()
        val near = rig.ctx.latestFix.value!!
        rig.ctx.setFix(FakeContext.SAMPLE_FIX.copy(lat = 37.380, lng = 127.108))
        rig.car.shift(Gear.PARK)
        runCurrent()
        rig.car.shift(Gear.DRIVE)
        rig.car.drive(3.0)
        rig.ctx.setFix(near)

        rig.car.shift(Gear.PARK)
        advanceTimeBy(TripTiming.T_GEAR_P_SEC * 1_000 + 1_000)

        val review = rig.stateValue as SessionState.PendingReview
        assertEquals(8.0, review.distanceKm!!, 0.001)
    }

    @Test
    fun `내비 목적지가 없으면 어디서 P를 넣든 지금처럼 끝난다`() = runTest {
        val rig = Rig(this)
        rig.ctx.setDestination(null)
        startedTrip(rig)
        rig.car.drive(5.0)
        runCurrent()
        rig.ctx.setFix(FakeContext.SAMPLE_FIX.copy(lat = 37.380, lng = 127.108))

        rig.car.shift(Gear.PARK)
        advanceTimeBy(TripTiming.T_GEAR_P_SEC * 1_000 + 1_000)

        assertTrue(rig.stateValue is SessionState.PendingReview)
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
    fun `끝난 운행을 지우면 목록에서 빠지는 DISCARDED로 남고 대기로 — 목적 확정이 와도 되살리지 않는다`() = runTest {
        val rig = Rig(this)
        val id = startedTrip(rig)
        rig.car.drive(23.4)
        runCurrent()
        rig.car.shift(Gear.PARK)
        runCurrent()
        rig.session.finalize()

        rig.session.discardTrip(id)
        rig.session.confirmPurpose(id, PurposeDetail.FIELD_WORK, null, InputMode.TAP)

        assertTrue(rig.stateValue is SessionState.Idle)
        assertEquals(TripStatus.DISCARDED, rig.dao.trip(id)!!.status)
        assertNull(rig.dao.trip(id)!!.purposeDetail)
    }

    @Test
    fun `종료 대기 중에 지우면 먼저 끝내고 지운다 — 계기판 마지막 값은 남아 놓친 운행으로 묻지 않는다`() = runTest {
        val rig = Rig(this)
        val id = startedTrip(rig)
        rig.car.drive(5.0)
        runCurrent()
        rig.session.requestStop()
        runCurrent()
        assertTrue(rig.stateValue is SessionState.StopCandidate)

        rig.session.discardTrip(id)

        assertTrue(rig.stateValue is SessionState.Idle)
        assertEquals(TripStatus.DISCARDED, rig.dao.trip(id)!!.status)
        assertEquals(18_425.5, rig.dao.state!!.lastKnownOdometerKm!!, 0.0)
        assertNull(rig.session.missedTrip.value)
    }

    @Test
    fun `기록 중인 운행은 지우지 않는다`() = runTest {
        val rig = Rig(this)
        val id = startedTrip(rig)

        rig.session.discardTrip(id)

        assertTrue(rig.stateValue is SessionState.Recording)
        assertEquals(TripStatus.RECORDING, rig.dao.trip(id)!!.status)
    }

    @Test
    fun `끝난 운행의 거리를 고치면 수정값만 따로 남고 계기판 원본은 그대로다`() = runTest {
        val rig = Rig(this)
        val id = startedTrip(rig)
        rig.car.drive(23.4)
        runCurrent()
        rig.session.finalize()
        val before = rig.dao.trip(id)!!

        val saved = rig.session.editDistance(id, 25.0)

        val row = rig.dao.trip(id)!!
        assertTrue(saved)
        assertEquals(25.0, row.editedDistanceKm!!, 0.0)
        assertEquals(23.4, row.distanceKm!!, 0.0)
        assertEquals(DistanceSource.VEHICLE_ODOMETER, row.distanceSource)
        assertEquals(before.rawSnapshotJson, row.rawSnapshotJson)
        assertEquals(before.revision + 1, row.revision)
        assertEquals(25.0, (rig.stateValue as SessionState.PendingReview).distanceKm!!, 0.0)
    }

    @Test
    fun `계기판 값과 같게 되돌리면 수정 표시를 지운다`() = runTest {
        val rig = Rig(this)
        val id = startedTrip(rig)
        rig.car.drive(23.4)
        runCurrent()
        rig.session.finalize()
        rig.session.editDistance(id, 25.0)

        rig.session.editDistance(id, 23.4)

        assertNull(rig.dao.trip(id)!!.editedDistanceKm)
    }

    @Test
    fun `거리 미확정 운행도 사람이 거리를 채울 수 있다`() = runTest {
        val rig = Rig(this)
        rig.car.readable = false
        val id = startedTrip(rig)
        rig.session.requestStop()
        rig.session.finalize()

        assertTrue(rig.session.editDistance(id, 12.3))

        val row = rig.dao.trip(id)!!
        assertNull(row.distanceKm)
        assertEquals(DistanceSource.UNAVAILABLE, row.distanceSource)
        assertEquals(12.3, row.editedDistanceKm!!, 0.0)
    }

    @Test
    fun `0 이하·1500 km 초과·기록 중인 운행의 거리는 고치지 않는다`() = runTest {
        val rig = Rig(this)
        val id = startedTrip(rig)

        assertTrue(!rig.session.editDistance(id, 10.0))
        rig.car.drive(23.4)
        runCurrent()
        rig.session.finalize()
        assertTrue(!rig.session.editDistance(id, 0.0))
        assertTrue(!rig.session.editDistance(id, 1_500.1))
        assertTrue(!rig.session.editDistance(id, Double.NaN))
        assertNull(rig.dao.trip(id)!!.editedDistanceKm)
        assertTrue(rig.session.editDistance(id, 1_500.0))
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
