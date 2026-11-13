package kr.autolog.vehicle.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kr.autolog.shared.KoreanAddress
import kr.autolog.vehicle.core.AddressSource
import kr.autolog.vehicle.core.ContextApi
import kr.autolog.vehicle.core.DestinationInfo
import kr.autolog.vehicle.core.DriverSource
import kr.autolog.vehicle.core.GeocodeApi
import kr.autolog.vehicle.core.InputMode
import kr.autolog.vehicle.core.LiveTrip
import kr.autolog.vehicle.core.MissedTrip
import kr.autolog.vehicle.core.PurposeDetail
import kr.autolog.vehicle.core.PurposeEngine
import kr.autolog.vehicle.core.RecoveryChoice
import kr.autolog.vehicle.core.SessionState
import kr.autolog.vehicle.core.StartResult
import kr.autolog.vehicle.core.StopReason
import kr.autolog.vehicle.core.TripSessionApi
import kr.autolog.vehicle.core.TripTiming
import kr.autolog.vehicle.core.VehicleMotion
import kr.autolog.vehicle.data.DistanceSource
import kr.autolog.vehicle.data.PlaceSource
import kr.autolog.vehicle.data.PurposeSource
import kr.autolog.vehicle.data.RecentUserEntity
import kr.autolog.vehicle.data.StopEntity
import kr.autolog.vehicle.data.TripDao
import kr.autolog.vehicle.data.TripEntity
import kr.autolog.vehicle.data.TripStatus
import kr.autolog.vehicle.data.VehicleStateEntity
import kr.autolog.vehicle.session.RecoveryDecider.Decision
import kr.autolog.vehicle.vehicle.Gear
import kr.autolog.vehicle.vehicle.OdometerReading
import kr.autolog.vehicle.vehicle.VehicleSignals
import kr.autolog.vehicle.vehicle.retryUntilValue
import java.time.Clock
import java.util.UUID

/**
 * 운행 세션 상태기계 (F-02–05, F-10, F-13, 기술명세 2.8). [TripSessionApi]의 실구현.
 *
 * IDLE → RECORDING → STOP_CANDIDATE(이유별 대기) → FINALIZING → PENDING_REVIEW → (목적 확정) IDLE.
 * 상태 전이는 [mutex]로 하나씩. 시작·10초 체크포인트·기어 P·종료는 Room에 즉시 커밋한다(CONVENTIONS 7.1-4).
 * 세션이 유일한 시계다: 자동 종료는 [SessionState.StopCandidate.deadlineAt]이 지나면 1초 틱에서 일어난다.
 * [start]는 서비스(또는 앱) 시작 때 한 번 — 복구 판단 후 차량 신호를 구독한다.
 */
