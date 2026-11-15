package kr.htaxlog.relay.inbox

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class InboxRow(
    val id: Long,
    val vehicleId: UUID,
    val kind: String,
    val payloadJson: String,
    val createdAt: Instant,
)

interface InboxRepository {
    fun insert(vehicleId: UUID, kind: String, payloadJson: String, createdAt: Instant, expiresAt: Instant): Long

    /** 아직 차가 가져가지 않은 같은 종류 편지를 지운다(기준 정보는 최신 1건만, F-43). */
    fun deletePending(vehicleId: UUID, kind: String): Int

    fun findPending(vehicleId: UUID, now: Instant): List<InboxRow>

    /** 내 차 편지만 지운다. 없거나 남의 차 편지면 false. */
    fun delete(id: Long, vehicleId: UUID): Boolean

    fun touchLastInbox(vehicleId: UUID, at: Instant)

    fun deleteExpired(now: Instant): Int
}

@Repository
class JdbcInboxRepository(private val jdbc: JdbcClient) : InboxRepository {
    override fun insert(
        vehicleId: UUID,
        kind: String,
        payloadJson: String,
        createdAt: Instant,
        expiresAt: Instant,
    ): Long {
        val keys = GeneratedKeyHolder()
        jdbc
            .sql(
                """
                INSERT INTO inbox (vehicle_id, kind, payload, created_at, expires_at)
                VALUES (:vehicleId, :kind, CAST(:payload AS jsonb), :createdAt, :expiresAt)
                """.trimIndent(),
            ).param("vehicleId", vehicleId)
            .param("kind", kind)
            .param("payload", payloadJson)
            .param("createdAt", Timestamp.from(createdAt))
            .param("expiresAt", Timestamp.from(expiresAt))
            .update(keys, "id")
        return keys.key!!.toLong()
    }

    override fun deletePending(vehicleId: UUID, kind: String): Int = jdbc
        .sql("DELETE FROM inbox WHERE vehicle_id = :vehicleId AND kind = :kind")
        .param("vehicleId", vehicleId)
        .param("kind", kind)
        .update()

    override fun findPending(vehicleId: UUID, now: Instant): List<InboxRow> = jdbc
        .sql(
            """
                SELECT id, vehicle_id, kind, payload::text AS payload, created_at FROM inbox
                WHERE vehicle_id = :vehicleId AND (expires_at IS NULL OR expires_at > :now)
                ORDER BY created_at, id
            """.trimIndent(),
        ).param("vehicleId", vehicleId)
        .param("now", Timestamp.from(now))
        .query { rs, _ ->
            InboxRow(
                id = rs.getLong("id"),
                vehicleId = rs.getObject("vehicle_id", UUID::class.java),
                kind = rs.getString("kind"),
                payloadJson = rs.getString("payload"),
                createdAt = rs.getTimestamp("created_at").toInstant(),
            )
        }.list()

    override fun delete(id: Long, vehicleId: UUID): Boolean = jdbc
        .sql("DELETE FROM inbox WHERE id = :id AND vehicle_id = :vehicleId")
        .param("id", id)
        .param("vehicleId", vehicleId)
        .update() == 1

    override fun touchLastInbox(vehicleId: UUID, at: Instant) {
        jdbc
            .sql("UPDATE vehicles SET last_inbox_at = :at WHERE vehicle_id = :vehicleId")
            .param("at", Timestamp.from(at))
            .param("vehicleId", vehicleId)
            .update()
    }

    override fun deleteExpired(now: Instant): Int =
        jdbc.sql("DELETE FROM inbox WHERE expires_at <= :now").param("now", Timestamp.from(now)).update()
}
