package kr.autolog.vehicle.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kr.autolog.vehicle.common.throttleLatest
import kr.autolog.vehicle.core.DriverSource
import kr.autolog.vehicle.core.LiveTrip
import kr.autolog.vehicle.core.RecoveryChoice
import kr.autolog.vehicle.core.SessionState
import kr.autolog.vehicle.core.StartResult
import kr.autolog.vehicle.core.StopReason
import kr.autolog.vehicle.core.TripSessionApi

/**
 * 화면이 보는 운행 세션(스펙 6.5·6.6 경계). 앱 A 계약(core `SessionState`)이 바뀌어도 이 타입은 유지된다 —
 * 바뀌는 것은 [DefaultSessionRepository]의 변환뿐. 화면에 필요한 값만 담는다.
 */
sealed interface TripSession {
    data object Idle : TripSession

    /** [liveKm] null = 계기판을 못 읽음(거리 미확정, 추정 금지). */
    data class Recording(val trip: ActiveTrip, val liveKm: Double?) : TripSession

    /** 자동 종료 대기. [deadlineAt]이 유일한 시계다 — 화면은 남은 시간만 계산한다(스펙 7.2.1). */
    data class AutoEnding(val trip: ActiveTrip, val liveKm: Double?, val reason: AutoEndReason, val deadlineAt: Long) :
        TripSession

    /** 운행이 끝나 목적 확인을 기다린다 — Home이 운행 결과 카드를 자동으로 연다. */
    data class PendingReview(val tripId: String) : TripSession

    /** 앱이 꺼진 사이 미종료 운행이 남았다(F-10 복구). [extraKm] = 마지막 저장 뒤 늘어난 거리. */
    data class NeedsRecovery(val extraKm: Double?) : TripSession
}

/** 진행 중 운행에서 화면이 쓰는 값. [origin] null = 출발지 모름. */
data class ActiveTrip(val tripId: String, val startTime: Long, val origin: String?)

enum class AutoEndReason { GEAR_P, NO_MOVEMENT, NAVI_ARRIVED, USER_REQUEST }

/** 복구 팝업의 선택(F-10): 기록하기 · 계속 기록 · 삭제. */
enum class RecoveryAction { RECORD, CONTINUE, DELETE }

/** 운행 세션 (스펙 6.5). 앱 A 실계약이 오면 구현만 바꾼다. */
interface SessionRepository {
    /** 1초 스로틀(스펙 6.11). */
    val state: Flow<TripSession>
    val totalOdometerKm: Flow<Double?>

    /** 앱이 꺼진 동안 달린 거리(F-10 놓친 운행). 없으면 null. */
    val missedKm: Flow<Double?>
    val currentDriverId: StateFlow<String?>

    suspend fun selectDriver(userId: String?)

    suspend fun startTrip(): Outcome

    /** 기다리지 않고 끝낸다(운행 결과 [저장하기] — 종료 대기 중). */
    suspend fun endNow()

    /** [기록 종료]: 자동 종료와 같은 5초 종료 대기에 들어간다 — 운행 결과 카드가 카운트다운과 함께 열린다(스펙 1.2, D-28). */
    suspend fun requestStop()

    suspend fun keepRecording()

    suspend fun assignDriver(tripId: String, userId: String)

    suspend fun acceptMissedTrip()

    suspend fun ignoreMissedTrip()

    suspend fun resolveRecovery(action: RecoveryAction)
}

class DefaultSessionRepository(private val api: TripSessionApi) : SessionRepository {
    override val state: Flow<TripSession> = api.state.throttleLatest(1_000).map { it.toTripSession() }
    override val totalOdometerKm: Flow<Double?> = api.totalOdometerKm.throttleLatest(1_000)
    override val missedKm: Flow<Double?> = api.missedTrip.map { it?.gapKm }
    override val currentDriverId: StateFlow<String?> = api.currentDriver

    override suspend fun selectDriver(userId: String?) = api.selectDriver(userId)

    override suspend fun startTrip(): Outcome = when (val r = api.startSession()) {
        is StartResult.Started -> Outcome.Ok
        is StartResult.Failed -> Outcome.Failed(r.reason)
    }

    override suspend fun endNow() = api.finalize()

    override suspend fun requestStop() = api.requestStop()

    override suspend fun keepRecording() = api.resume()

    override suspend fun assignDriver(tripId: String, userId: String) =
        api.assignDriver(tripId, userId, DriverSource.DRIVER_SELECTED)

    override suspend fun acceptMissedTrip() = api.acceptMissedTrip()

    override suspend fun ignoreMissedTrip() = api.ignoreMissedTrip()

    override suspend fun resolveRecovery(action: RecoveryAction) = api.resolveRecovery(
        when (action) {
            RecoveryAction.RECORD -> RecoveryChoice.RECORD
            RecoveryAction.CONTINUE -> RecoveryChoice.CONTINUE
            RecoveryAction.DELETE -> RecoveryChoice.DELETE
        },
    )
}

// ---- 앱 A 계약 → 화면 타입. 계약이 바뀌면 여기만 고친다 ----

internal fun SessionState.toTripSession(): TripSession = when (this) {
    SessionState.Idle -> TripSession.Idle
    is SessionState.Recording -> TripSession.Recording(trip.toActive(), liveKm)
    is SessionState.StopCandidate -> TripSession.AutoEnding(
        trip.toActive(),
        liveKm,
        reason.toAutoEnd(),
        deadlineAt,
    )
    is SessionState.PendingReview -> TripSession.PendingReview(trip.tripId)
    is SessionState.NeedsRecovery -> TripSession.NeedsRecovery(extraKm)
}

private fun LiveTrip.toActive() = ActiveTrip(tripId, startTime, originLabel)

private fun StopReason.toAutoEnd(): AutoEndReason = when (this) {
    StopReason.GEAR_P -> AutoEndReason.GEAR_P
    StopReason.NO_MOVEMENT -> AutoEndReason.NO_MOVEMENT
    StopReason.NAVI_ARRIVED -> AutoEndReason.NAVI_ARRIVED
    StopReason.USER_REQUEST -> AutoEndReason.USER_REQUEST
}
