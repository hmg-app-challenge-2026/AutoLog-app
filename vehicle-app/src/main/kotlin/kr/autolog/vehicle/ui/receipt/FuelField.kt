package kr.autolog.vehicle.ui.receipt

import androidx.annotation.StringRes
import kr.autolog.vehicle.R

/** 칸을 고치는 방법. 숫자는 숫자판 팝업, 글자는 입력 칸 팝업(스펙 7.5 — 텍스트 입력은 최후의 수단). */
enum class FuelInput {
    /** 원 단위 정수(금액·단가·할인). */
    INTEGER,

    /** 소수점 있는 숫자(리터). */
    DECIMAL,

    /** 자유 글자(주유소·일시·유종). */
    TEXT,

    /** 숫자 4자리(카드 끝 4자리 — 전체 번호는 어디에도 두지 않는다). */
    LAST4,
    ;

    val numeric: Boolean get() = this == INTEGER || this == DECIMAL
}

/**
 * 주유 기록 입력 칸 하나(스펙 7.5, D-20). 칸의 이름·단위·입력 방법·OCR 신뢰도 키를 **여기 한 곳**에 둔다 —
 * 화면·상태·편집이 칸마다 `when`을 따로 두지 않는다. 선언 순서 = 화면에 놓이는 순서.
 * 칸을 더하려면 항목 하나와 [toValues]·[toReceiptFields](영수증 계약과의 변환) 한 줄씩만 더한다.
 */
enum class FuelField(
    @StringRes val label: Int,
    val input: FuelInput,
    /** `ReceiptPayload.confidence`의 키. */
    val confidenceKey: String,
    /** 값 뒤에 붙는 단위. 없으면 null. */
    @StringRes val unit: Int? = null,
    /** 영수증에서 왔을 때만 보이는 칸(직접 입력은 공통 칸만 — 금액·리터·단가). */
    val receiptOnly: Boolean = false,
) {
    MERCHANT(R.string.fuel_merchant, FuelInput.TEXT, "merchant", receiptOnly = true),
    DATETIME(R.string.fuel_datetime, FuelInput.TEXT, "datetime", receiptOnly = true),
    FUEL_TYPE(R.string.fuel_type, FuelInput.TEXT, "fuelType", receiptOnly = true),
    AMOUNT(R.string.fuel_amount, FuelInput.INTEGER, "amountWon", unit = R.string.unit_won),
    LITERS(R.string.fuel_liters, FuelInput.DECIMAL, "liters", unit = R.string.unit_liter),
    UNIT_PRICE(R.string.fuel_unit_price, FuelInput.INTEGER, "unitPrice", unit = R.string.unit_won_per_liter),
    DISCOUNT(R.string.fuel_discount, FuelInput.INTEGER, "discountWon", unit = R.string.unit_won, receiptOnly = true),
    CARD_LAST4(R.string.fuel_card_last4, FuelInput.LAST4, "cardLast4", receiptOnly = true),
}
