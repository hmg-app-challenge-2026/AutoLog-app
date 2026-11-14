package kr.autolog.relay.admin

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class AdminVehicle(
    val vehicleId: UUID,
    val plateNo: String,
    val model: String?,
    val vehicleCode: String,
    val registered: Boolean,
    val lastInboxAt: Instant?,
)

@Repository
class AdminVehicleRepository(private val jdbc: JdbcClient) {
    fun insertVehicle(orgId: UUID, plateNo: String, model: String?, vehicleCode: String): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                "INSERT INTO vehicles (vehicle_id, org_id, plate_no, model, vehicle_code) VALUES (:id, :org, :plate, :model, :code)",
            )
            .param("id", id)
            .param("org", orgId)
            .param("plate", plateNo)
            .param("model", model)
            .param("code", vehicleCode)
            .update()
        return id
    }

    fun list(orgId: UUID): List<AdminVehicle> = jdbc
        .sql(
            """
                SELECT vehicle_id, plate_no, model, vehicle_code, device_token_hash IS NOT NULL AS registered, last_inbox_at
                FROM vehicles WHERE org_id = :org ORDER BY plate_no
            """.trimIndent(),
        ).param("org", orgId)
        .query { rs, _ ->
            AdminVehicle(
                vehicleId = rs.getObject("vehicle_id", UUID::class.java),
                plateNo = rs.getString("plate_no"),
                model = rs.getString("model"),
                vehicleCode = rs.getString("vehicle_code"),
                registered = rs.getBoolean("registered"),
                lastInboxAt = rs.getTimestamp("last_inbox_at")?.toInstant(),
            )
        }.list()

    fun exists(orgId: UUID, vehicleId: UUID): Boolean = jdbc
        .sql("SELECT count(*) FROM vehicles WHERE org_id = :org AND vehicle_id = :id")
        .param("org", orgId)
        .param("id", vehicleId)
        .query(Long::class.java)
        .single() > 0

    fun insertConnectCode(code: String, vehicleId: UUID, expiresAt: Instant) {
        jdbc
            .sql("INSERT INTO connect_codes (code, vehicle_id, expires_at) VALUES (:code, :id, :exp)")
            .param("code", code)
            .param("id", vehicleId)
            .param("exp", Timestamp.from(expiresAt))
            .update()
    }

    fun revokeDevice(vehicleId: UUID) {
        jdbc.sql("UPDATE vehicles SET device_token_hash = NULL WHERE vehicle_id = :id").param("id", vehicleId).update()
    }

    /**
     * 차량과 그 차에 딸린 서버 데이터(배차·영수증·inbox·QR·연결 코드)를 한 번에 지운다.
     * 지운 영수증의 저장소 키(원본·썸네일)를 돌려준다 — 파일은 호출한 쪽이 커밋 뒤에 지운다.
     */
    @Transactional
    fun deleteVehicle(vehicleId: UUID): List<String> {
        val keys = jdbc
            .sql("SELECT storage_key, thumb_key FROM receipts WHERE vehicle_id = :id")
            .param("id", vehicleId)
            .query { rs, _ -> listOfNotNull(rs.getString("storage_key"), rs.getString("thumb_key")) }
            .list()
            .flatten()
            .distinct()
        listOf("receipts", "inbox", "schedules", "qr_tokens", "connect_codes", "vehicles").forEach { table ->
            jdbc.sql("DELETE FROM $table WHERE vehicle_id = :id").param("id", vehicleId).update()
        }
        return keys
    }
}
