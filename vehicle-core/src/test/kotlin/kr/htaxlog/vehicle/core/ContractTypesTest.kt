package kr.htaxlog.vehicle.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractTypesTest {
    @Test
    fun `신뢰도 0_8 이상만 확신 추천이다`() {
        assertTrue(Suggestion(PurposeDetail.CLIENT_VISIT, 0.8, PurposeRuleId.CLIENT).isConfident)
        assertFalse(Suggestion(PurposeDetail.NEEDS_CHECK, 0.79, PurposeRuleId.FALLBACK).isConfident)
    }

    @Test
    fun `세부 목적에서 국세청 구분이 따라온다`() {
        assertEquals(
            PurposeCategory.COMMUTE,
            Suggestion(PurposeDetail.COMMUTE_IN, 0.95, PurposeRuleId.COMMUTE_IN).category,
        )
    }

    @Test
    fun `모르는 세부 목적 문자열은 null`() {
        assertEquals(PurposeDetail.MEETING, PurposeDetail.fromName("MEETING"))
        assertNull(PurposeDetail.fromName("SOMETHING"))
        assertNull(PurposeDetail.fromName(null))
    }

    @Test
    fun `SMTP 설정을 문자열로 찍어도 비밀번호는 보이지 않는다`() {
        val c = SmtpConfig("smtp.example.com", username = "me@example.com", password = "s3cret")

        assertFalse(c.toString().contains("s3cret"))
    }
}
