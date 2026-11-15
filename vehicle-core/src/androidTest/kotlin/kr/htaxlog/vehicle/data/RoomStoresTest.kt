package kr.htaxlog.vehicle.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kr.htaxlog.shared.HTaxLogJson
import kr.htaxlog.shared.PlaceKind
import kr.htaxlog.shared.ReceiptPayload
import kr.htaxlog.vehicle.core.ExpenseDraft
import kr.htaxlog.vehicle.core.ExpenseSource
import kr.htaxlog.vehicle.core.Origin
import kr.htaxlog.vehicle.core.PurposeDetail
import kr.htaxlog.vehicle.core.RecipientInfo
import kr.htaxlog.vehicle.core.UserInfo
import kr.htaxlog.vehicle.fake.FakeReceiptApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.YearMonth

/** RoomDomainStore·RoomLocalStore (실제 SQLite). 기준 동작은 fake/InMemoryDomainStore와 Contracts KDoc. */
@RunWith(AndroidJUnit4::class)
class RoomStoresTest {
    private lateinit var db: HTaxLogDb
    private lateinit var store: RoomDomainStore
    private lateinit var local: RoomLocalStore
    private var serverAck = true
    private val acked = mutableListOf<Long>()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, HTaxLogDb::class.java).build()
        store = RoomDomainStore(db.tripDao(), db.referenceDao(), db.expenseDao(), ackRemote = { id ->
            acked += id
            serverAck
        })
        local = RoomLocalStore(db.tripDao(), db.expenseDao())
    }

    @After
    fun tearDown() = db.close()

    /** 서버 배포분은 통째로 바뀌고 차 안 입력분은 남는다 (F-11, F-24). */
    @Test
    fun serverReplaceKeepsLocalUsers(): Unit = runBlocking {
        store.saveUser(UserInfo("u-local", "직접입력", null, null))
        db.referenceDao().replaceServerUsers(listOf(UserEntity("u-1", "김영업", "영업1팀", null, Origin.SERVER)))
        db.referenceDao().replaceServerUsers(listOf(UserEntity("u-2", "이현장", null, null, Origin.SERVER)))

        val users = store.users().first()

        assertEquals(listOf("u-2", "u-local"), users.map { it.userId }.sorted())
        assertEquals(Origin.LOCAL, users.first { it.userId == "u-local" }.origin)
    }

    /** 수신자는 서버분 먼저, 직접 입력분 삭제는 서버분을 건드리지 않는다. */
    @Test
    fun recipientsServerFirstAndLocalDeleteOnly(): Unit = runBlocking {
        store.saveRecipient(RecipientInfo("a@local.kr", "로컬"))
        db.referenceDao().replaceServerRecipients(listOf(RecipientEntity("z@server.kr", "회계", Origin.SERVER)))

        store.deleteLocalRecipient("z@server.kr")
        val list = store.recipients().first()

        assertEquals(listOf("z@server.kr", "a@local.kr"), list.map { it.email })
    }

    /** 배차는 기간이 겹치는 이 차 것만, 운전자 없는 배차는 뺀다. */
    @Test
    fun schedulesOverlapForThisCar(): Unit = runBlocking {
        db.referenceDao().replaceSchedules(
            listOf(
                ScheduleEntity("s-1", "car-1", "u-1", 1_000, 2_000, null, "CLIENT_VISIT"),
                ScheduleEntity("s-2", "car-2", "u-2", 1_000, 2_000, null, null),
                ScheduleEntity("s-3", "car-1", null, 1_000, 2_000, null, null),
                ScheduleEntity("s-4", "car-1", "u-3", 5_000, 6_000, null, null),
            ),
        )

        assertEquals(listOf("s-1"), store.schedules("car-1", 1_500, 3_000).map { it.scheduleId })
    }

    /** 운행 요약에는 사람이 확정한 목적만 들어가고, 정차 라벨이 붙는다. */
    @Test
    fun tripSummaryUsesConfirmedPurposeOnly(): Unit = runBlocking {
        val dao = db.tripDao()
        dao.upsert(
            trip("t-1", 1_000).copy(purposeDetail = "CLIENT_VISIT", purposeSource = PurposeSource.USER_CONFIRMED),
        )
        dao.upsert(
            trip("t-2", 2_000).copy(suggestedPurpose = "COMMUTE_IN", purposeSource = PurposeSource.RULE_SUGGESTED),
        )
        dao.insertStop(
            StopEntity(tripId = "t-1", startTime = 1_500, endTime = 1_600, odometerKm = null, label = "SK 판교주유소"),
        )

        val trips = store.trips(0, 2_000)

        assertEquals(PurposeDetail.CLIENT_VISIT, trips[0].confirmedPurpose)
        assertEquals(listOf("SK 판교주유소"), trips[0].stopLabels)
        assertNull(trips[1].confirmedPurpose)
    }

    /** 비용은 운행에 연결되고, 같은 영수증은 중복으로 알아본다. 카드는 끝 4자리만. */
    @Test
    fun expenseIsLinkedAndDeduplicated(): Unit = runBlocking {
        val id = store.saveExpense(
            ExpenseDraft(
                tripId = "t-1", occurredAt = 1_000, amountWon = 68_000, liters = 40.0, unitPrice = 1_700,
                discountWon = 0, fuelType = "휘발유", merchant = "SK 판교", cardLast4 = "**** 1234",
                receiptSha256 = "abc", ocrConfidence = mapOf("amountWon" to 0.98), editedFields = setOf("liters"),
            ),
        )

        assertTrue(store.hasReceipt("abc"))
        assertFalse(store.hasReceipt("zzz"))
        assertEquals(listOf(id), db.expenseDao().linksForTrip("t-1").map { it.expenseId })
        val saved = db.expenseDao().expensesBetween(0, 2_000).single()
        assertEquals("1234", saved.cardLast4)
        assertEquals(ExpenseSource.OCR, saved.source)
    }

    /** 영수증을 확정하면 화면에서 빠지고, 서버 ack가 성공하면 지운다. */
    @Test
    fun ackedReceiptLeavesInbox(): Unit = runBlocking {
        putReceipt(FakeReceiptApi.SAMPLE_RECEIPT, inboxId = 7)
        assertEquals(1, local.receiptInbox.first().size)

        store.ackReceipt(FakeReceiptApi.SAMPLE_RECEIPT.receiptId)

        assertTrue(local.receiptInbox.first().isEmpty())
        assertEquals(listOf(7L), acked)
        assertNull(db.expenseDao().receipt(FakeReceiptApi.SAMPLE_RECEIPT.receiptId))
    }

    /** 서버 ack가 실패하면 확정 표시만 남겨 다음 동기화에 다시 보낸다. */
    @Test
    fun failedAckIsRetriedLater(): Unit = runBlocking {
        serverAck = false
        putReceipt(FakeReceiptApi.SAMPLE_RECEIPT, inboxId = 7)

        store.ackReceipt(FakeReceiptApi.SAMPLE_RECEIPT.receiptId)

        assertTrue(local.receiptInbox.first().isEmpty())
        assertEquals(1, db.expenseDao().confirmedReceipts().size)
    }

    /** 달 구분은 한국 시간 기준: 10/31 15:30Z = 11/01 00:30 KST는 11월이다. */
    @Test
    fun monthIsSeoulTime(): Unit = runBlocking {
        val kstNovFirst = Instant.parse("2026-10-31T15:30:00Z").toEpochMilli()
        db.tripDao().upsert(trip("t-nov", kstNovFirst))

        assertEquals(listOf("t-nov"), local.trips(YearMonth.of(2026, 11)).first().map { it.tripId })
        assertTrue(local.trips(YearMonth.of(2026, 10)).first().isEmpty())
    }

    /** 화면 행: 목적은 확정분만, 거리는 고친 값이 있으면 그것. */
    @Test
    fun tripRowShowsConfirmedPurposeAndEditedDistance(): Unit = runBlocking {
        db.tripDao().upsert(
            trip("t-1", 1_000).copy(distanceKm = 23.4, editedDistanceKm = 24.0, suggestedPurpose = "CLIENT_VISIT"),
        )

        val row = local.trip("t-1")!!

        assertNull(row.purpose)
        assertEquals(24.0, row.distanceKm!!, 0.0)
        assertEquals(listOf("t-1"), local.pendingReviews.first().map { it.tripId })
    }

    @Test
    fun localPlaceIsSavedAsLocal(): Unit = runBlocking {
        store.savePlace(kr.htaxlog.vehicle.core.PlaceInfo("p-1", "판교 A고객사", PlaceKind.CLIENT, 37.4, 127.1))

        assertEquals(Origin.LOCAL, store.places().single().origin)
    }

    private suspend fun putReceipt(p: ReceiptPayload, inboxId: Long) = db.expenseDao().upsertReceipt(
        ReceiptInboxEntity(p.receiptId, HTaxLogJson.encodeToString(ReceiptPayload.serializer(), p), 1_000, inboxId),
    )

    private fun trip(id: String, start: Long) = TripEntity(
        tripId = id,
        vehicleId = "car-1",
        userId = "u-1",
        startTime = start,
        endTime = start + 60_000,
        startOdometerKm = 100.0,
        status = TripStatus.PENDING_REVIEW,
    )
}
