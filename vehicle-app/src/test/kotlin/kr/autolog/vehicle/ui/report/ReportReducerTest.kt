package kr.autolog.vehicle.ui.report

import kr.autolog.vehicle.core.Origin
import kr.autolog.vehicle.core.RecipientInfo
import kr.autolog.vehicle.data.repository.MonthSummary
import kr.autolog.vehicle.data.repository.ServerLink
import kr.autolog.vehicle.data.repository.UploadInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class ReportReducerTest {
    private val nov = YearMonth.of(2026, 11)
    private val noUpload = UploadInfo(enabled = false, pendingCount = 0, lastUploadAt = null, lastError = null)
    private val people = listOf(
        RecipientInfo("me@example.com", "나", Origin.LOCAL),
        RecipientInfo("accounting@example.com", "회계팀", Origin.SERVER),
    )

    private fun reduce(
        summary: MonthSummary? = MonthSummary(6, 127.0, 90.7, 2),
        deselected: Set<String> = emptySet(),
        link: ServerLink = ServerLink.NotLinked,
        upload: UploadInfo = noUpload,
        month: YearMonth = nov,
    ) = reduceReport(month, nov, summary, people, deselected, ReportStep.Ready, true, link, upload)

    @Test
    fun `요약 — 업무 비율은 업무 거리 나누기 총 거리, 거리를 모르면 비율 없음`() {
        assertEquals(ReportSummaryUi(6, "127.0", 71, 2), reduce().summary)
        assertNull(reduce(summary = MonthSummary(1, 0.0, 0.0, 1)).summary!!.businessPercent)
    }

    @Test
    fun `받는 사람은 서버 배포분 먼저, 처음엔 모두 고름, 끈 사람만 빠진다`() {
        val s = reduce(deselected = setOf("me@example.com"))

        assertEquals(listOf("accounting@example.com", "me@example.com"), s.recipients.map { it.email })
        assertTrue(s.recipients.first().fromServer)
        assertEquals(1, s.selectedCount)
    }

    @Test
    fun `지금 달이면 다음 달로 못 간다`() {
        assertFalse(reduce().canGoNext)
        assertTrue(reduce(month = nov.minusMonths(1)).canGoNext)
    }

    @Test
    fun `사내 서버가 연결돼 있고 업로드가 켜져 있을 때만 자동 전송 안내 — 마지막 전송 시각은 서울 시간`() {
        assertNull(reduce().serverUpload)

        val linked = ServerLink.Linked("12가3456", "https://autolog.example", lastSyncAt = null)
        val on = noUpload.copy(enabled = true)
        val at = 1_794_446_640_000 // 2026-11-12 10:24 KST
        assertEquals(ServerUploadUi("10:24"), reduce(link = linked, upload = on.copy(lastUploadAt = at)).serverUpload)
        assertEquals(ServerUploadUi(null), reduce(link = linked, upload = on).serverUpload)
    }

    @Test
    fun `서버에 연결돼 있어도 업로드 계약이 없으면(실제 앱 지금) 자동 전송 안내를 하지 않는다`() {
        val linked = ServerLink.Linked("12가3456", "https://autolog.example", lastSyncAt = null)
        assertNull(reduce(link = linked, upload = noUpload).serverUpload)
    }
}
