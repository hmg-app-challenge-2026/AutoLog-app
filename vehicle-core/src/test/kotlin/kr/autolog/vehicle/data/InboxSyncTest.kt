package kr.autolog.vehicle.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kr.autolog.shared.MailAccountDto
import kr.autolog.vehicle.core.SyncResult
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class InboxSyncTest {
    private val server = MockWebServer()
    private val store = MemoryDeviceStore()
    private val seen = mutableSetOf<Long>()
    private var failUsers = false
    private val savedUsers = mutableListOf<UserEntity>()

    /** DAO는 필요한 메서드만 흉내 낸다. 나머지 호출은 Unit/false/null. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> dao(type: Class<T>, handle: (String, Array<Any?>) -> Any?): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, m, args ->
            handle(m.name, args ?: emptyArray()) ?: when (m.returnType) {
                java.lang.Boolean.TYPE -> false
                else -> if (m.name == "isSeen") false else Unit
            }
        } as T

    private val refs = dao(ReferenceDao::class.java) { name, args ->
        if (name == "replaceServerUsers") {
            if (failUsers) error("disk full")
            @Suppress("UNCHECKED_CAST")
            savedUsers += args[0] as List<UserEntity>
        }
        null
    }
    private val expenses = dao(ExpenseDao::class.java) { name, _ ->
        if (name ==
            "confirmedReceipts"
        ) {
            emptyList<Any>()
        } else {
            null
        }
    }
    private val inbox = dao(InboxDao::class.java) { name, args ->
        when (name) {
            "isSeen" -> (args[0] as Long) in seen
            "markSeen" -> {
                seen += (args[0] as InboxSeenEntity).inboxId
                Unit
            }
            else -> null
        }
    }

    private lateinit var sync: InboxSync

    @Before
    fun setUp() {
        server.start()
        store.record = DeviceRecord(server.url("/").toString().trimEnd('/'), "tok", "veh-1", "org-1")
        sync = InboxSync(DeviceRepository(store, RelayHttp(io = Dispatchers.Unconfined)), refs, expenses, inbox)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun usersLetter() =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
            """[{"id":7,"kind":"USERS","payload":{"type":"USERS","users":[{"userId":"u1","name":"김영업","dept":null,"email":null}]},"created_at":"2026-11-09T09:00:00+09:00"}]""",
        )

    @Test
    fun `기준 정보를 반영하면 반영 표시 후 서버에 ack한다`() = runTest {
        server.enqueue(usersLetter())
        server.enqueue(MockResponse().setResponseCode(204))

        val r = sync.syncNow()

        assertEquals(SyncResult.Ok(1), r)
        assertEquals("김영업", savedUsers.single().name)
        assertEquals("/v1/inbox", server.takeRequest().path)
        assertEquals("/v1/inbox/7/ack", server.takeRequest().path)
        assertTrue(7L in seen)
    }

    @Test
    fun `반영에 실패한 편지는 ack하지 않아 다음 동기화 때 다시 받는다`() = runTest {
        failUsers = true
        server.enqueue(usersLetter())

        sync.syncNow()

        assertEquals(1, server.requestCount) // inbox 조회만, ack 없음
        assertTrue(7L !in seen)

        // 다음 동기화: 이번엔 반영 성공 → ack
        failUsers = false
        server.enqueue(usersLetter())
        server.enqueue(MockResponse().setResponseCode(204))
        sync.syncNow()

        assertEquals(3, server.requestCount)
        assertEquals("김영업", savedUsers.single().name)
    }

    private fun mailLetter(account: String) =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
            """[{"id":9,"kind":"MAIL_ACCOUNT","payload":{"type":"MAIL_ACCOUNT","account":$account},"created_at":"2026-11-14T09:00:00+09:00"}]""",
        )

    @Test
    fun `회사 메일 계정 편지는 메일 쪽에 넘기고 반영되면 ack한다`() = runTest {
        val got = mutableListOf<MailAccountDto?>()
        sync.onMailAccount = {
            got += it
            true
        }
        server.enqueue(
            mailLetter("""{"host":"smtp.example.com","port":465,"username":"a@example.com","password":"pw"}"""),
        )
        server.enqueue(MockResponse().setResponseCode(204))

        sync.syncNow()

        assertEquals("a@example.com", got.single()?.username)
        server.takeRequest()
        assertEquals("/v1/inbox/9/ack", server.takeRequest().path)
    }

    @Test
    fun `메일 쪽이 반영하지 못하면 회사 메일 계정 편지를 ack하지 않는다`() = runTest {
        sync.onMailAccount = { false }
        server.enqueue(mailLetter("null"))

        sync.syncNow()

        assertEquals(1, server.requestCount)
        assertTrue(9L !in seen)
    }
}
