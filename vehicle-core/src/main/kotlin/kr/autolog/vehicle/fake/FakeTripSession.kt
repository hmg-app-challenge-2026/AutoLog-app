package kr.autolog.vehicle.fake

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.autolog.vehicle.core.DriverSource
import kr.autolog.vehicle.core.InputMode
import kr.autolog.vehicle.core.LiveTrip
import kr.autolog.vehicle.core.MissedTrip
import kr.autolog.vehicle.core.PurposeDetail
import kr.autolog.vehicle.core.RecoveryChoice
import kr.autolog.vehicle.core.SessionState
import kr.autolog.vehicle.core.StartResult
import kr.autolog.vehicle.core.StopReason
import kr.autolog.vehicle.core.TripRow
import kr.autolog.vehicle.core.TripSessionApi
import kr.autolog.vehicle.core.TripTiming
import kr.autolog.vehicle.core.VehicleMotion
import java.time.Clock

/**
 * [TripSessionApi] 가짜 (프론트 초안에서 옮김). 데모 패널·테스트가 [setMotion]·[addKm]으로 차량을 흉내 낸다.
 * 규칙: 운행 중 PARKED → GEAR_P 카운트다운 / 카운트다운 중 MOVING → 계속 / 운행 없을 때 MOVING → 자동 시작(F-02).
 */
