package kr.htaxlog.relay.receipt

import kr.htaxlog.relay.inbox.InboxPayloadRefresher
import kr.htaxlog.shared.InboxPayload
import kr.htaxlog.shared.ReceiptPayload
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * RECEIPT 편지의 썸네일 URL을 차가 가져가는 시점에 새로 서명한다.
 * 서명 URL은 7일(기능명세 F-41)인데 편지는 30일 보관이라, 넣을 때 서명하면 늦게 가져간 차는 사진을 못 연다.
 */
@Component
class ReceiptThumbUrlRefresher(private val repo: ReceiptRepository, private val storage: ReceiptStorage) :
    InboxPayloadRefresher {
    override fun refresh(vehicleId: UUID, payload: InboxPayload): InboxPayload {
        if (payload !is ReceiptPayload) return payload
        val id = runCatching { UUID.fromString(payload.receiptId) }.getOrNull() ?: return payload
        val key = repo.thumbKey(id, vehicleId) ?: return payload
        return payload.copy(thumbUrl = storage.signedUrl(key))
    }
}
