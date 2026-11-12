package kr.autolog.vehicle.ui.home

import kr.autolog.vehicle.data.repository.ActiveTrip
import kr.autolog.vehicle.data.repository.HistoryItem
import kr.autolog.vehicle.data.repository.MonthSummary
import kr.autolog.vehicle.data.repository.TripSession
import kr.autolog.vehicle.ui.design.formatKm
import kr.autolog.vehicle.ui.design.remainingSeconds
import kr.autolog.vehicle.ui.trips.toTripRowUi

/**
 * Home 상태 계산(순수 함수, HomeReducerTest 대상). 입력은 Repository 값 타입만 — 앱 A 계약이 바뀌어도 이 파일은 그대로다.
 * [destination]은 내비 목적지 이름, [now]는 경과 분·남은 초 계산용.
 */
internal fun reduceHome(
    session: TripSession,
    missedKm: Double?,
    destination: String?,
    now: Long,
    month: MonthSummary? = null,
    recent: List<HistoryItem> = emptyList(),
): HomeUiState {
    val idle = session.isIdle()
    // 놓친 운행은 운행이 없을 때만 묻는다 — 기록 중엔 지금 운행이 먼저
    val base =
        HomeUiState(
            month = month?.let {
                MonthUi(it.tripCount, formatKm(it.totalKm).orEmpty(), formatKm(it.businessKm).orEmpty())
            },
            destination = destination,
            missedKm = if (idle) formatKm(missedKm) else null,
            recentTrips = recent.take(RECENT_TRIPS).map { it.toTripRowUi(now) },
        )
    return when (session) {
        TripSession.Idle, is TripSession.PendingReview -> base
        // 종료 대기(AutoEnding)도 Home 뒤 배경은 기록 중 그대로 — 위에 운행 결과 카드가 열린다(D-28)
        is TripSession.Recording -> base.withTrip(HomePhase.RECORDING_PARKED, session.trip, session.liveKm, now)
        is TripSession.AutoEnding -> base.withTrip(HomePhase.RECORDING_PARKED, session.trip, session.liveKm, now)
            .copy(autoEndInSec = remainingSeconds(session.deadlineAt, now))
        is TripSession.NeedsRecovery ->
            base.copy(phase = HomePhase.NEEDS_RECOVERY, recoveryExtraKm = formatKm(session.extraKm))
    }
}

/**
 * 운행이 없는 대기 상태(Home "대기 중")인가 — 기록 중·종료 대기·복구 중이 아니다.
 * 오른쪽 위 운전자 배지는 이때만 보인다(D-30, FullApp).
 */
internal fun TripSession.isIdle(): Boolean = this is TripSession.Idle || this is TripSession.PendingReview

/** 기록 중·자동 종료 대기가 같이 쓰는 값: 거리 · 출발지 · 경과 분. */
private fun HomeUiState.withTrip(phase: HomePhase, trip: ActiveTrip, liveKm: Double?, now: Long) = copy(
    phase = phase,
    tripKm = formatKm(liveKm),
    origin = trip.origin,
    elapsedMin = ((now - trip.startTime) / 60_000).toInt().coerceAtLeast(0),
)
