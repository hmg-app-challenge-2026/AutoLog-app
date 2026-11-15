package kr.htaxlog.relay.receipt

import kr.htaxlog.shared.ReceiptFields
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReceiptVerifierTest {
    private fun fields(
        liters: Double? = 40.0,
        unit: Int? = 1700,
        discount: Int? = 0,
        amount: Int? = 68_000,
        merchant: String? = "SK 판교주유소",
        card: String? = "1234",
    ) = ReceiptFields(merchant, "2026-11-02T09:20:00+09:00", "휘발유", liters, unit, discount, amount, card)

    @Test
    fun `40L x 1700원 = 68000원이면 검산 통과`() {
        assertEquals(true, ReceiptVerifier.arithmeticOk(fields()))
    }

    @Test
    fun `할인을 빼고 맞으면 통과`() {
        assertEquals(true, ReceiptVerifier.arithmeticOk(fields(discount = 2_000, amount = 66_000)))
    }

    @Test
    fun `허용 오차 100원 이내면 통과, 넘으면 실패`() {
        assertEquals(true, ReceiptVerifier.arithmeticOk(fields(amount = 68_100)))
        assertEquals(false, ReceiptVerifier.arithmeticOk(fields(amount = 68_400)))
    }

    @Test
    fun `큰 금액은 0점5퍼센트까지 허용한다`() {
        // 100 L × 2000 = 200,000 → 허용 1,000원
        assertEquals(true, ReceiptVerifier.arithmeticOk(fields(liters = 100.0, unit = 2_000, amount = 200_900)))
    }

    @Test
    fun `수량이나 단가가 없으면 검산할 수 없다`() {
        assertNull(ReceiptVerifier.arithmeticOk(fields(liters = null)))
        assertNull(ReceiptVerifier.arithmeticOk(fields(unit = null)))
    }

    @Test
    fun `카드번호 전체가 와도 끝 4자리만 남긴다`() {
        assertEquals("3456", ReceiptVerifier.mask(fields(card = "1234-5678-9012-3456")).cardLast4)
        assertEquals("3456", ReceiptVerifier.mask(fields(card = "****3456")).cardLast4)
    }

    @Test
    fun `숫자 4자리가 안 되면 카드 끝자리는 비운다`() {
        assertNull(ReceiptVerifier.mask(fields(card = "12")).cardLast4)
    }

    @Test
    fun `상호에 섞인 카드번호는 지운다`() {
        val masked = ReceiptVerifier.mask(fields(merchant = "SK주유소 4111 1111 1111 1111"))

        assertEquals("SK주유소", masked.merchant)
        assertFalse(masked.merchant!!.contains("1111"))
    }

    @Test
    fun `짧은 숫자는 카드번호로 보지 않는다`() {
        assertTrue(
            ReceiptVerifier.mask(fields(merchant = "GS칼텍스 판교2점 031-123-4567")).merchant!!.contains("031-123-4567"),
        )
    }
}
