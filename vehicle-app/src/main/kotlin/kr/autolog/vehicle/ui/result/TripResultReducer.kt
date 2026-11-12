package kr.autolog.vehicle.ui.result

import kr.autolog.vehicle.data.repository.TripResult
import kr.autolog.vehicle.nav.TripResultMode
import kr.autolog.vehicle.ui.design.formatKm
import kr.autolog.vehicle.ui.trips.tripWhenOf

/**
 * 운행 결과 상태 계산(순수 함수, TripResultReducerTest 대상). [result] null + [loaded] = 운행을 찾지 못함.
 * [mode] = REVIEW(운행 기록 조회에서 연 사후 확인)면 카운트다운이 없고 제목이 운행 시각이다(D-29).
 * 남은 시간: 수정 중이면 없음, 종료 대기 중이면 앱 A 종료 시각까지, 끝난 운행이면 자체 마감 시각까지 — 둘 다 **마감 시각 − 지금**(ms)이라
 * 막대가 마감 순간에 정확히 0이 된다(초 단위로 세면 마지막 1초만큼 막대가 남은 채 화면이 넘어갔다).
 */
internal fun reduceTripResult(
    result: TripResult?,
    loaded: Boolean,
    interaction: TripResultInteraction,
    now: Long,
    mode: TripResultMode = TripResultMode.AFTER_TRIP,
): TripResultUiState {
    val review = mode == TripResultMode.REVIEW
    if (!loaded) return TripResultUiState(loading = true)
    if (result == null) return TripResultUiState(loading = false, notFound = true)
    return TripResultUiState(
        loading = false,
        km = formatKm(result.distanceKm),
        minutes = result.endTime?.let { ((it - result.startTime) / 60_000).toInt().coerceAtLeast(0) },
        origin = result.origin,
        destination = result.destination,
        selected = interaction.selected ?: result.category,
        editing = interaction.editing,
        autoCloseRemainingMs = when {
            interaction.editing || interaction.saved -> null
            else -> (result.endsAt ?: interaction.autoCloseAt)?.let { (it - now).coerceAtLeast(0) }
        },
        autoCloseTotalMs = interaction.autoCloseTotalMs,
        saving = interaction.saving,
        saved = interaction.saved,
        canContinue = result.endsAt != null && !review,
        review = review,
        startedAt = if (review) tripWhenOf(result.startTime, now) else null,
    )
}