class FakeTripSession(
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    initialOdometerKm: Double? = 18_420.5,
    private val gearPWaitMs: Long = TripTiming.T_GEAR_P_SEC * 1_000,
    /** 끝난 운행을 남길 저장소. 실제 구현에서는 세션과 Room이 같은 원본을 쓴다. */
    private val store: FakeLocalStore? = null,
) : TripSessionApi {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    private val _motion = MutableStateFlow<VehicleMotion?>(VehicleMotion.PARKED)
    private val _totalOdometerKm = MutableStateFlow(initialOdometerKm)
    private val _missedTrip = MutableStateFlow<MissedTrip?>(null)
    private val _currentDriver = MutableStateFlow<String?>(null)
    private var autoEndJob: Job? = null

    /** 달리는 중에 사람이 끝냈으면 다음 정차·주차까지 자동 시작하지 않는다(실구현 TripSessionManager와 같은 규칙, F-04). */
    private var holdAutoStart = false

    override val state: StateFlow<SessionState> = _state.asStateFlow()
    override val motion: StateFlow<VehicleMotion?> = _motion.asStateFlow()
    override val totalOdometerKm: StateFlow<Double?> = _totalOdometerKm.asStateFlow()
    override val missedTrip: StateFlow<MissedTrip?> = _missedTrip.asStateFlow()
    override val currentDriver: StateFlow<String?> = _currentDriver.asStateFlow()

    override suspend fun selectDriver(userId: String?) {
        _currentDriver.value = userId
    }

    override suspend fun startSession(): StartResult {
        if (_state.value !is SessionState.Idle) return StartResult.Failed("이미 기록 중입니다")
        val now = clock.millis()
        val odo = _totalOdometerKm.value
        val trip = LiveTrip("trip-$now", now, odo, "판교 테크노밸리 거래처", _currentDriver.value, "본사")
        _state.value = SessionState.Recording(trip, liveKm = if (odo == null) null else 0.0, lastSavedAt = now)
        return StartResult.Started(trip.tripId)
    }

    override suspend fun finalize() {
        autoEndJob?.cancel()
        finalizeNow()
        if (_motion.value == VehicleMotion.MOVING) holdAutoStart = true
    }

    override suspend fun requestStop() {
        val s = _state.value as? SessionState.Recording ?: return
        startAutoEnd(s, StopReason.USER_REQUEST)
    }

    override suspend fun resume() {
        val s = _state.value as? SessionState.StopCandidate ?: return
        autoEndJob?.cancel()
        _state.value = SessionState.Recording(s.trip, s.liveKm, clock.millis())
    }

    override suspend fun confirmPurpose(
        tripId: String,
        detail: PurposeDetail,
        note: String?,
        inputMode: InputMode,
        destinationLabel: String?,
    ) {
        store?.confirm(tripId, detail, note, destinationLabel)
        val s = _state.value as? SessionState.PendingReview ?: return
        if (s.trip.tripId == tripId) _state.value = SessionState.Idle
    }

    override suspend fun assignDriver(tripId: String, userId: String, source: DriverSource) {
        store?.assignDriver(tripId, userId)
    }

    override suspend fun acceptMissedTrip() {
        _missedTrip.value = null
    }

    override suspend fun ignoreMissedTrip() {
        _missedTrip.value = null
    }

    override suspend fun resolveRecovery(choice: RecoveryChoice) {
        val s = _state.value as? SessionState.NeedsRecovery ?: return
        _state.value = when (choice) {
            RecoveryChoice.RECORD -> {
                record(s.trip, s.extraKm)
                SessionState.PendingReview(s.trip, s.extraKm, null)
            }
            RecoveryChoice.CONTINUE -> SessionState.Recording(s.trip, s.extraKm, clock.millis())
            RecoveryChoice.DELETE -> SessionState.Idle
        }
    }

    // ---- 데모·테스트 조작 ----

    fun setMotion(motion: VehicleMotion?) {
        _motion.value = motion
        if (motion != VehicleMotion.MOVING) holdAutoStart = false
        val s = _state.value
        // 사람이 끝낸 대기([기록 종료])는 움직여도 되돌리지 않는다
        val userStop = s is SessionState.StopCandidate && s.reason == StopReason.USER_REQUEST
        when {
            motion == VehicleMotion.PARKED && s is SessionState.Recording -> startAutoEnd(s, StopReason.GEAR_P)
            motion == VehicleMotion.MOVING && s is SessionState.StopCandidate && !userStop -> {
                autoEndJob?.cancel()
                _state.value = SessionState.Recording(s.trip, s.liveKm, clock.millis())
            }
            motion == VehicleMotion.MOVING && !holdAutoStart && s is SessionState.Idle -> scope.launch {
                startSession()
            }
            else -> Unit
        }
    }

    fun addKm(km: Double) {
        _totalOdometerKm.value = _totalOdometerKm.value?.plus(km)
        val s = _state.value
        if (s is SessionState.Recording) {
            _state.value = s.copy(liveKm = s.liveKm?.plus(km), lastSavedAt = clock.millis())
        }
    }

    fun setMissedTrip(missed: MissedTrip?) {
        _missedTrip.value = missed
    }

    fun enterRecovery(extraKm: Double?) {
        val now = clock.millis()
        val trip = LiveTrip("trip-rec-$now", now - 3_600_000, _totalOdometerKm.value, null, _currentDriver.value)
        _state.value = SessionState.NeedsRecovery(trip, extraKm)
    }

    private fun startAutoEnd(s: SessionState.Recording, reason: StopReason) {
        val now = clock.millis()
        _state.value = SessionState.StopCandidate(s.trip, s.liveKm, reason, since = now, deadlineAt = now + gearPWaitMs)
        autoEndJob?.cancel()
        autoEndJob = scope.launch {
            delay(gearPWaitMs)
            (_state.value as? SessionState.StopCandidate)?.let { c ->
                finalizeNow()
                if (c.reason == StopReason.USER_REQUEST &&
                    _motion.value == VehicleMotion.MOVING
                ) {
                    holdAutoStart = true
                }
            }
        }
    }

    private fun finalizeNow() {
        val (trip, km) = when (val s = _state.value) {
            is SessionState.Recording -> s.trip to s.liveKm
            is SessionState.StopCandidate -> s.trip to s.liveKm
            else -> return
        }
        record(trip, km)
        _state.value = SessionState.PendingReview(trip, km, null)
    }

    private fun record(trip: LiveTrip, km: Double?) {
        store?.upsert(
            TripRow(
                tripId = trip.tripId,
                startTime = trip.startTime,
                endTime = clock.millis(),
                driverUserId = trip.driverUserId,
                originLabel = trip.originLabel,
                destinationLabel = trip.destinationLabel,
                distanceKm = km,
                purpose = null,
            ),
        )
    }
}
