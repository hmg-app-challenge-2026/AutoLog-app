package kr.htaxlog.relay.receipt

import kr.htaxlog.shared.ReceiptFields
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * OCR 결과 검산과 카드번호 마스킹 (F-41, 기술명세 4.5 단계 5·6). 순수 함수.
 */
object ReceiptVerifier {
    private val CARD_NUMBER = Regex("""(?:\d[ -]?){13,19}""")
    private val DIGITS = Regex("""\d""")

    /**
     * 검산: |결제금액 − 수량×단가 + 할인| ≤ max(100원, 결제금액×0.5%).
     * 수량·단가·금액 중 하나라도 없으면 검산할 수 없으므로 null.
     */
    fun arithmeticOk(f: ReceiptFields): Boolean? {
        val amount = f.amountWon ?: return null
        val liters = f.liters ?: return null
        val unit = f.unitPrice ?: return null
        val expected = (liters * unit).roundToInt() - (f.discountWon ?: 0)
        val tolerance = max(100.0, amount * 0.005)
        return abs(amount - expected) <= tolerance
    }

    /**
     * 카드번호 이중 방어. Gemini가 지시를 어기고 전체 번호를 내도 끝 4자리만 남긴다.
     * - cardLast4: 숫자만 추려 끝 4자리
     * - 나머지 문자열 필드: 13~19자리 숫자열을 지운다
     */
    fun mask(f: ReceiptFields): ReceiptFields = f.copy(
        merchant = f.merchant?.let(::stripCardNumbers),
        fuelType = f.fuelType?.let(::stripCardNumbers),
        cardLast4 =
        f.cardLast4
            ?.let { DIGITS.findAll(it).joinToString("") { m -> m.value } }
            ?.takeLast(4)
            ?.takeIf { it.length == 4 },
    )

    private fun stripCardNumbers(s: String): String = CARD_NUMBER.replace(s, "").trim()
}
