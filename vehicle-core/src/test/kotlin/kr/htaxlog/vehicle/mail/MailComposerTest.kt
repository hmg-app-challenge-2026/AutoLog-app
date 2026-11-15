package kr.htaxlog.vehicle.mail

import kr.htaxlog.vehicle.core.ReportFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.YearMonth

class MailComposerTest {
    private fun report(review: Int = 0, plate: String = "12가 3456") =
        ReportFile(YearMonth.of(2026, 1), File("x.xlsx"), plate, 3, 0.0, review)

    @Test
    fun `제목은 연월과 차량번호를 담는다`() {
        assertEquals("[H-TaxLog] 2026년 1월 운행기록부 · 12가 3456", MailComposer.subject(report()))
    }

    @Test
    fun `검토할 건이 없으면 없다고 적는다`() {
        assertTrue(MailComposer.body(report()).contains("검토 필요 없음"))
    }

    @Test
    fun `첨부 이름에는 공백을 넣지 않는다`() {
        assertEquals("운행기록부_2026-01_12가3456.xlsx", MailComposer.attachmentName(report()))
    }

    @Test
    fun `번호판을 모르면 미등록이라고 쓰지 않고 그 부분을 뺀다`() {
        val r = report(plate = "미등록")

        assertEquals("[H-TaxLog] 2026년 1월 운행기록부", MailComposer.subject(r))
        assertEquals("운행기록부_2026-01.xlsx", MailComposer.attachmentName(r))
        assertFalse(MailComposer.body(r).contains("미등록"))
        assertTrue(MailComposer.body(r).startsWith("2026년 1월 운행기록부를 보내드립니다."))
    }
}
