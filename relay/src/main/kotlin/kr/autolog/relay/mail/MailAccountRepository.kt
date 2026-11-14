package kr.autolog.relay.mail

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/** DB 한 행. [passwordEnc]는 [MailSecret]으로 암호화된 값. */
data class MailAccountRow(
    val orgId: UUID,
    val host: String,
    val port: Int,
    val username: String,
    val passwordEnc: String,
    val fromName: String?,
    val startTls: Boolean,
    val updatedAt: Instant,
)

@Repository
class MailAccountRepository(private val jdbc: JdbcClient) {
    fun find(orgId: UUID): MailAccountRow? = jdbc
        .sql("SELECT * FROM org_mail_accounts WHERE org_id = :org")
        .param("org", orgId)
        .query { rs, _ -> rs.toRow() }
        .optional()
        .orElse(null)

    /** 차가 속한 회사의 계정. 차가 없거나 회사에 계정이 없으면 null. */
    fun findByVehicle(vehicleId: UUID): MailAccountRow? = jdbc
        .sql(
            """
            SELECT m.* FROM org_mail_accounts m JOIN vehicles v ON v.org_id = m.org_id
            WHERE v.vehicle_id = :vehicle
            """.trimIndent(),
        ).param("vehicle", vehicleId)
        .query { rs, _ -> rs.toRow() }
        .optional()
        .orElse(null)

    fun upsert(row: MailAccountRow) {
        jdbc
            .sql(
                """
                INSERT INTO org_mail_accounts (org_id, host, port, username, password_enc, from_name, start_tls, updated_at)
                VALUES (:org, :host, :port, :username, :password, :fromName, :startTls, :updatedAt)
                ON CONFLICT (org_id) DO UPDATE SET
                    host = EXCLUDED.host, port = EXCLUDED.port, username = EXCLUDED.username,
                    password_enc = EXCLUDED.password_enc, from_name = EXCLUDED.from_name,
                    start_tls = EXCLUDED.start_tls, updated_at = EXCLUDED.updated_at
                """.trimIndent(),
            ).param("org", row.orgId)
            .param("host", row.host)
            .param("port", row.port)
            .param("username", row.username)
            .param("password", row.passwordEnc)
            .param("fromName", row.fromName)
            .param("startTls", row.startTls)
            .param("updatedAt", Timestamp.from(row.updatedAt))
            .update()
    }

    fun delete(orgId: UUID): Boolean =
        jdbc.sql("DELETE FROM org_mail_accounts WHERE org_id = :org").param("org", orgId).update() > 0

    private fun ResultSet.toRow() = MailAccountRow(
        orgId = getObject("org_id", UUID::class.java),
        host = getString("host"),
        port = getInt("port"),
        username = getString("username"),
        passwordEnc = getString("password_enc"),
        fromName = getString("from_name"),
        startTls = getBoolean("start_tls"),
        updatedAt = getTimestamp("updated_at").toInstant(),
    )
}
