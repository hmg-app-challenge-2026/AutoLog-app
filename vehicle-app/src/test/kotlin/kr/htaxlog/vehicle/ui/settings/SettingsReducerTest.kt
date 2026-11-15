package kr.htaxlog.vehicle.ui.settings

import kr.htaxlog.vehicle.core.Origin
import kr.htaxlog.vehicle.core.RecipientInfo
import kr.htaxlog.vehicle.core.UserInfo
import kr.htaxlog.vehicle.data.repository.ServerLink
import kr.htaxlog.vehicle.ui.trips.TripDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsReducerTest {
    private val now = 1_794_446_640_000 // 2026-11-12 10:24 KST
    private val app = AppInfo("0.1.0", demo = true)

    private fun sources(link: ServerLink = ServerLink.NotLinked, mailAccount: String? = null) = SettingsSources(
        link = link,
        recipients = listOf(
            RecipientInfo("me@example.com", "나", Origin.LOCAL),
            RecipientInfo("accounting@example.com", "회계팀", Origin.SERVER),
        ),
        users = listOf(UserInfo("u-2", "이현장"), UserInfo("u-1", "김영업", origin = Origin.LOCAL)),
        autoMonthlySend = false,
        mailAccount = mailAccount,
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
    fun `연결 상태와 마지막 동기화 시각은 오늘이면 오늘 시각으로`() {
        val linked = ServerLink.Linked("12가3456", "https://htaxlog.acme.co.kr", lastSyncAt = now - 600_000)
        val s = reduceSettings(SettingsLocal(), sources(linked), app, now)

        val link = s.link as LinkUi.Linked
        assertEquals("12가3456", link.vehicle)
        assertEquals(TripDay.TODAY, link.lastSync!!.day)
        assertEquals("10:14", link.lastSync!!.time)
    }

    @Test
    fun `메일은 연결된 주소가 있어야 연결됨`() {
        assertFalse(reduceSettings(SettingsLocal(), sources(), app, now).mailConnected)

        val linked = reduceSettings(SettingsLocal(), sources(mailAccount = "htaxlog@acme.co.kr"), app, now)

        assertTrue(linked.mailConnected)
        assertEquals("htaxlog@acme.co.kr", linked.mailAccount)
    }
}
