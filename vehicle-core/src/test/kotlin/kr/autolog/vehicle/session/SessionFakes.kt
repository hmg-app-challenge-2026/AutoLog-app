package kr.autolog.vehicle.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kr.autolog.vehicle.core.AddressSource
import kr.autolog.vehicle.data.PlaceSource
import kr.autolog.vehicle.data.RecentUserEntity
import kr.autolog.vehicle.data.StopEntity
import kr.autolog.vehicle.data.TripDao
import kr.autolog.vehicle.data.TripEntity
import kr.autolog.vehicle.data.TripStatus
import kr.autolog.vehicle.data.VehicleStateEntity
import kr.autolog.vehicle.vehicle.Gear
import kr.autolog.vehicle.vehicle.OdometerReading
import kr.autolog.vehicle.vehicle.VehicleSignals
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** 테스트 가상 시간을 따르는 시계. */
fun TestScope.virtualClock(): Clock = object : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = Instant.ofEpochMilli(testScheduler.currentTime)
}

/** 계기판·기어를 손으로 움직이는 가짜 차. [readable] = false면 조회가 실패한다. */
class FakeVehicle(private val clock: Clock, var km: Double = 18_420.5, var currentGear: Gear? = Gear.PARK) :
    VehicleSignals {
    var readable = true
    private val odo = MutableSharedFlow<OdometerReading>(extraBufferCapacity = 64)
    private val gears = MutableSharedFlow<Gear>(extraBufferCapacity = 64)

    override suspend fun readOdometer(): OdometerReading? = if (readable) OdometerReading(km, clock.millis()) else null

    override val odometer: Flow<OdometerReading> = odo

    override suspend fun readGear(): Gear? = currentGear

    override val gear: Flow<Gear> = gears

    private val ignition = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    override val ignitionOff: Flow<Unit> = ignition

    fun turnOff() {
        ignition.tryEmit(Unit)
    }

    private val speeds = MutableSharedFlow<Double>(extraBufferCapacity = 64)
    override val speedKph: Flow<Double> = speeds

    /** 차속만 바꾼다. 계기판은 그대로(에뮬레이터 콘솔과 같은 상황). */
    fun speed(kph: Double) {
        speeds.tryEmit(kph)
    }

    fun drive(km: Double) {
        this.km = kr.autolog.vehicle.session.DistancePolicy.difference(-km, this.km) // this.km + km, 10진수
        odo.tryEmit(OdometerReading(this.km, clock.millis()))
    }

    fun shift(g: Gear) {
        currentGear = g
        gears.tryEmit(g)
    }
}

/** Room 대신 쓰는 메모리 DAO. 쿼리 의미는 TripDao와 같게 맞춘다. */
class InMemoryTripDao : TripDao {
    val trips = MutableStateFlow<Map<String, TripEntity>>(emptyMap())
    val stops = mutableListOf<StopEntity>()
    var state: VehicleStateEntity? = null
    val recentUsers = mutableMapOf<String, Long>()

    override suspend fun upsert(trip: TripEntity) {
        trips.value = trips.value + (trip.tripId to trip)
    }

    override suspend fun trip(tripId: String) = trips.value[tripId]

    override suspend fun findRecording() =
        trips.value.values.filter { it.status == TripStatus.RECORDING }.maxByOrNull { it.startTime }

    override suspend fun checkpoint(tripId: String, km: Double, at: Long): Int {
        val t = trips.value[tripId]?.takeIf { it.status == TripStatus.RECORDING } ?: return 0
        upsert(t.copy(lastOdometerKm = km, lastSeenAt = at))
        return 1
    }

    override suspend fun tripsBetween(from: Long, to: Long) = trips.value.values.filter {
        it.startTime in from until to && it.status != TripStatus.DISCARDED
    }.sortedBy { it.startTime }

    override fun observeFinishedBetween(from: Long, to: Long) = trips.map { m ->
        m.values.filter {
            it.startTime in from until to && it.status !in DONE_EXCLUDED
        }.sortedByDescending { it.startTime }
    }

    override fun observeRecent(limit: Int) = trips.map { m ->
        m.values.filter { it.status !in DONE_EXCLUDED }.sortedByDescending { it.startTime }.take(limit)
    }

    override fun observePendingReviews() = trips.map { m ->
        m.values.filter { it.status == TripStatus.PENDING_REVIEW || it.status == TripStatus.NEEDS_RECOVERY }
            .sortedByDescending { it.startTime }
    }

    override suspend fun setOriginPlace(tripId: String, label: String?, address: String?, source: AddressSource?): Int {
        val t = trips.value[tripId] ?: return 0
        upsert(
            t.copy(
                originLabel = label,
                originAddress = address,
                originAddressSource = source,
                revision =
                t.revision + 1,
            ),
        )
        return 1
    }

    override suspend fun setDestinationPlace(
        tripId: String,
        label: String?,
        lat: Double?,
        lng: Double?,
        placeSource: PlaceSource?,
        address: String?,
        source: AddressSource?,
    ): Int {
        val t = trips.value[tripId] ?: return 0
        upsert(
            t.copy(
                destinationLabel = label,
                destinationLat = lat,
                destinationLng = lng,
                destinationSource = placeSource,
                destinationAddress = address,
                destinationAddressSource = source,
                revision = t.revision + 1,
            ),
        )
        return 1
    }

    override suspend fun tripsNeedingAddress(since: Long, limit: Int) = trips.value.values
        .filter { t ->
            t.startTime >= since &&
                t.status != TripStatus.RECORDING &&
                t.status != TripStatus.DISCARDED &&
                (
                    (t.originLat != null && t.originAddressSource != AddressSource.KAKAO_RELAY) ||
                        (t.destinationLat != null && t.destinationAddressSource != AddressSource.KAKAO_RELAY)
                    )
        }.sortedByDescending { it.startTime }
        .take(limit)

    override suspend fun setReviewFlags(tripId: String, flags: String?) {
        trips.value[tripId]?.let { upsert(it.copy(reviewFlags = flags)) }
    }

    override suspend fun insertStop(stop: StopEntity): Long {
        stops += stop.copy(id = stops.size + 1L)
        return stops.size.toLong()
    }

    override suspend fun stops(tripId: String) = stops.filter { it.tripId == tripId }.sortedBy { it.startTime }

    override suspend fun vehicleState() = state

    override suspend fun saveVehicleState(state: VehicleStateEntity) {
        this.state = state
    }

    override suspend fun upsertRecentUser(user: RecentUserEntity) {
        recentUsers[user.userId] = user.lastUsedAt
    }

    private companion object {
        val DONE_EXCLUDED = setOf(TripStatus.RECORDING, TripStatus.DISCARDED)
    }
}
