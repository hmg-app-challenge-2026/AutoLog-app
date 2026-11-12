package kr.autolog.vehicle.ui.receipt

import kr.autolog.shared.OcrStatus
import kr.autolog.shared.ReceiptFields
import kr.autolog.shared.ReceiptPayload
import kr.autolog.vehicle.core.TripCandidate
import kr.autolog.vehicle.data.repository.TripSession
import kr.autolog.vehicle.ui.design.components.CONFIDENCE_OK
import kr.autolog.vehicle.ui.design.components.applyKey
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/*
 * 주유 기록 입력 창의 상태 계산(순수 함수, FuelEntryReducerTest 대상). 입력은 Repository 값 타입만.
 */

/** 검산 허용 오차(원). OCR 리터가 소수 둘째 자리에서 반올림돼도 통과하게. */
private const val ARITH_TOLERANCE_WON = 1.0

private const val CARD_DIGITS = 4

/** 직접 입력: 지금 운행(기록 중·종료 대기) 또는 방금 끝난 운행이 있으면 그 운행을 고른 채로 연다. */
internal fun reduceManualEntry(session: TripSession): FuelEntryUiState {
    val trips =
        when (session) {
            is TripSession.Recording -> listOf(FuelTripOption(session.trip.tripId, FuelTripLabel.Current))
            is TripSession.AutoEnding -> listOf(FuelTripOption(session.trip.tripId, FuelTripLabel.Current))
            is TripSession.PendingReview -> listOf(FuelTripOption(session.tripId, FuelTripLabel.JustEnded))
            TripSession.Idle, is TripSession.NeedsRecovery -> emptyList()
        }
    return FuelEntryUiState(trips = trips, selectedTripId = trips.firstOrNull()?.tripId)
}

/**
 * 영수증에서 왔을 때: OCR 값으로 칸을 채운다. 스펙 7.5 자동 조건(모든 칸 확인됨 · 검산 통과 · 후보 1개)이면
 * 그 후보를 고른 채로 연다 — [추가하기] 1탭으로 끝난다.
 */
internal fun reduceReceiptEntry(receipt: ReceiptPayload, candidates: List<TripCandidate>): FuelEntryUiState {
    val trips = candidates.map { FuelTripOption(it.tripId, FuelTripLabel.Named(it.label), it.hasFuelStop) }
    val autoSelect =
        receipt.arithOk == true &&
            receipt.confidence.isNotEmpty() &&
            receipt.confidence.values.all { it >= CONFIDENCE_OK } &&
            trips.size == 1
    return FuelEntryUiState(
        receipt = receipt,
        values = receipt.fields.toValues(),
        trips = trips,
        selectedTripId = if (autoSelect) trips.single().tripId else null,
    )
}

/** 지금 고치는 숫자 칸에 숫자판 한 글자를 더한다. 숫자 칸이 아니면 그대로. */
internal fun FuelEntryUiState.pressKey(key: Char): FuelEntryUiState = editNumber { applyKey(it, key) }

internal fun FuelEntryUiState.deleteKey(): FuelEntryUiState = editNumber { it.dropLast(1) }

/** 지금 고치는 글자 칸을 [text]로. 카드 칸은 숫자 4자리만 남긴다. */
internal fun FuelEntryUiState.typeText(text: String): FuelEntryUiState {
    val field = editing?.takeIf { !it.input.numeric } ?: return this
    val value = if (field.input == FuelInput.LAST4) text.filter(Char::isDigit).take(CARD_DIGITS) else text
    return withValue(field, value)
}

private fun FuelEntryUiState.editNumber(change: (String) -> String): FuelEntryUiState {
    val field = editing?.takeIf { it.input.numeric } ?: return this
    return withValue(field, change(this[field]))
}

/** 사람이 고친 칸은 신뢰도 대신 확인됨으로 보고, 지난 저장 오류는 지운다. */
private fun FuelEntryUiState.withValue(field: FuelField, value: String) =
    copy(values = values + (field to value), edited = edited + field, error = null)

/** 리터 × 단가 − 할인 = 금액(±1원). 셋 중 하나라도 비면 OCR이 낸 검산 결과를 쓴다. */
internal fun fuelArithOk(state: FuelEntryUiState): Boolean? {
    val receipt = state.receipt ?: return null
    if (receipt.ocrStatus == OcrStatus.FAILED) return null
    val liters = state[FuelField.LITERS].toDoubleOrNull()
    val unitPrice = state[FuelField.UNIT_PRICE].toIntOrNull()
    val amount = state[FuelField.AMOUNT].toIntOrNull()
    if (liters == null || unitPrice == null || amount == null) return receipt.arithOk
    val discount = state[FuelField.DISCOUNT].toIntOrNull() ?: 0
    return abs(liters * unitPrice - discount - amount) <= ARITH_TOLERANCE_WON
}

/** 영수증 계약 → 칸 글자. */
internal fun ReceiptFields.toValues(): Map<FuelField, String> = buildMap {
    merchant?.let { put(FuelField.MERCHANT, it) }
    datetime?.let { put(FuelField.DATETIME, it) }
    fuelType?.let { put(FuelField.FUEL_TYPE, it) }
    amountWon?.let { put(FuelField.AMOUNT, it.toString()) }
    liters?.let { put(FuelField.LITERS, it.toString()) }
    unitPrice?.let { put(FuelField.UNIT_PRICE, it.toString()) }
    discountWon?.let { put(FuelField.DISCOUNT, it.toString()) }
    cardLast4?.let { put(FuelField.CARD_LAST4, it) }
}

/** 칸 글자 → 영수증 계약(저장할 때). 빈 칸은 null. */
internal fun FuelEntryUiState.toReceiptFields(): ReceiptFields {
    fun text(field: FuelField) = this[field].ifBlank { null }
    return ReceiptFields(
        merchant = text(FuelField.MERCHANT),
        datetime = text(FuelField.DATETIME),
        fuelType = text(FuelField.FUEL_TYPE),
        liters = this[FuelField.LITERS].toDoubleOrNull(),
        unitPrice = this[FuelField.UNIT_PRICE].toIntOrNull(),
        discountWon = this[FuelField.DISCOUNT].toIntOrNull(),
        amountWon = this[FuelField.AMOUNT].toIntOrNull(),
        cardLast4 = text(FuelField.CARD_LAST4),
    )
}

private val DATE_TIME = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.KOREA)

/**
 * 칸 글자를 화면 표시용으로(단위·마스킹은 화면이 붙인다). 비었으면 null.
 * 원 단위 숫자는 천 단위 쉼표, 일시는 "11/2 09:20", 리터·글자는 그대로.
 */
internal fun formatFuelValue(field: FuelField, raw: String): String? {
    if (raw.isBlank()) return null
    return when {
        field.input == FuelInput.INTEGER ->
            raw.toLongOrNull()?.let { NumberFormat.getNumberInstance(Locale.KOREA).format(it) } ?: raw
        field == FuelField.DATETIME ->
            runCatching { OffsetDateTime.parse(raw).format(DATE_TIME) }.getOrDefault(raw)
        else -> raw
    }
}
