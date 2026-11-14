package kr.autolog.vehicle.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kr.autolog.vehicle.core.Origin
import kr.autolog.vehicle.core.RecipientInfo
import kr.autolog.vehicle.core.SettingsResult
import kr.autolog.vehicle.core.SyncResult
import kr.autolog.vehicle.core.UserInfo
import kr.autolog.vehicle.fake.FakeRelayClient
import kr.autolog.vehicle.fake.InMemoryDomainStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsServiceTest {
    private val store =
        InMemoryDomainStore().apply {
            recipientList.value = listOf(RecipientInfo("accounting@example.com", "회계팀", Origin.SERVER))
        }
    private val relay = FakeRelayClient(sync = SyncResult.Ok(3))
    private val pref = MemPref()
    private val settings = SettingsService(store, relay, pref, newId = { "local-1" })

    @Test
    fun `수신자를 직접 추가하면 소문자로 저장되고 서버 배포분 뒤에 나온다`() = runTest {
        val r = settings.addRecipient("  Boss@Example.COM ", "팀장")

        assertEquals(SettingsResult.Ok, r)
        assertEquals(
            listOf("accounting@example.com", "boss@example.com"),
            settings.recipients().first().map {
                it.email
            },
        )
    }

    @Test
    fun `이메일 형식이 틀리면 Invalid`() = runTest {
        assertTrue(settings.addRecipient("boss@", null) is SettingsResult.Invalid)
        assertTrue(settings.addRecipient("boss example.com", null) is SettingsResult.Invalid)
    }

    @Test
    fun `회사 지정 수신자는 다시 추가할 수 없다`() = runTest {
        assertTrue(settings.addRecipient("ACCOUNTING@example.com", null) is SettingsResult.Invalid)
        assertEquals(1, settings.recipients().first().size)
    }

    @Test
    fun `회사 지정 수신자를 빼면 서버가 목록을 다시 보내도 이 차에서는 숨긴다`() = runTest {
        val hidden = HiddenRecipientStore.InMemory()
        val s = SettingsService(store, relay, pref, hidden)

        assertEquals(SettingsResult.Ok, s.removeRecipient("Accounting@example.com"))
        store.recipientList.value = listOf(RecipientInfo("accounting@example.com", "회계팀", Origin.SERVER))

        assertTrue(s.recipients().first().isEmpty())
        assertEquals(setOf("accounting@example.com"), hidden.load())
        // 차를 다시 켜도(저장소에서 다시 읽어도) 숨긴 채로
        assertTrue(SettingsService(store, relay, pref, hidden).recipients().first().isEmpty())
    }

    @Test
    fun `뺀 회사 지정 수신자를 다시 추가하면 회사 지정으로 돌아온다`() = runTest {
        settings.removeRecipient("accounting@example.com")

        assertEquals(SettingsResult.Ok, settings.addRecipient("accounting@example.com", null))

        assertEquals(Origin.SERVER, settings.recipients().first().single().origin)
    }

    @Test
    fun `직접 추가한 수신자는 지울 수 있다`() = runTest {
        settings.addRecipient("boss@example.com", null)

        assertEquals(SettingsResult.Ok, settings.removeRecipient("BOSS@example.com"))
        assertEquals(1, settings.recipients().first().size)
    }

    @Test
    fun `같은 이메일이 서버와 로컬에 있으면 서버 것 하나만 보인다`() {
        val merged =
            RecipientBook.merge(
                listOf(RecipientInfo("a@x.com", "로컬", Origin.LOCAL), RecipientInfo("A@x.com", "서버", Origin.SERVER)),
            )

        assertEquals(listOf("서버"), merged.map { it.name })
    }

    @Test
    fun `직원을 직접 추가하면 LOCAL로 저장되고 이름순으로 나온다`() = runTest {
        store.userList.value = listOf(UserInfo("u-1", "박관리"))

        assertEquals(SettingsResult.Ok, settings.addUser(" 김신입 ", "영업2팀", null))

        val users = settings.users().first()
        assertEquals(listOf("김신입", "박관리"), users.map { it.name })
        assertEquals(Origin.LOCAL, users.first().origin)
    }

    @Test
    fun `직원 이름이 비었거나 이메일이 틀리면 Invalid`() = runTest {
        assertTrue(settings.addUser(" ", null, null) is SettingsResult.Invalid)
        assertTrue(settings.addUser("김신입", null, "no-at") is SettingsResult.Invalid)
    }

    @Test
    fun `자동 발송 스위치는 저장되고 바로 반영된다`() = runTest {
        assertFalse(settings.autoMonthlySend.value)

        settings.setAutoMonthlySend(true)

        assertTrue(settings.autoMonthlySend.value)
        assertTrue(pref.value)
    }

    @Test
    fun `지금 동기화는 서버 결과를 그대로 돌려준다`() = runTest {
        assertEquals(SyncResult.Ok(3), settings.syncNow())
    }
}

private class MemPref : AutoSendPreference {
    var value = false

    override fun load() = value

    override fun save(enabled: Boolean) {
        value = enabled
    }
}
