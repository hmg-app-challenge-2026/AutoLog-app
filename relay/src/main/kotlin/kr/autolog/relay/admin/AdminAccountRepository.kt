package kr.autolog.relay.admin

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class Organization(val orgId: UUID, val name: String, val adminCount: Int, val vehicleCount: Int)

data class AdminAccount(val adminId: UUID, val orgId: UUID, val email: String, val passwordHash: String)

data class AdminSummary(val adminId: UUID, val email: String, val createdAt: Instant?)

data class AdminSession(val adminId: UUID, val orgId: UUID, val email: String, val orgName: String)

/** 회사·관리자·로그인 세션 (V3). 세션 토큰은 sha256 해시로만 다룬다. */
@Repository
class AdminAccountRepository(private val jdbc: JdbcClient) {
    fun orgs(): List<Organization> = jdbc
        .sql(
            """
                SELECT o.org_id, o.name,
                       (SELECT count(*) FROM admins a WHERE a.org_id = o.org_id) AS admins,
                       (SELECT count(*) FROM vehicles v WHERE v.org_id = o.org_id) AS vehicles
                FROM organizations o ORDER BY o.name
            """.trimIndent(),
        ).query { rs, _ ->
            Organization(
                rs.getObject("org_id", UUID::class.java),
                rs.getString("name"),
                rs.getInt("admins"),
                rs.getInt("vehicles"),
            )
        }.list()

    fun orgExists(orgId: UUID): Boolean = count("SELECT count(*) FROM organizations WHERE org_id = :v", orgId) > 0

    fun orgNameTaken(name: String): Boolean = count("SELECT count(*) FROM organizations WHERE name = :v", name) > 0

    fun insertOrg(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql(
            "INSERT INTO organizations (org_id, name) VALUES (:id, :name)",
        ).param("id", id).param("name", name).update()
    }

    fun emailTaken(email: String): Boolean = count("SELECT count(*) FROM admins WHERE email = :v", email) > 0

    fun insertAdmin(orgId: UUID, email: String, passwordHash: String): UUID = UUID.randomUUID().also { id ->
        jdbc
            .sql("INSERT INTO admins (admin_id, org_id, email, password_hash) VALUES (:id, :org, :email, :hash)")
            .param("id", id)
            .param("org", orgId)
            .param("email", email)
            .param("hash", passwordHash)
            .update()
    }

    fun admins(orgId: UUID): List<AdminSummary> = jdbc
        .sql("SELECT admin_id, email, created_at FROM admins WHERE org_id = :org ORDER BY email")
        .param("org", orgId)
        .query { rs, _ ->
            AdminSummary(
                rs.getObject("admin_id", UUID::class.java),
                rs.getString("email"),
                rs.getTimestamp("created_at")?.toInstant(),
            )
        }.list()

    fun adminByEmail(email: String): AdminAccount? = jdbc
        .sql("SELECT admin_id, org_id, email, password_hash FROM admins WHERE email = :email")
        .param("email", email)
        .query { rs, _ -> account(rs) }
        .optional()
        .orElse(null)

    fun adminById(adminId: UUID): AdminAccount? = jdbc
        .sql("SELECT admin_id, org_id, email, password_hash FROM admins WHERE admin_id = :id")
        .param("id", adminId)
        .query { rs, _ -> account(rs) }
        .optional()
        .orElse(null)

    fun updatePassword(adminId: UUID, passwordHash: String): Boolean = jdbc
        .sql("UPDATE admins SET password_hash = :hash WHERE admin_id = :id")
        .param("hash", passwordHash)
        .param("id", adminId)
        .update() > 0

    fun insertSession(tokenHash: String, adminId: UUID, orgId: UUID, expiresAt: Instant) {
        jdbc
            .sql("INSERT INTO admin_sessions (token_hash, admin_id, org_id, expires_at) VALUES (:t, :a, :o, :e)")
            .param("t", tokenHash)
            .param("a", adminId)
            .param("o", orgId)
            .param("e", Timestamp.from(expiresAt))
            .update()
    }

    /** 만료 전 세션. 관리자 이메일·회사 이름까지 함께. */
    fun session(tokenHash: String, now: Instant): AdminSession? = jdbc
        .sql(
            """
                SELECT s.admin_id, s.org_id, a.email, o.name
                FROM admin_sessions s
                JOIN admins a ON a.admin_id = s.admin_id
                JOIN organizations o ON o.org_id = s.org_id
                WHERE s.token_hash = :t AND s.expires_at > :now
            """.trimIndent(),
        ).param("t", tokenHash)
        .param("now", Timestamp.from(now))
        .query { rs, _ ->
            AdminSession(
                rs.getObject("admin_id", UUID::class.java),
                rs.getObject("org_id", UUID::class.java),
                rs.getString("email"),
                rs.getString("name"),
            )
        }.optional()
        .orElse(null)

    fun deleteSession(tokenHash: String) {
        jdbc.sql("DELETE FROM admin_sessions WHERE token_hash = :t").param("t", tokenHash).update()
    }

    /** 그 관리자의 세션을 끊는다. [keep]은 남길 세션 해시(지금 쓰는 것), null이면 전부. */
    fun deleteSessions(adminId: UUID, keep: String? = null) {
        if (keep == null) {
            jdbc.sql("DELETE FROM admin_sessions WHERE admin_id = :a").param("a", adminId).update()
        } else {
            jdbc
                .sql("DELETE FROM admin_sessions WHERE admin_id = :a AND token_hash <> :keep")
                .param("a", adminId)
                .param("keep", keep)
                .update()
        }
    }

    fun deleteExpiredSessions(now: Instant): Int = jdbc
        .sql("DELETE FROM admin_sessions WHERE expires_at <= :now")
        .param("now", Timestamp.from(now))
        .update()

    private fun count(sql: String, value: Any): Long = jdbc.sql(sql).param("v", value).query(Long::class.java).single()

    private fun account(rs: ResultSet) = AdminAccount(
        rs.getObject("admin_id", UUID::class.java),
        rs.getObject("org_id", UUID::class.java),
        rs.getString("email"),
        rs.getString("password_hash"),
    )
}