class TripSessionManager(
    private val vehicle: VehicleSignals,
    private val dao: TripDao,
    private val context: ContextApi,
    private val purpose: PurposeEngine,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    private val vehicleId: () -> String = { UNREGISTERED_VEHICLE },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val log: (String) -> Unit = {},
    /** 출발·도착 좌표 → 주소(서버 카카오 → 내비 폴백). null이면 내비가 시작 때 준 주소만 쓴다. */
    private val geocode: GeocodeApi? = null,
) : TripSessionApi {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    private val _motion = MutableStateFlow<VehicleMotion?>(null)
    private val _totalOdometerKm = MutableStateFlow<Double?>(null)
    private val _missedTrip = MutableStateFlow<MissedTrip?>(null)
    private val _currentDriver = MutableStateFlow<String?>(null)

    override val state: StateFlow<SessionState> = _state.asStateFlow()
    override val motion: StateFlow<VehicleMotion?> = _motion.asStateFlow()
    override val totalOdometerKm: StateFlow<Double?> = _totalOdometerKm.asStateFlow()
    override val missedTrip: StateFlow<MissedTrip?> = _missedTrip.asStateFlow()
    override val currentDriver: StateFlow<String?> = _currentDriver.asStateFlow()

    @Volatile private var gear: Gear? = null

    @Volatile private var lastKm: Double? = null

    @Volatile private var lastMovedAt: Long? = null

    /** 마지막 차속(km/h). 차속 신호가 없는 차면 계속 null — 계기판만으로 판단한다. */
    @Volatile private var speedKph: Double? = null

    /** 지금 기록(또는 재개)을 시작한 시각. '움직임 없음' 판단은 이보다 앞선 시각을 기준으로 삼지 않는다. */
    @Volatile private var watchFrom: Long = 0

    /** 시작 복구 중(계기판 재조회 최대 60초)에는 새 운행을 만들지 않는다. 끝나지 않은 운행이 고아로 남기 때문이다. */
    @Volatile private var recovering = false

    /**
     * 사람이 [기록 종료]로 끝냈는데 그때 주차(P)가 아니면 켠다(정차·주행 중 종료, 주차 중 [기록 종료] 5초 대기 중 출발).
     * 켜져 있는 동안은 움직여도 새 운행을 자동으로 시작하지 않고, **차가 주차되거나** 사람이 직접 시작하면 끈다 —
     * 끝낸 사람의 뜻대로 끝나게. 신호 대기 같은 정차로는 풀리지 않는다(F-04, 2026-11-12 사용자 결정 B, 프론트 스펙 D-34).
     */
    @Volatile private var holdAutoStart = false

    /** 놓친 운행을 찾은 순간의 계기판 구간. 그 뒤 체크포인트가 lastKnown을 덮어써도 이 값으로 기록한다. */
    private data class MissedSpan(val startKm: Double, val endKm: Double, val endAt: Long)

    @Volatile private var missedSpan: MissedSpan? = null
    private var observing: Job? = null

    fun start() {
        if (observing != null) return
        observing = scope.launch {
            recoverOnStart()
            launch { vehicle.odometer.collect { onOdometer(it) } }
            launch { vehicle.gear.collect { onGear(it) } }
            launch { vehicle.ignitionOff.collect { onIgnitionOff() } }
            launch { vehicle.speedKph.collect { onSpeed(it) } }
            launch { context.arrivals.collect { onArrival() } }
            launch { context.destination.collect { onDestination(it) } }
            while (isActive) {
                delay(TICK_MS)
                onTick()
            }
        }
    }

    fun stop() {
        observing?.cancel()
        observing = null
    }

    // ===== TripSessionApi =====

    override suspend fun selectDriver(userId: String?) = mutex.withLock {
        _currentDriver.value = userId
        dao.saveVehicleState(vehicleState().copy(currentDriverUserId = userId))
        if (userId == null) return@withLock
        dao.upsertRecentUser(RecentUserEntity(userId, clock.millis()))
        val s = _state.value as? SessionState.Recording ?: return@withLock
        val trip = dao.trip(s.trip.tripId)?.takeIf { it.userId == null } ?: return@withLock
        save(trip.copy(userId = userId, driverSource = DriverSource.DRIVER_SELECTED))
        _state.value = s.copy(trip = s.trip.copy(driverUserId = userId))
    }

    override suspend fun startSession(): StartResult = mutex.withLock { startLocked() }

    override suspend fun requestStop() = mutex.withLock {
        val s = _state.value as? SessionState.Recording ?: return@withLock
        enterStopLocked(s.trip, s.liveKm, StopReason.USER_REQUEST, null)
    }

    override suspend fun finalize() = mutex.withLock {
        finalizeLocked()
        holdAutoStartUntilParked()
    }

    override suspend fun resume() = mutex.withLock {
        (_state.value as? SessionState.StopCandidate)?.let { resumeLocked(it) } ?: Unit
    }

    override suspend fun discardTrip(tripId: String) = mutex.withLock {
        // 종료 대기면 먼저 끝낸다 — 끝난 시각·계기판 값(놓친 운행 기준)을 남긴다. 짧은 운행 버리기에 걸리면 이미 DISCARDED
        val s = _state.value
        if (s is SessionState.StopCandidate && s.trip.tripId == tripId) finalizeLocked()
        // 기록 중인 운행·이미 지운 운행은 그대로
        val trip = dao.trip(tripId)
            ?.takeIf { it.status != TripStatus.RECORDING && it.status != TripStatus.DISCARDED }
            ?: return@withLock
        save(trip.copy(status = TripStatus.DISCARDED))
        val now = _state.value
        if (now is SessionState.PendingReview && now.trip.tripId == tripId) _state.value = SessionState.Idle
        log("discard $tripId: user")
        holdAutoStartUntilParked()
    }

    override suspend fun confirmPurpose(
        tripId: String,
        detail: PurposeDetail,
        note: String?,
        inputMode: InputMode,
        destinationLabel: String?,
    ) = mutex.withLock {
        // 지운 운행은 되살리지 않는다(목적 확정이 상태를 CONFIRMED로 바꾸므로 — D-39)
        val t = dao.trip(tripId)?.takeIf { it.status != TripStatus.DISCARDED } ?: return@withLock
        save(
            t.copy(
                purposeDetail = detail.name,
                purposeNote = note ?: t.purposeNote,
                purposeInputMode = inputMode.name,
                purposeSource = PurposeSource.USER_CONFIRMED,
                destinationLabel = destinationLabel ?: t.destinationLabel,
                destinationSource = if (destinationLabel != null) PlaceSource.USER_SELECTED else t.destinationSource,
                status = if (t.status == TripStatus.RECORDING) t.status else TripStatus.CONFIRMED,
            ),
        )
        val s = _state.value
        if (s is SessionState.PendingReview && s.trip.tripId == tripId) _state.value = SessionState.Idle
    }

    override suspend fun editDistance(tripId: String, km: Double): Boolean = mutex.withLock {
        if (!DistancePolicy.isEditableKm(km)) return@withLock false
        val t = dao.trip(tripId)
            ?.takeIf { it.status != TripStatus.RECORDING && it.status != TripStatus.DISCARDED }
            ?: return@withLock false
        // 계기판 값과 같게 되돌리면 수정 표시를 지운다. 원본 distanceKm·rawSnapshotJson은 그대로(CONVENTIONS 7.1-2)
        save(t.copy(editedDistanceKm = km.takeIf { it != t.distanceKm }))
        val s = _state.value
        if (s is SessionState.PendingReview && s.trip.tripId == tripId) _state.value = s.copy(distanceKm = km)
        log("edit distance $tripId: ${t.distanceKm} → $km")
        true
    }

    override suspend fun assignDriver(tripId: String, userId: String, source: DriverSource) = mutex.withLock {
        val t = dao.trip(tripId) ?: return@withLock
        save(t.copy(userId = userId, driverSource = source))
        dao.upsertRecentUser(RecentUserEntity(userId, clock.millis()))
    }

    override suspend fun acceptMissedTrip() = mutex.withLock {
        val missed = _missedTrip.value ?: return@withLock
        val span = missedSpan ?: return@withLock
        val start = span.startKm
        val end = span.endKm
        val now = clock.millis()
        val trip = TripEntity(
            tripId = newId(),
            vehicleId = vehicleId(),
            userId = null,
            startTime = missed.lastKnownAt,
            endTime = span.endAt,
            timeEstimated = true,
            startOdometerKm = start,
            endOdometerKm = end,
            lastOdometerKm = end,
            distanceKm = DistancePolicy.difference(start, end),
            purposeNote = MISSED_TRIP_NOTE,
            status = TripStatus.PENDING_REVIEW,
        )
        dao.upsert(trip)
        // 그 사이 새 운행이 기록되고 있으면 lastKnown은 이미 더 최신이다. 되돌리지 않는다.
        if (_state.value is SessionState.Idle) saveLastKnown(maxOf(end, lastKm ?: end), now)
        clearMissed()
        if (_state.value is SessionState.Idle) {
            _state.value = SessionState.PendingReview(trip.toLive(), trip.distanceKm, suggestion = null)
        }
    }

    override suspend fun ignoreMissedTrip() = mutex.withLock {
        val span = missedSpan ?: return@withLock
        val idle = _state.value is SessionState.Idle
        val st = vehicleState()
        dao.saveVehicleState(
            st.copy(
                lastKnownOdometerKm = if (idle) maxOf(span.endKm, lastKm ?: span.endKm) else st.lastKnownOdometerKm,
                lastKnownAt = if (idle) clock.millis() else st.lastKnownAt,
                ignoredOdometerKm = span.endKm,
            ),
        )
        clearMissed()
    }

    private fun clearMissed() {
        missedSpan = null
        _missedTrip.value = null
    }

    override suspend fun resolveRecovery(choice: RecoveryChoice) = mutex.withLock {
        val s = _state.value as? SessionState.NeedsRecovery ?: return@withLock
        val trip = dao.trip(s.trip.tripId) ?: return@withLock
        when (choice) {
            RecoveryChoice.RECORD -> finishLocked(trip, vehicle.readOdometer()?.km ?: lastKm, clock.millis())
            RecoveryChoice.CONTINUE -> {
                watchFrom = clock.millis()
                _state.value = SessionState.Recording(s.trip, liveKm(trip, lastKm), clock.millis())
            }
            RecoveryChoice.DELETE -> {
                save(trip.copy(status = TripStatus.DISCARDED))
                lastKm?.let { saveLastKnown(it, clock.millis()) }
                _state.value = SessionState.Idle
            }
        }
    }

    // ===== 차량 신호 =====

    private suspend fun recoverOnStart() {
        recovering = true
        try {
            recoverLocked()
        } finally {
            recovering = false
        }
        updateMotion()
    }

    private suspend fun recoverLocked() {
        val st = vehicleState()
        _currentDriver.value = st.currentDriverUserId
        gear = vehicle.readGear()
        val now = retryUntilValue(READY_ATTEMPTS, READY_INTERVAL_MS) { vehicle.readOdometer() }
        now?.let { observeKm(it, moved = false) }
        mutex.withLock {
            val rec = dao.findRecording()
            val decision = RecoveryDecider.decide(
                hasRecording = rec != null,
                recordingLastKm = rec?.let { it.lastOdometerKm ?: it.startOdometerKm },
                nowKm = now?.km,
                lastKnownKm = st.lastKnownOdometerKm,
                ignoredKm = st.ignoredOdometerKm,
            )
            log("recover: $decision")
            when (decision) {
                Decision.Nothing -> now?.let { saveLastKnown(it.km, it.at) }
                Decision.ContinueRecording -> rec?.let { continueRecording(it) }
                is Decision.AutoFinalize ->
                    if (gear == Gear.DRIVE || gear == Gear.REVERSE) {
                        continueRecording(rec!!) // 기어가 들어가 있으면 아직 운행 중(신호 대기 중 재시작 등)
                    } else {
                        finishLocked(rec!!, decision.endKm, rec.lastSeenAt ?: clock.millis())
                    }
                is Decision.AskRecovery -> _state.value = SessionState.NeedsRecovery(rec!!.toLive(), decision.extraKm)
                is Decision.MissedTrip -> {
                    val startKm = st.lastKnownOdometerKm
                    if (startKm != null && now != null) {
                        missedSpan = MissedSpan(startKm, now.km, now.at)
                        _missedTrip.value = MissedTrip(decision.gapKm, st.lastKnownAt ?: clock.millis())
                    }
                }
            }
        }
    }

    private suspend fun onOdometer(r: OdometerReading) {
        val moved = lastKm?.let { r.km > it } ?: false
        observeKm(r, moved)
        mutex.withLock {
            // 사람이 끝낸 종료 대기([기록 종료])는 움직여도 되돌리지 않는다 — 대기가 끝나면 그대로 끝난다
            (_state.value as? SessionState.StopCandidate)
                ?.takeIf { moved && it.reason != StopReason.USER_REQUEST }
                ?.let { resumeLocked(it) }
            val s = _state.value as? SessionState.Recording ?: return@withLock
            val trip = dao.trip(s.trip.tripId) ?: return@withLock
            var savedAt = s.lastSavedAt
            if (r.at - s.lastSavedAt >= CHECKPOINT_MS) {
                dao.checkpoint(trip.tripId, r.km, r.at)
                saveLastKnown(r.km, r.at)
                savedAt = r.at
            }
            _state.value = s.copy(liveKm = liveKm(trip, r.km), lastSavedAt = savedAt)
        }
        updateMotion()
        autoStartIfDriving()
    }

    /**
     * 차속 변화. 일정 속도 이상이면 "움직였다"로 기록한다(거리는 바꾸지 않는다).
     * 계기판이 아직 안 바뀌었어도 자동 시작하고, 정차 대기 중이면 기록을 이어 간다.
     */
    private suspend fun onSpeed(kph: Double) {
        val now = clock.millis()
        speedKph = kph
        val moving = DrivingJudge.isMovingSpeed(kph)
        if (moving) lastMovedAt = now
        updateMotion()
        if (moving && gear != Gear.PARK) {
            mutex.withLock {
                // 기어 P 대기와 사람이 끝낸 대기([기록 종료])는 움직여도 되돌리지 않는다
                (_state.value as? SessionState.StopCandidate)
                    ?.takeIf { it.reason != StopReason.GEAR_P && it.reason != StopReason.USER_REQUEST }
                    ?.let { resumeLocked(it) }
            }
            autoStartIfDriving()
        }
    }

    private suspend fun onGear(g: Gear) {
        gear = g
        updateMotion()
        mutex.withLock {
            when (val s = _state.value) {
                is SessionState.Recording -> if (g == Gear.PARK) {
                    // 기어 P → 시동 OFF 직전일 수 있다. 계기판을 바로 다시 읽어 저장한다.
                    val r = vehicle.readOdometer()
                    if (r != null) {
                        observeKm(r, moved = false)
                        dao.checkpoint(s.trip.tripId, r.km, r.at)
                        saveLastKnown(r.km, r.at)
                    }
                    val trip = dao.trip(s.trip.tripId)
                    enterStopLocked(s.trip, trip?.let { liveKm(it, lastKm) } ?: s.liveKm, StopReason.GEAR_P, null)
                }
                is SessionState.StopCandidate -> when {
                    g == Gear.PARK && s.reason !in setOf(StopReason.GEAR_P, StopReason.USER_REQUEST) -> enterStopLocked(
                        s.trip,
                        s.liveKm,
                        StopReason.GEAR_P,
                        s.since,
                    )
                    g != Gear.PARK && s.reason == StopReason.GEAR_P -> resumeLocked(s)
                    else -> Unit
                }
                else -> Unit
            }
        }
    }

    /** 시동 OFF·ACC 순간 — 기다리지 않고 바로 확정한다(프론트 스펙 1.1.1 4순위). 프로세스가 곧 멈출 수 있다. */
    private suspend fun onIgnitionOff() = mutex.withLock {
        val s = _state.value
        if (s !is SessionState.Recording && s !is SessionState.StopCandidate) return@withLock
        log("ignition off → finalize")
        finalizeLocked()
    }

    private suspend fun onArrival() = mutex.withLock {
        val s = _state.value as? SessionState.Recording ?: return@withLock
        enterStopLocked(s.trip, s.liveKm, StopReason.NAVI_ARRIVED, null)
    }

    private suspend fun onDestination(d: DestinationInfo?) = mutex.withLock {
        val s = _state.value as? SessionState.Recording ?: return@withLock
        if (d == null || !d.isSet() || d.label == s.trip.destinationLabel) return@withLock
        val trip = dao.trip(s.trip.tripId) ?: return@withLock
        val updated = trip.withDestination(d)
        dao.upsert(updated)
        _state.value = s.copy(trip = s.trip.withPlacesOf(updated))
    }

    private suspend fun onTick() {
        // 차속 이벤트는 값이 바뀔 때만 올 수 있다. 같은 속도로 달리는 동안에도 "움직이는 중"을 유지한다.
        if (DrivingJudge.isMovingSpeed(speedKph) && gear != Gear.PARK) lastMovedAt = clock.millis()
        updateMotion()
        mutex.withLock {
            val now = clock.millis()
            when (val s = _state.value) {
                is SessionState.StopCandidate -> if (now >= s.deadlineAt) {
                    finalizeLocked()
                    if (s.reason == StopReason.USER_REQUEST) holdAutoStartUntilParked()
                }
                is SessionState.Recording -> {
                    val stillSince = maxOf(lastMovedAt ?: watchFrom, watchFrom)
                    if (lastKm != null && gear != Gear.PARK && now - stillSince >= DrivingJudge.MOVING_WINDOW_MS) {
                        enterStopLocked(s.trip, s.liveKm, StopReason.NO_MOVEMENT, stillSince)
                    }
                }
                else -> Unit
            }
        }
    }

    /** 운행이 없을 때 차가 움직이면 기록을 자동으로 시작한다(F-02). 주행 중에는 화면이 없으므로 묻지 않는다. */
    private suspend fun autoStartIfDriving() {
        if (holdAutoStart) return
        val s = _state.value
        if (_motion.value == VehicleMotion.MOVING && (s is SessionState.Idle || s is SessionState.PendingReview)) {
            val r = startSession()
            log("auto start: $r")
        }
    }

    // ===== 전이 (mutex 안에서만) =====

    private suspend fun startLocked(): StartResult {
        if (recovering) return StartResult.Failed("이전 운행을 확인하는 중입니다. 잠시 후 다시 시작해 주세요")
        val s = _state.value
        if (s is SessionState.Recording || s is SessionState.StopCandidate || s is SessionState.NeedsRecovery) {
            return StartResult.Failed("이미 기록 중입니다")
        }
        holdAutoStart = false // 사람이 직접 시작했으면 멈춤도 끝
        val now = clock.millis()
        val odo = vehicle.readOdometer()
        odo?.let { observeKm(it, moved = false) }
        val naviLocation = context.currentNaviLocation()
        val fix = if (naviLocation == null) context.freshFix() else null
        val driver = _currentDriver.value
        val trip = TripEntity(
            tripId = newId(),
            vehicleId = vehicleId(),
            userId = driver,
            driverSource = driver?.let { DriverSource.DRIVER_SELECTED },
            startTime = now,
            startOdometerKm = odo?.km,
            lastOdometerKm = odo?.km,
            lastSeenAt = now,
            distanceSource = if (odo == null) DistanceSource.UNAVAILABLE else DistanceSource.VEHICLE_ODOMETER,
            // 이름(등록 장소·건물)과 서버 주소는 enrichOrigin이 곧 채운다. 그 전까지는 내비 주소
            originAddress = KoreanAddress.shortenRegion(naviLocation?.address),
            originAddressSource = naviLocation?.address?.takeIf { it.isNotBlank() }?.let { AddressSource.NAVI_HELPER },
            originLat = naviLocation?.lat ?: fix?.lat,
            originLng = naviLocation?.lng ?: fix?.lng,
            originSource = when {
                naviLocation != null -> PlaceSource.NAVI_CURRENT
                fix != null -> PlaceSource.FUSED_LOCATION
                else -> null
            },
            rawSnapshotJson = "{\"start\":{\"km\":${odo?.km},\"at\":${odo?.at}}}",
        ).let { t -> context.destination.value?.takeIf { it.isSet() }?.let { t.withDestination(it) } ?: t }
        dao.upsert(trip)
        watchFrom = now
        _state.value = SessionState.Recording(trip.toLive(), liveKm = odo?.let { 0.0 }, lastSavedAt = now)
        log("start ${trip.tripId} odo=${odo?.km}")
        scope.launch { enrichOrigin(trip.tripId) }
        return StartResult.Started(trip.tripId)
    }

    private suspend fun finalizeLocked() {
        val (live, endTime, userRequested) = when (val s = _state.value) {
            is SessionState.Recording -> Triple(s.trip, clock.millis(), false)
            is SessionState.StopCandidate -> Triple(s.trip, s.since, s.reason == StopReason.USER_REQUEST)
            else -> return
        }
        val trip = dao.trip(live.tripId) ?: return
        // 지금 못 읽으면 기어 P·체크포인트 때 실제로 읽어 둔 계기판 값으로 끝낸다(추정이 아니라 측정값).
        val end = vehicle.readOdometer()?.km ?: trip.lastOdometerKm
        finishLocked(trip, end, endTime, userRequested)
    }

    private suspend fun finishLocked(trip: TripEntity, endKm: Double?, endTime: Long, userRequested: Boolean = false) {
        val verdict = DistancePolicy.evaluate(trip.startOdometerKm, endKm, trip.startTime, endTime)
        if (verdict is DistancePolicy.Verdict.NoMovement && !userRequested) {
            save(trip.copy(status = TripStatus.DISCARDED, endTime = endTime, endOdometerKm = endKm))
            _state.value = SessionState.Idle
            log("discard ${trip.tripId}: no movement")
            return
        }
        if (verdict is DistancePolicy.Verdict.Unavailable) log("distance unavailable ${trip.tripId}: ${verdict.reason}")
        // 사용자가 직접 종료한 0 km 운행도 결과 화면에서 확인할 수 있게 남긴다.
        val distance = when (verdict) {
            is DistancePolicy.Verdict.Ok -> verdict.distanceKm
            DistancePolicy.Verdict.NoMovement -> 0.0
            is DistancePolicy.Verdict.Unavailable -> null
        }
        val fix = context.latestFix.value
        var done = trip.copy(
            endTime = endTime,
            endOdometerKm = endKm,
            distanceKm = distance,
            distanceSource = if (distance == null) DistanceSource.UNAVAILABLE else DistanceSource.VEHICLE_ODOMETER,
            destinationLat = trip.destinationLat ?: fix?.lat,
            destinationLng = trip.destinationLng ?: fix?.lng,
            destinationSource = trip.destinationSource ?: fix?.let { PlaceSource.FUSED_LOCATION },
            status = TripStatus.PENDING_REVIEW,
            rawSnapshotJson = trip.rawSnapshotJson?.removeSuffix("}") + ",\"end\":{\"km\":$endKm,\"at\":$endTime}}",
        )
        val facts = done.toFacts()
        val suggestion = runCatching { purpose.suggest(facts) }.getOrNull()
        if (done.userId == null) {
            runCatching { purpose.driverCandidate(facts) }.getOrNull()
                ?.takeIf { it.source == DriverSource.SCHEDULE && it.confidence >= AUTO_DRIVER_CONFIDENCE }
                ?.let { done = done.copy(userId = it.userId, driverSource = DriverSource.SCHEDULE) }
        }
        done = done.copy(
            suggestedPurpose = suggestion?.detail?.name,
            suggestedConfidence = suggestion?.confidence,
            suggestedRule = suggestion?.rule?.name,
            purposeSource = if (suggestion != null) PurposeSource.RULE_SUGGESTED else done.purposeSource,
        )
        save(done)
        endKm?.let { saveLastKnown(it, endTime) }
        _state.value = SessionState.PendingReview(done.toLive(), distance, suggestion)
        log("finish ${trip.tripId} distance=$distance")
        scope.launch { enrichDestination(trip.tripId, current = true) }
    }

    private fun enterStopLocked(trip: LiveTrip, liveKm: Double?, reason: StopReason, since: Long?) {
        val now = clock.millis()
        val waitSec = if (reason == StopReason.GEAR_P || reason == StopReason.USER_REQUEST) {
            TripTiming.T_GEAR_P_SEC
        } else {
            TripTiming.T_STOP_SEC
        }
        val start = since ?: now
        _state.value =
            SessionState.StopCandidate(trip, liveKm, reason, since = start, deadlineAt = now + waitSec * 1_000)
    }

    private suspend fun resumeLocked(s: SessionState.StopCandidate) {
        val now = clock.millis()
        if (now - s.since >= MIN_STOP_RECORD_MS) {
            dao.insertStop(
                StopEntity(
                    tripId = s.trip.tripId,
                    startTime = s.since,
                    endTime = now,
                    odometerKm = lastKm,
                    reason = s.reason.name,
                ),
            )
        }
        val trip = dao.trip(s.trip.tripId)
        watchFrom = now
        _state.value = SessionState.Recording(
            s.trip,
            trip?.let { liveKm(it, lastKm) } ?: s.liveKm,
            lastSavedAt = trip?.lastSeenAt ?: now,
        )
    }

    private fun continueRecording(trip: TripEntity) {
        watchFrom = clock.millis()
        _state.value = SessionState.Recording(trip.toLive(), liveKm(trip, lastKm), clock.millis())
    }

    // ===== 출발·도착 주소 (F-06, F-07, I-05) — 네트워크는 락 밖, 쓰기는 락 안 =====

    /**
     * 서버(카카오) 주소가 없는 최근 [BACKFILL_DAYS]일 운행을 최대 [limit]건 채운다. 동기화가 끝날 때마다 부른다(InboxSync).
     * 지난 좌표라 내비 폴백은 쓰지 않는다. 서버가 연속으로 답하지 않으면 멈춘다. 채운 건수를 돌려준다.
     */
    suspend fun fillMissingAddresses(limit: Int = BACKFILL_LIMIT): Int {
        if (geocode == null) return 0
        val since = clock.millis() - BACKFILL_DAYS * DAY_MS
        var filled = 0
        var misses = 0
        for (t in dao.tripsNeedingAddress(since, limit)) {
            if (misses >= BACKFILL_MAX_MISSES) break
            val ok = (needsKakao(t.originLat, t.originAddressSource) && enrichOrigin(t.tripId)) or
                (
                    needsKakao(t.destinationLat, t.destinationAddressSource) &&
                        enrichDestination(t.tripId, current = false)
                    )
            if (ok) filled++ else misses++
        }
        if (filled > 0) log("address backfill: $filled")
        return filled
    }

    private fun needsKakao(lat: Double?, source: AddressSource?) = lat != null && source != AddressSource.KAKAO_RELAY

    /** 출발지 이름·주소를 붙인다. 서버 주소를 받았으면 true. */
    private suspend fun enrichOrigin(tripId: String): Boolean {
        val t = dao.trip(tripId) ?: return false
        val lat = t.originLat ?: return false
        val lng = t.originLng ?: return false
        val place = purpose.placeAt(lat, lng)
        // 시작 때 내비 주소는 이미 넣었다 → 여기선 서버만
        val geo = geocode?.reverse(lat, lng, isCurrentPosition = false)
        mutex.withLock {
            val fresh = dao.trip(tripId) ?: return false
            val m = PlacePolicy.mergeOrigin(fresh, place, geo)
            if (m != fresh) dao.setOriginPlace(tripId, m.originLabel, m.originAddress, m.originAddressSource)
            refreshLiveLocked(tripId)
        }
        return geo?.source == AddressSource.KAKAO_RELAY
    }

    /**
     * 도착지 이름·좌표·주소를 붙인다. [current]면 운행이 막 끝난 때라 차의 지금 위치(내비 → 위치 SDK)를 실제 도착 위치로 보고,
     * 서버에 닿지 않으면 내비 주소로 대신한다. 서버 주소를 받았으면 true.
     */
    private suspend fun enrichDestination(tripId: String, current: Boolean): Boolean {
        val t = dao.trip(tripId) ?: return false
        val end = if (current) endPosition() else null
        val lat = end?.lat ?: t.destinationLat ?: return false
        val lng = end?.lng ?: t.destinationLng ?: return false
        val place = purpose.placeAt(lat, lng)
        val geo = geocode?.reverse(lat, lng, isCurrentPosition = current)
        mutex.withLock {
            val fresh = dao.trip(tripId)?.takeIf { it.status != TripStatus.DISCARDED } ?: return false
            val m = PlacePolicy.mergeDestination(fresh, end, place, geo)
            if (m != fresh) {
                dao.setDestinationPlace(
                    tripId,
                    m.destinationLabel,
                    m.destinationLat,
                    m.destinationLng,
                    m.destinationSource,
                    m.destinationAddress,
                    m.destinationAddressSource,
                )
            }
            refreshLiveLocked(tripId)
        }
        return geo?.source == AddressSource.KAKAO_RELAY
    }

    /** 운행이 끝난 직후 차의 실제 위치. 내비 현재 위치 → 30초 이내 위치 SDK 좌표 → 모름. */
    private suspend fun endPosition(): EndPosition? {
        context.currentNaviLocation()?.let { return EndPosition(it.lat, it.lng, PlaceSource.NAVI_CURRENT) }
        return context.freshFix()?.let { EndPosition(it.lat, it.lng, PlaceSource.FUSED_LOCATION) }
    }

    /** 화면에 보이는 운행이면 장소 표시를 Room 최신 값으로 바꾼다. */
    private suspend fun refreshLiveLocked(tripId: String) {
        val t = dao.trip(tripId) ?: return
        _state.value = when (val s = _state.value) {
            is SessionState.Recording -> if (s.trip.tripId == tripId) s.copy(trip = s.trip.withPlacesOf(t)) else s
            is SessionState.StopCandidate -> if (s.trip.tripId == tripId) s.copy(trip = s.trip.withPlacesOf(t)) else s
            is SessionState.PendingReview -> if (s.trip.tripId == tripId) s.copy(trip = s.trip.withPlacesOf(t)) else s
            is SessionState.NeedsRecovery -> if (s.trip.tripId == tripId) s.copy(trip = s.trip.withPlacesOf(t)) else s
            SessionState.Idle -> s
        }
    }

    // ===== 도우미 =====

    private fun observeKm(r: OdometerReading, moved: Boolean) {
        lastKm = r.km
        _totalOdometerKm.value = r.km
        if (moved) lastMovedAt = r.at
    }

    private fun updateMotion() {
        _motion.value = DrivingJudge.judge(gear, lastKm != null, lastMovedAt, clock.millis(), speedKph)
        // 주차했다 — 다음 출발부터는 다시 자동 시작(정차·판정 불가로는 풀지 않는다)
        if (_motion.value == VehicleMotion.PARKED) holdAutoStart = false
    }

    /** 사람이 끝낸 직후 주차(P)가 아니면 다음 주차까지 자동 시작을 멈춘다([holdAutoStart]). */
    private fun holdAutoStartUntilParked() {
        val s = _state.value
        val ended = s is SessionState.Idle || s is SessionState.PendingReview
        if (ended && _motion.value != VehicleMotion.PARKED) holdAutoStart = true
    }

    private suspend fun save(trip: TripEntity) = dao.upsert(trip.copy(revision = trip.revision + 1))

    private suspend fun vehicleState() = dao.vehicleState() ?: VehicleStateEntity()

    private suspend fun saveLastKnown(km: Double, at: Long) {
        dao.saveVehicleState(vehicleState().copy(lastKnownOdometerKm = km, lastKnownAt = at))
    }

    private fun liveKm(trip: TripEntity, km: Double?): Double? {
        val start = trip.startOdometerKm ?: return null
        return km?.let { maxOf(0.0, DistancePolicy.difference(start, it)) }
    }

    companion object {
        const val UNREGISTERED_VEHICLE = "unregistered"
        const val MISSED_TRIP_NOTE = "앱 미실행 중 운행(시각 추정)"
        const val TICK_MS = 1_000L
        const val CHECKPOINT_MS = 10_000L

        /** 이보다 짧은 정차는 Stop으로 남기지 않는다(신호 대기 소음 제거). F-04 "60초 정차 후 재출발 시 Trip 유지". */
        const val MIN_STOP_RECORD_MS = 60_000L

        /** 배차로 찾은 운전자가 이 신뢰도 이상이면 자동 지정한다(기술명세 3.3 ② 0.95). 그 외 후보는 묻는다. */
        const val AUTO_DRIVER_CONFIDENCE = 0.95
        const val READY_ATTEMPTS = 12
        const val READY_INTERVAL_MS = 5_000L

        /** 주소 나중 채우기: 최근 30일, 한 번에 20건, 서버가 3번 연속 답이 없으면 멈춤. */
        const val BACKFILL_DAYS = 30L
        const val BACKFILL_LIMIT = 20
        const val BACKFILL_MAX_MISSES = 3
        private const val DAY_MS = 86_400_000L
    }
}
