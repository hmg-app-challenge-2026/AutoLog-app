package kr.htaxlog.vehicle.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room 스키마·쿼리 (실제 SQLite, 에뮬레이터). 계측 테스트라 이름은 영어 camelCase. */
@RunWith(AndroidJUnit4::class)
class TripDaoTest {
    private lateinit var db: HTaxLogDb
    private lateinit var dao: TripDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, HTaxLogDb::class.java).build()
        dao = db.tripDao()
    }

    @After
    fun tearDown() = db.close()

    private fun trip(id: String, start: Long, status: TripStatus = TripStatus.RECORDING) = TripEntity(
        tripId = id,
        vehicleId = "car-1",
        userId = "u-1",
        startTime = start,
        startOdometerKm = 18_420.5,
        status = status,
    )

    /** 시작 즉시 커밋한 운행을 앱 재시작 후 찾는다 (F-10). */
    @Test
    fun recordingTripIsFoundAfterRestart(): Unit = runBlocking {
        dao.upsert(trip("t-1", 1_000))

        assertEquals("t-1", dao.findRecording()?.tripId)
    }

    /** 체크포인트는 기록 중인 운행의 마지막 계기판 값만 바꾼다. */
    @Test
    fun checkpointUpdatesOnlyRecordingTrip(): Unit = runBlocking {
        dao.upsert(trip("t-1", 1_000))
        dao.upsert(trip("t-2", 500, TripStatus.CONFIRMED))

        val updated = dao.checkpoint("t-1", 18_430.1, 11_000) + dao.checkpoint("t-2", 1.0, 11_000)

        assertEquals(1, updated)
        assertEquals(18_430.1, dao.trip("t-1")!!.lastOdometerKm!!, 0.0)
        assertNull(dao.trip("t-2")!!.lastOdometerKm)
    }

    /** 거리 원본은 Double 그대로 저장된다(정밀도 손실 없음). */
    @Test
    fun odometerValuesRoundTripExactly(): Unit = runBlocking {
        dao.upsert(trip("t-1", 1_000).copy(endOdometerKm = 18_443.9, distanceKm = 18_443.9 - 18_420.5))

        val t = dao.trip("t-1")!!

        assertEquals("23.4", "%.1f".format(t.distanceKm))
        assertEquals(18_443.9, t.endOdometerKm!!, 0.0)
    }

    /** 이력은 끝난 운행만, 최신 순. 버린 운행은 빠진다. */
    @Test
    fun finishedTripsExcludeRecordingAndDiscarded(): Unit = runBlocking {
        dao.upsert(trip("rec", 3_000))
        dao.upsert(trip("old", 1_000, TripStatus.CONFIRMED))
        dao.upsert(trip("new", 2_000, TripStatus.PENDING_REVIEW))
        dao.upsert(trip("gone", 2_500, TripStatus.DISCARDED))

        val rows = dao.observeFinishedBetween(0, 10_000).first()

        assertEquals(listOf("new", "old"), rows.map { it.tripId })
        assertEquals(listOf("new"), dao.observePendingReviews().first().map { it.tripId })
    }

    /** 차량 상태는 1행이고 덮어쓴다. */
    @Test
    fun vehicleStateIsSingleRow(): Unit = runBlocking {
        dao.saveVehicleState(VehicleStateEntity(lastKnownOdometerKm = 100.0))
        dao.saveVehicleState(VehicleStateEntity(lastKnownOdometerKm = 115.0, ignoredOdometerKm = 115.0))

        assertEquals(115.0, dao.vehicleState()!!.lastKnownOdometerKm!!, 0.0)
    }

    /** 정차 기록은 운행별로 시간 순. */
    @Test
    fun stopsAreListedPerTrip(): Unit = runBlocking {
        dao.upsert(trip("t-1", 1_000))
        dao.insertStop(
            StopEntity(tripId = "t-1", startTime = 5_000, endTime = 6_000, odometerKm = 18_425.0, reason = "GEAR_P"),
        )
        dao.insertStop(StopEntity(tripId = "t-1", startTime = 2_000, endTime = 3_000, odometerKm = 18_421.0))

        assertEquals(listOf(2_000L, 5_000L), dao.stops("t-1").map { it.startTime })
    }
}
