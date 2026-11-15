package kr.htaxlog.vehicle.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kr.htaxlog.vehicle.core.DomainStore
import kr.htaxlog.vehicle.core.ExpenseDraft
import kr.htaxlog.vehicle.core.Origin
import kr.htaxlog.vehicle.core.PlaceInfo
import kr.htaxlog.vehicle.core.QrResult
import kr.htaxlog.vehicle.core.RecentUse
import kr.htaxlog.vehicle.core.RecipientInfo
import kr.htaxlog.vehicle.core.RelayClient
import kr.htaxlog.vehicle.core.ScheduleInfo
import kr.htaxlog.vehicle.core.SyncResult
import kr.htaxlog.vehicle.core.TripSummary
import kr.htaxlog.vehicle.core.UserInfo

/**
 * [DomainStore] 메모리 구현. 앱 B의 테스트와, 앱 A가 Room 구현을 만들 때 동작 기준으로 쓴다.
 * 필드를 직접 채워 상황을 만든다.
 */
class InMemoryDomainStore : DomainStore {
    val userList = MutableStateFlow<List<UserInfo>>(emptyList())
    val recent = mutableListOf<RecentUse>()
    val placeList = mutableListOf<PlaceInfo>()
    val scheduleList = mutableListOf<Pair<String, ScheduleInfo>>()
    val tripList = mutableListOf<TripSummary>()
    val recipientList = MutableStateFlow<List<RecipientInfo>>(emptyList())
    val expenses = mutableListOf<ExpenseDraft>()
    val acked = mutableListOf<String>()

    override fun users(): Flow<List<UserInfo>> = userList

    override suspend fun recentUsers(limit: Int): List<RecentUse> = recent.sortedByDescending {
        it.lastUsedAt
    }.take(limit)

    override suspend fun places(): List<PlaceInfo> = placeList.toList()

    override suspend fun schedules(vehicleId: String, from: Long, to: Long): List<ScheduleInfo> = scheduleList
        .filter { (v, s) -> v == vehicleId && s.startsAt <= to && s.endsAt >= from }
        .map { it.second }

    override suspend fun trips(from: Long, to: Long): List<TripSummary> =
        tripList.filter { it.startTime in from..to }.sortedBy { it.startTime }

    override fun recipients(): Flow<List<RecipientInfo>> = recipientList

    override suspend fun saveRecipient(r: RecipientInfo) {
        recipientList.value = recipientList.value.filterNot { it.email == r.email && it.origin == Origin.LOCAL } +
            r.copy(origin = Origin.LOCAL)
    }

    override suspend fun deleteLocalRecipient(email: String) {
        recipientList.value = recipientList.value.filterNot { it.email == email && it.origin == Origin.LOCAL }
    }

    override suspend fun saveUser(u: UserInfo) {
        userList.value = userList.value.filterNot { it.userId == u.userId } + u.copy(origin = Origin.LOCAL)
    }

    override suspend fun savePlace(p: PlaceInfo) {
        placeList.removeAll { it.placeId == p.placeId }
        placeList += p.copy(origin = Origin.LOCAL)
    }

    override suspend fun hasReceipt(sha256: String): Boolean = expenses.any { it.receiptSha256 == sha256 }

    override suspend fun saveExpense(e: ExpenseDraft): String {
        expenses += e
        return "exp-${expenses.size}"
    }

    override suspend fun ackReceipt(receiptId: String) {
        acked += receiptId
    }
}

/** [RelayClient] 가짜. 결과를 바꿔 끼워 서버 실패 상황을 만든다. */
class FakeRelayClient(
    var qr: QrResult = QrResult.Issued("https://htaxlog.example/r/abcd1234?t=demo", Long.MAX_VALUE),
    var sync: SyncResult = SyncResult.Ok(0),
) : RelayClient {
    var qrCalls = 0
        private set

    override suspend fun issueQrToken(): QrResult {
        qrCalls++
        return qr
    }

    var syncCalls = 0
        private set

    override suspend fun syncNow(): SyncResult {
        syncCalls++
        return sync
    }
}
