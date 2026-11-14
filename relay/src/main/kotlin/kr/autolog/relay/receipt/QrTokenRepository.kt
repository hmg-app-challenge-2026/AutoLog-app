package kr.autolog.relay.receipt

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

interface QrTokenRepository {
    fun insert(tokenHash: String, vehicleId: UUID, expiresAt: Instant)

    /** 차량 코드·만료·미사용을 확인하고 사용 처리한다. 성공하면 차량 id, 아니면 null (원자적). */
    fun consume(vehicleCode: String, tokenHash: String, now: Instant): UUID?

    /** 사용하지 않고 토큰이 유효한지만 본다(카메라 페이지 열기). */
    fun isValid(vehicleCode: String, tokenHash: String, now: Instant): Boolean

    /** 만료·사용 여부와 상관없이 이 차의 토큰이면 차량 id. 다시 보내기가 이미 들어간 사진인지 볼 때 쓴다. */
    fun vehicleOf(vehicleCode: String, tokenHash: String): UUID?
}

@Repository
class JdbcQrTokenRepository(private val jdbc: JdbcClient) : QrTokenRepository {
    override fun insert(tokenHash: String, vehicleId: UUID, expiresAt: Instant) {
        jdbc
            .sql("INSERT INTO qr_tokens (token, vehicle_id, expires_at) VALUES (:token, :vehicleId, :expiresAt)")
            .param("token", tokenHash)
            .param("vehicleId", vehicleId)
            .param("expiresAt", Timestamp.from(expiresAt))
            .update()
    }

    override fun consume(vehicleCode: String, tokenHash: String, now: Instant): UUID? = jdbc
        .sql(
            """
                UPDATE qr_tokens q SET used_at = :now
                FROM vehicles v
                WHERE q.token = :token AND q.used_at IS NULL AND q.expires_at > :now
                  AND v.vehicle_id = q.vehicle_id AND v.vehicle_code = :code
                RETURNING q.vehicle_id
            """.trimIndent(),
        ).param("now", Timestamp.from(now))
        .param("token", tokenHash)
        .param("code", vehicleCode)
        .query(UUID::class.java)
        .optional()
        .orElse(null)

    override fun isValid(vehicleCode: String, tokenHash: String, now: Instant): Boolean = jdbc
        .sql(
            """
                SELECT count(*) FROM qr_tokens q JOIN vehicles v ON v.vehicle_id = q.vehicle_id
                WHERE q.token = :token AND q.used_at IS NULL AND q.expires_at > :now AND v.vehicle_code = :code
            """.trimIndent(),
        ).param("token", tokenHash)
        .param("now", Timestamp.from(now))
        .param("code", vehicleCode)
        .query(Long::class.java)
        .single() > 0

    override fun vehicleOf(vehicleCode: String, tokenHash: String): UUID? = jdbc
        .sql(
            """
                SELECT q.vehicle_id FROM qr_tokens q JOIN vehicles v ON v.vehicle_id = q.vehicle_id
                WHERE q.token = :token AND v.vehicle_code = :code
            """.trimIndent(),
        ).param("token", tokenHash)
        .param("code", vehicleCode)
        .query(UUID::class.java)
        .optional()
        .orElse(null)
}
