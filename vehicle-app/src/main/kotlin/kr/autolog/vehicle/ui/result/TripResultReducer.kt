package kr.autolog.vehicle.ui.result

import kr.autolog.vehicle.core.TripLimits
import kr.autolog.vehicle.data.repository.TripResult
import kr.autolog.vehicle.nav.TripResultMode
import kr.autolog.vehicle.ui.design.components.applyKey
import kr.autolog.vehicle.ui.design.formatKm
import kr.autolog.vehicle.ui.trips.tripWhenOf
import java.util.Locale

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
        km = formatKm(interaction.editedKm ?: result.distanceKm),
        distanceEdited = interaction.editedKm != null || result.distanceEdited,
        distanceEditor = interaction.distanceEditor,
        originAddress = result.originAddress,
        destinationAddress = result.destinationAddress,
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
        confirmingDelete = interaction.confirmingDelete,
        canContinue = result.endsAt != null && !review,
        review = review,
        startedAt = if (review) tripWhenOf(result.startTime, now) else null,
    )
}

/** 거리 팝업을 열 때의 글자: 지금 보이는 거리(소수 1자리, 쉼표 없이). 거리 미확정이면 빈 칸. */
internal fun distanceDraftOf(km: Double?): String = km?.let { String.format(Locale.US, "%.1f", it) }.orEmpty()

/** 거리 숫자판 한 글자: [applyKey] 규칙 + 소수는 1자리까지, 최대 "1500.0" 길이. */
internal fun pressDistanceKey(current: String, key: Char): String {
    val next = applyKey(current, key, maxLength = DISTANCE_MAX_CHARS)
    val decimals = next.substringAfter('.', "").length
    return if (decimals > 1) current else next
}

/** [완료] 결과: 받을 수 있는 거리, 또는 받을 수 없는 이유. */
internal sealed interface DistanceInput {
    data class Valid(val km: Double) : DistanceInput

    data class Invalid(val error: DistanceEntryError) : DistanceInput
}

/** 0 초과 [TripLimits.MAX_TRIP_KM] 이하만 받는다(앱 A `editDistance`와 같은 범위). */
internal fun parseDistance(draft: String): DistanceInput {
    val km = draft.toDoubleOrNull()
    return when {
        km == null || km <= 0.0 -> DistanceInput.Invalid(DistanceEntryError.EMPTY_OR_ZERO)
        km > TripLimits.MAX_TRIP_KM -> DistanceInput.Invalid(DistanceEntryError.TOO_LONG)
        else -> DistanceInput.Valid(km)
    }
}

private const val DISTANCE_MAX_CHARS = 6
