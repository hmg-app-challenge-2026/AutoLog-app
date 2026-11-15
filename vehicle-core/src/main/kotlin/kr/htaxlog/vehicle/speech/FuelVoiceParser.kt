package kr.htaxlog.vehicle.speech

import kr.htaxlog.vehicle.core.VoiceFuel

/**
 * 주유 음성 → 금액·리터·단가 (F-18). 예: "6만8천원 40리터", "칠만 원 사십 리터", "리터당 1,689원 50리터".
 * "리터당/단가" 뒤의 원은 단가, 나머지 원은 결제 금액. 상식 밖의 값(금액 1천~200만, 1~300 L, 단가 500~5천)은 버린다.
 */
object FuelVoiceParser {
    private const val NUM = "[0-9영공일이삼사오육칠팔구십백천만점.,]+"
    private val TOKEN = Regex("(리터당|엘당|l당|단가)?($NUM)(원|리터|엘|l|ℓ)")

    // "금액이6만" 처럼 조사 '이'가 숫자 앞에 붙는 경우를 떼어 낸다.
    private val LEADING_KOREAN_DIGITS = Regex("^[영공일이삼사오육칠팔구]+(?=[0-9])")

    fun parse(text: String): VoiceFuel? {
        val t = text.lowercase().filterNot { it.isWhitespace() }
        var amount: Int? = null
        var liters: Double? = null
        var unitPrice: Int? = null
        for (m in TOKEN.findAll(t)) {
            val (prefix, rawNum, unit) = m.destructured
            val value = KoreanNumber.parse(rawNum.replace(LEADING_KOREAN_DIGITS, "")) ?: continue
            when {
                unit == "원" && prefix.isNotEmpty() -> if (unitPrice == null &&
                    value in 500.0..5_000.0
                ) {
                    unitPrice = value.toInt()
                }
                unit == "원" -> if (amount == null && value in 1_000.0..2_000_000.0) amount = value.toInt()
                else -> if (liters == null && value in 1.0..300.0) liters = value
            }
        }
        return if (amount == null && liters == null && unitPrice == null) null else VoiceFuel(amount, liters, unitPrice)
    }
}
