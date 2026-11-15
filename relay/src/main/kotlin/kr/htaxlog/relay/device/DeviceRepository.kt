package kr.htaxlog.relay.device

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class ConnectCode(val code: String, val vehicleId: UUID, val expiresAt: Instant, val usedAt: Instant?)

data class DeviceVehicle(
    val vehicleId: UUID,
    val orgId: UUID,
    val vehicleCode: String,
    val plateNo: String? = null,
    val model: String? = null,
)

interface DeviceRepository {
    fun findConnectCode(code: String): ConnectCode?

    /** 코드를 사용 처리한다. 이미 쓰였으면 false (동시에 두 번 써도 한 번만 성공). */
    fun markCodeUsed(code: String, at: Instant): Boolean

    fun saveDeviceToken(vehicleId: UUID, tokenHash: String, at: Instant): DeviceVehicle

    fun findByTokenHash(tokenHash: String): DeviceVehicle?
}

@Repository
class JdbcDeviceRepository(private val jdbc: JdbcClient) : DeviceRepository {
    override fun findConnectCode(code: String): ConnectCode? = jdbc
        .sql("SELECT code, vehicle_id, expires_at, used_at FROM connect_codes WHERE code = :code")
        .param("code", code)
        .query { rs, _ ->
            ConnectCode(
                code = rs.getString("code"),
                vehicleId = rs.getObject("vehicle_id", UUID::class.java),
                expiresAt = rs.getTimestamp("expires_at").toInstant(),
                usedAt = rs.getTimestamp("used_at")?.toInstant(),
            )
        }.optional()
        .orElse(null)

    override fun markCodeUsed(code: String, at: Instant): Boolean = jdbc
        .sql("UPDATE connect_codes SET used_at = :at WHERE code = :code AND used_at IS NULL")
        .param("at", Timestamp.from(at))
        .param("code", code)
        .update() == 1

    override fun saveDeviceToken(vehicleId: UUID, tokenHash: String, at: Instant): DeviceVehicle = jdbc
        .sql(
            """
                UPDATE vehicles SET device_token_hash = :hash, registered_at = :at
                WHERE vehicle_id = :id RETURNING vehicle_id, org_id, vehicle_code, plate_no, model
            """.trimIndent(),
        ).param("hash", tokenHash)
        .param("at", Timestamp.from(at))
        .param("id", vehicleId)
        .query(::mapVehicle)
        .single()

    override fun findByTokenHash(tokenHash: String): DeviceVehicle? = jdbc
        .sql("SELECT vehicle_id, org_id, vehicle_code, plate_no, model FROM vehicles WHERE device_token_hash = :hash")
        .param("hash", tokenHash)
        .query(::mapVehicle)
        .optional()
        .orElse(null)

    private fun mapVehicle(rs: java.sql.ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = DeviceVehicle(
        vehicleId = rs.getObject("vehicle_id", UUID::class.java),
        orgId = rs.getObject("org_id", UUID::class.java),
        vehicleCode = rs.getString("vehicle_code"),
        plateNo = rs.getString("plate_no"),
        model = rs.getString("model"),
    )
}
