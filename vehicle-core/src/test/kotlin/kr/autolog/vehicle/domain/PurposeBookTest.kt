package kr.autolog.vehicle.domain

import kr.autolog.shared.PurposeDto
import kr.autolog.shared.PurposeRuleDto
import kr.autolog.shared.PurposesPayload
import kr.autolog.vehicle.core.Origin
import kr.autolog.vehicle.core.PurposeCategory
import kr.autolog.vehicle.core.PurposeDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurposeBookTest {
    private val store = PurposeBookStore.InMemory()
    private var n = 0
    private val book = PurposeBook(store) { "local-${++n}" }
    private val company = PurposesPayload(
        purposes = listOf(PurposeDto("납품", "BUSINESS")),
        rules = listOf(PurposeRuleDto("r-1", "p-office", "p-depot", "custom:BUSINESS:납품")),
    )

    @Test
    fun `추가 목적은 이름만으로 구분과 이름이 되살아난다`() {
        val d = PurposeDetail.custom(" 납품 ", PurposeCategory.BUSINESS)

        assertEquals("custom:BUSINESS:납품", d.name)
        assertEquals(d, PurposeDetail.fromName(d.name))
        assertEquals("납품", PurposeDetail.fromName(d.name)?.labelKo)
        assertEquals(PurposeDetail.MEETING, PurposeDetail.fromName("MEETING"))
        assertEquals(null, PurposeDetail.fromName("custom:NOPE:x"))
    }

    @Test
    fun `회사 목적과 규칙을 받고 이 차에서 만든 것은 따로 남는다`() {
        book.addPurpose("현장 점검", PurposeCategory.BUSINESS)
        book.applyServer(company)

        val origins = book.purposes().filter { it.detail.isCustom }.map { it.detail.labelKo to it.origin }
        assertEquals(listOf("납품" to Origin.SERVER, "현장 점검" to Origin.LOCAL), origins)
        assertEquals("r-1", book.rules().single().ruleId)
        // 저장소에서 다시 읽어도 같다
        assertEquals(book.data.value, PurposeBook(store).data.value)
    }

    @Test
    fun `회사 지정을 이 차에서 빼면 서버가 다시 보내도 숨기고 같은 이름을 추가하면 돌아온다`() {
        book.applyServer(company)

        book.removePurpose("custom:BUSINESS:납품")
        book.removeRule("r-1")
        book.applyServer(company)

        assertTrue(book.purposes().none { it.detail.labelKo == "납품" })
        assertTrue(book.rules().isEmpty())

        book.addPurpose("납품", PurposeCategory.BUSINESS)
        assertEquals(Origin.SERVER, book.purposes().single { it.detail.labelKo == "납품" }.origin)
    }

    @Test
    fun `규칙은 같은 출발·도착이 있거나 출발과 도착이 같으면 거절한다`() {
        book.applyServer(company)

        assertTrue(book.addRule("p-office", "p-depot", "MEETING").isFailure)
        assertTrue(book.addRule("p-depot", "p-depot", "MEETING").isFailure)
        assertTrue(book.addRule(null, "p-depot", "MEETING").isSuccess)
        assertFalse(book.removePurpose("MEETING").isSuccess) // 기본 목적은 못 뺀다
    }

    @Test
    fun `이 차 목적을 빼면 그 목적을 쓰는 이 차 규칙도 함께 빠진다`() {
        val d = book.addPurpose("현장 점검", PurposeCategory.BUSINESS).getOrThrow()
        book.addRule(null, "p-site", d.name)

        book.removePurpose(d.name)

        assertTrue(book.rules().isEmpty())
    }
}
