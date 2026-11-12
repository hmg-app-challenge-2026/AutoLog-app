package kr.autolog.vehicle.ui.settings

import kr.autolog.vehicle.core.Origin
import kr.autolog.vehicle.core.RecipientInfo
import kr.autolog.vehicle.core.UserInfo
import kr.autolog.vehicle.data.repository.ServerLink
import kr.autolog.vehicle.data.repository.UploadInfo
import kr.autolog.vehicle.ui.trips.TripDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsReducerTest {
    private val now = 1_794_446_640_000 // 2026-11-12 10:24 KST
    private val app = AppInfo("0.1.0", demo = true)

    private fun sources(link: ServerLink = ServerLink.NotLinked) = SettingsSources(
        link = link,
        upload = UploadInfo(enabled = true, pendingCount = 2, lastUploadAt = now - 3_600_000, lastError = null),
        recipients = listOf(
            RecipientInfo("me@example.com", "나", Origin.LOCAL),
            RecipientInfo("accounting@example.com", "회계팀", Origin.SERVER),
        ),
        users = listOf(UserInfo("u-2", "이현장"), UserInfo("u-1", "김영업", origin = Origin.LOCAL)),
        autoMonthlySend = false,
    )

    @Test
    fun `받는 사람은 회사 지정 먼저, 직원은 이름 순`() {
        val s = reduceSettings(SettingsLocal(), sources(), app, now)

        assertEquals(listOf("accounting@example.com", "me@example.com"), s.recipients.map { it.email })
        assertTrue(s.recipients.first().fromServer)
        assertEquals(listOf("김영업", "이현장"), s.staff.map { it.name })
        assertFalse(s.staff.first().fromServer)
    }

    @Test
    fun `연결 상태와 마지막 동기화·전송 시각은 오늘이면 오늘 시각으로`() {
        val linked = ServerLink.Linked("12가3456", "https://autolog.acme.co.kr", lastSyncAt = now - 600_000)
        val s = reduceSettings(SettingsLocal(), sources(linked), app, now)

        val link = s.link as LinkUi.Linked
        assertEquals("12가3456", link.vehicle)
        assertEquals(TripDay.TODAY, link.lastSync!!.day)
        assertEquals("10:14", link.lastSync!!.time)
        assertEquals("09:24", s.upload.lastUpload!!.time)
        assertEquals(2, s.upload.pending)
    }

    @Test
    fun `연결 팝업의 서버 주소는 지금 주소, 없으면 https`() {
        assertEquals("https://", registerPreset(ServerLink.NotLinked))
        assertEquals("https://a.example", registerPreset(ServerLink.Unreachable("12가3456", "https://a.example")))
    }

    @Test
    fun `메일 칸은 호스트·아이디·비밀번호·숫자 포트가 다 있어야 완성`() {
        assertFalse(MailForm().complete)
        assertTrue(MailForm("smtp.example.com", "587", "me@example.com", "pw").complete)
        assertFalse(MailForm("smtp.example.com", "", "me@example.com", "pw").complete)
    }
}
