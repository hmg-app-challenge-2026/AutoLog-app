package kr.autolog.relay.inbox

import kr.autolog.relay.common.ApiException
import kr.autolog.relay.common.RelayProperties
import kr.autolog.shared.AutoLogJson
import kr.autolog.shared.InboxItem
import kr.autolog.shared.InboxKind
import kr.autolog.shared.InboxPayload
import kr.autolog.shared.MailAccountPayload
import kr.autolog.shared.PlacesPayload
import kr.autolog.shared.PurposesPayload
import kr.autolog.shared.ReceiptPayload
import kr.autolog.shared.RecipientsPayload
import kr.autolog.shared.SchedulesPayload
import kr.autolog.shared.UsersPayload
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 차가 편지를 가져갈 때 내용을 새로 고친다. 예: 영수증 썸네일 서명 URL은 7일짜리라
 * 편지(30일 보관)를 넣을 때가 아니라 가져갈 때 새로 서명한다.
 */
fun interface InboxPayloadRefresher {
    fun refresh(vehicleId: UUID, payload: InboxPayload): InboxPayload
}

/**
 * 서버 → 차 우체통 (F-42, F-43). 차 → 서버 방향은 없다.
 *
 * - RECEIPT: 쌓인다(영수증마다 1통).
 * - USERS·PLACES·SCHEDULES·RECIPIENTS·MAIL_ACCOUNT·PURPOSES: 항상 전체(최신) 값이라, 차가 아직 안 가져간 이전 편지는 지우고 최신 1통만 둔다.
 */
@Service
class InboxService(
    private val repo: InboxRepository,
    private val props: RelayProperties,
    private val clock: Clock,
    private val refreshers: List<InboxPayloadRefresher> = emptyList(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun deliver(vehicleId: UUID, payload: InboxPayload): Long {
        val kind = kindOf(payload)
        if (kind != InboxKind.RECEIPT) repo.deletePending(vehicleId, kind.name)
        val now = clock.instant()
        val json = AutoLogJson.encodeToString(InboxPayload.serializer(), payload)
        return repo.insert(vehicleId, kind.name, json, now, now.plus(Duration.ofDays(props.inboxRetentionDays)))
    }

    @Transactional
    fun pending(vehicleId: UUID): List<InboxItem> {
        val now = clock.instant()
        repo.touchLastInbox(vehicleId, now)
        return repo.findPending(vehicleId, now).map { row ->
            InboxItem(
                id = row.id,
                kind = InboxKind.valueOf(row.kind),
                payload = refresh(vehicleId, AutoLogJson.decodeFromString(InboxPayload.serializer(), row.payloadJson)),
                createdAt = KST_ISO.format(row.createdAt.atOffset(KST)),
            )
        }
    }

    private fun refresh(vehicleId: UUID, payload: InboxPayload): InboxPayload =
        refreshers.fold(payload) { p, r -> r.refresh(vehicleId, p) }

    fun ack(vehicleId: UUID, id: Long) {
        if (!repo.delete(id, vehicleId)) {
            throw ApiException(HttpStatus.NOT_FOUND, "INBOX_NOT_FOUND", "편지가 없거나 이미 확인됐습니다")
        }
    }

    /** ack 없이 30일 지난 편지 삭제 (매일 04:00 KST). */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    fun purgeExpired() {
        val n = repo.deleteExpired(clock.instant())
        if (n > 0) log.info("inbox 만료 삭제 {}건", n)
    }

    companion object {
        private val KST: ZoneOffset = ZoneOffset.ofHours(9)
        private val KST_ISO: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        fun kindOf(payload: InboxPayload): InboxKind = when (payload) {
            is ReceiptPayload -> InboxKind.RECEIPT
            is UsersPayload -> InboxKind.USERS
            is PlacesPayload -> InboxKind.PLACES
            is SchedulesPayload -> InboxKind.SCHEDULES
            is RecipientsPayload -> InboxKind.RECIPIENTS
            is MailAccountPayload -> InboxKind.MAIL_ACCOUNT
            is PurposesPayload -> InboxKind.PURPOSES
        }
    }
}
