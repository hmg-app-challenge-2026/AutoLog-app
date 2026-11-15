package kr.htaxlog.vehicle.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kr.htaxlog.shared.HTaxLogJson
import kr.htaxlog.shared.InboxItem
import kr.htaxlog.shared.InboxKind
import kr.htaxlog.shared.InboxPayload
import kr.htaxlog.shared.ScheduleDto
import kr.htaxlog.shared.SchedulesPayload
import kr.htaxlog.shared.UserDto
import kr.htaxlog.shared.UsersPayload
import kr.htaxlog.vehicle.core.ConnectionState
import kr.htaxlog.vehicle.core.Origin
import kr.htaxlog.vehicle.core.SyncResult
import kr.htaxlog.vehicle.core.UserInfo
import kr.htaxlog.vehicle.fake.FakeReceiptApi
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** 서버 우체통 → Room (실제 SQLite + 에뮬레이터 안 MockWebServer). */
@RunWith(AndroidJUnit4::class)
class InboxSyncTest {
    private val server = MockWebServer()
    private val acks = CopyOnWriteArrayList<String>()
    private var items: List<InboxItem> = emptyList()
    private lateinit var db: HTaxLogDb
    private lateinit var device: DeviceRepository
    private lateinit var sync: InboxSync
    private lateinit var store: RoomDomainStore

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/v1/inbox" -> MockResponse().setBody(
                    HTaxLogJson.encodeToString(ListSerializer(InboxItem.serializer()), items),
                )
                request.path!!.endsWith("/ack") -> {
                    acks += request.path!!
                    MockResponse().setResponseCode(200)
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, HTaxLogDb::class.java).build()
        val mem = object : DeviceStore {
            var r: DeviceRecord? = DeviceRecord(server.url("/").toString().trimEnd('/'), "tok", "car-1", "org-1")
            override fun load() = r
            override fun save(record: DeviceRecord) {
                r = record
            }
            override fun clear() {
                r = null
            }
        }
        device = DeviceRepository(mem, RelayHttp())
        sync = InboxSync(device, db.referenceDao(), db.expenseDao(), db.inboxDao())
        store = RoomDomainStore(db.tripDao(), db.referenceDao(), db.expenseDao(), ackRemote = { id ->
            device.call { u, t -> ack(u, t, id) } is RelayResult.Ok
        })
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    private fun item(id: Long, kind: InboxKind, payload: InboxPayload) =
        InboxItem(id, kind, payload, "2026-11-09T09:00:00+09:00")

    /** 직원 배포: 서버분은 교체되고 차 안 입력분은 남으며, 편지는 ack된다. */
    @Test
    fun usersReplaceServerCopyKeepLocalAndAck(): Unit = runBlocking {
        store.saveUser(UserInfo("u-local", "직접입력"))
        items = listOf(item(1, InboxKind.USERS, UsersPayload(listOf(UserDto("u-1", "김영업", "영업1팀", null)))))

        val r = sync.syncNow()

        assertEquals(SyncResult.Ok(1), r)
        assertEquals(
            setOf("u-1" to Origin.SERVER, "u-local" to Origin.LOCAL),
            store.users().first().map {
                it.userId to
                    it.origin
            }.toSet(),
        )
        assertEquals(listOf("/v1/inbox/1/ack"), acks)
        assertTrue((device.connection.value as ConnectionState.Registered).lastSyncAt != null)
    }

    /** 배차 시각(ISO +09:00)은 epoch millis로 들어간다. */
    @Test
    fun schedulesParseIsoTime(): Unit = runBlocking {
        items = listOf(
            item(
                2,
                InboxKind.SCHEDULES,
                SchedulesPayload(
                    listOf(
                        ScheduleDto(
                            "s-1",
                            "car-1",
                            "u-1",
                            "2026-11-10T09:00:00+09:00",
                            "2026-11-10T12:00:00+09:00",
                            null,
                            null,
                            "CLIENT_VISIT",
                        ),
                    ),
                ),
            ),
        )

        sync.syncNow()

        val from = java.time.Instant.parse("2026-11-10T00:00:00Z").toEpochMilli()
        assertEquals(listOf("s-1"), store.schedules("car-1", from, from + 1).map { it.scheduleId })
    }

    /** 영수증은 확인 대기 목록에 들어가고 확정 전에는 ack하지 않는다. 확정하면 ack된다. */
    @Test
    fun receiptWaitsForConfirmationBeforeAck(): Unit = runBlocking {
        items = listOf(item(3, InboxKind.RECEIPT, FakeReceiptApi.SAMPLE_RECEIPT))
        val local = RoomLocalStore(db.tripDao(), db.expenseDao())

        sync.syncNow()
        assertEquals(1, local.receiptInbox.first().size)
        assertTrue(acks.isEmpty())

        store.ackReceipt(FakeReceiptApi.SAMPLE_RECEIPT.receiptId)

        assertEquals(listOf("/v1/inbox/3/ack"), acks)
        assertTrue(local.receiptInbox.first().isEmpty())
    }

    /** 같은 편지가 다시 와도(ack 실패 후) 두 번 반영하지 않고 ack만 다시 한다. */
    @Test
    fun seenLetterIsNotAppliedTwice(): Unit = runBlocking {
        items = listOf(item(4, InboxKind.USERS, UsersPayload(listOf(UserDto("u-1", "김영업", null, null)))))
        sync.syncNow()
        store.saveUser(UserInfo("u-1", "이름 고침"))

        val r = sync.syncNow()

        assertEquals(SyncResult.Ok(1), r)
        assertEquals("이름 고침", store.users().first().single { it.userId == "u-1" }.name)
        assertEquals(2, acks.size)
    }

    /** 서버가 꺼져 있으면 실패를 돌려주고 연결 불가로 표시할 뿐이다. */
    @Test
    fun serverDownFailsQuietly(): Unit = runBlocking {
        server.shutdown()

        val r = sync.syncNow()

        assertTrue(r is SyncResult.Failed)
        assertTrue(device.connection.value is ConnectionState.Unreachable)
    }
}
