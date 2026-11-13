package kr.autolog.vehicle.ui.receipt

import kr.autolog.shared.OcrStatus
import kr.autolog.shared.ReceiptPayload
import kr.autolog.vehicle.ui.design.components.confidenceLevelOf
import kr.autolog.vehicle.ui.design.components.needsCheck

/**
 * 주유 기록 입력 창 상태(스펙 7.5). 칸 값은 사람이 보고 고치는 글자 그대로 [values]에 두고,
 * 저장할 때만 [toReceiptFields]로 계약 타입으로 바꾼다. 계산은 `FuelEntryReducer.kt`의 순수 함수.
 */
data class FuelEntryUiState(
    val loading: Boolean = false,
    /** 영수증에서 왔으면 그 영수증(OCR 값·신뢰도·썸네일), 직접 입력이면 null. */
    val receipt: ReceiptPayload? = null,
    val values: Map<FuelField, String> = emptyMap(),
    val trips: List<FuelTripOption> = emptyList(),
    /** 연결할 운행. null = 연결 안 함. */
    val selectedTripId: String? = null,
    /** 지금 팝업으로 고치고 있는 칸. */
    val editing: FuelField? = null,
    /** 영수증 요약을 접고 칸 목록을 펼쳤다([수정하기], D-38). 직접 입력·금액을 못 읽은 영수증은 처음부터 칸 목록. */
    val detailsOpen: Boolean = false,
    /** 사람이 고친 칸 — OCR 신뢰도 대신 확인됨으로 본다. */
    val edited: Set<FuelField> = emptySet(),
    val saving: Boolean = false,
    val error: FuelEntryError? = null,
) {
    operator fun get(field: FuelField): String = values[field].orEmpty()

    /** 이 창에 보이는 칸(선언 순서). 직접 입력이면 공통 칸만, 숨긴 칸(할인)은 빼고. */
    val fields: List<FuelField> get() = FuelField.entries.filter { it.shown && (receipt != null || !it.receiptOnly) }

    /** 금액 검산(리터 × 단가 − 할인 = 금액). 영수증이 아니거나 OCR이 실패했으면 null(검사하지 않음). */
    val arithOk: Boolean? get() = fuelArithOk(this)

    /** 금액이 있고, 검산이 실패하지 않았고, 저장 중이 아닐 때. */
    val canAdd: Boolean get() = (this[FuelField.AMOUNT].toIntOrNull() ?: 0) > 0 && arithOk != false && !saving

    /** 칸의 OCR 신뢰도. 사람이 고친 칸은 1.0, 직접 입력이면 null. */
    fun confidence(field: FuelField): Double? =
        if (field in edited) 1.0 else receipt?.confidence?.get(field.confidenceKey)

    /** 사람이 봐야 할 칸 — 지금 신뢰도가 "확인 필요"·"다시 확인"(고친 칸은 빠진다). 선언 순서. */
    val needsCheck: List<FuelField> get() = fields.filter { levelOf(it, confidence(it)).needsCheck }

    /** 영수증 요약(금액을 주인공으로, D-38)을 보이는가 — 영수증이고, 금액을 읽었고, 아직 [수정하기]를 누르지 않았다. */
    val showsSummary: Boolean get() = receipt != null && !detailsOpen && this[FuelField.AMOUNT].isNotBlank()

    /**
     * 칸 목록 순서: 영수증이 처음 "확인 필요"로 준 칸이 먼저, 나머지는 선언 순서.
     * 고친 뒤에도 자리를 지킨다(처음 신뢰도로 정렬 — 고치는 중에 칸이 아래로 달아나지 않게).
     */
    val listedFields: List<FuelField>
        get() {
            val first = fields.filter { levelOf(it, receipt?.confidence?.get(it.confidenceKey)).needsCheck }
            return first + (fields - first.toSet())
        }

    /** 버튼 위 상태 한 줄(D-38). 무엇을 해야 [추가하기]가 되는지, 또는 다 됐는지. 우선순위 순. */
    val notice: FuelNotice?
        get() = when {
            loading -> null
            arithOk == false -> FuelNotice.ArithFailed
            (this[FuelField.AMOUNT].toIntOrNull() ?: 0) <= 0 ->
                if (receipt?.ocrStatus == OcrStatus.FAILED) FuelNotice.OcrFailed else FuelNotice.AmountMissing
            needsCheck.isNotEmpty() -> FuelNotice.NeedsCheck(needsCheck)
            arithOk == true -> FuelNotice.ArithOk
            else -> null
        }

    private fun levelOf(field: FuelField, confidence: Double?) = confidenceLevelOf(
        this[field].ifBlank {
            null
        },
        confidence,
    )
}

/** 주유 입력 창 버튼 위 상태 한 줄(D-38). 문구는 화면이 strings.xml로 만든다. */
sealed interface FuelNotice {
    /** 리터 × 단가 − 할인 ≠ 금액 — [추가하기] 비활성. */
    data object ArithFailed : FuelNotice

    /** 영수증을 읽지 못했다 — 금액만 넣어도 저장할 수 있다. */
    data object OcrFailed : FuelNotice

    /** 금액이 없다 — [추가하기] 비활성. */
    data object AmountMissing : FuelNotice

    /** 영수증 값 중 사람이 봐야 할 칸이 있다(추가는 된다). */
    data class NeedsCheck(val fields: List<FuelField>) : FuelNotice

    /** 영수증 값이 다 믿을 만하고 검산도 맞다. */
    data object ArithOk : FuelNotice
}

/** 연결할 운행 선택지 한 줄. */
data class FuelTripOption(val tripId: String, val label: FuelTripLabel, val hasFuelStop: Boolean = false)

/** 선택지 이름 — 문구는 화면이 strings.xml로 만든다(ViewModel·Reducer는 한국어를 모른다). */
sealed interface FuelTripLabel {
    /** 운행 후보가 준 표시 줄(예: "09:12 본사 → 판교 거래처"). */
    data class Named(val text: String) : FuelTripLabel

    /** 기록 중인 운행. */
    data object Current : FuelTripLabel

    /** 방금 끝나 목적 확인을 기다리는 운행. */
    data object JustEnded : FuelTripLabel
}

sealed interface FuelEntryError {
    /** 열려던 영수증이 inbox에 없다(이미 확정했거나 지워짐). */
    data object ReceiptMissing : FuelEntryError

    /** 저장소가 거절했다 — [reason]은 원천이 준 사유 그대로. */
    data class Rejected(val reason: String) : FuelEntryError
}

sealed interface FuelEntryAction {
    /** 칸을 눌러 고치기 시작. 숫자 칸은 숫자판, 글자 칸은 입력 칸 팝업. */
    data class Edit(val field: FuelField) : FuelEntryAction

    /** 숫자판 한 글자(0–9, '.'). */
    data class Key(val key: Char) : FuelEntryAction

    data object Delete : FuelEntryAction

    data class TextChanged(val value: String) : FuelEntryAction

    data object CloseEditor : FuelEntryAction

    data class SelectTrip(val tripId: String?) : FuelEntryAction

    /** 영수증 요약의 [수정하기] — 칸 목록을 펼친다(D-38). */
    data object OpenDetails : FuelEntryAction

    data object Add : FuelEntryAction

    data object Back : FuelEntryAction
}
