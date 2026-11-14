package kr.autolog.relay.purpose

import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

data class PurposeRow(val id: Long, val label: String, val category: String)

data class PurposeRuleRow(val ruleId: UUID, val originPlaceId: UUID?, val destPlaceId: UUID, val purpose: String)

/** 회사 목적·목적 규칙 (F-08). 모든 쿼리는 org_id 범위 안에서만 동작한다. */
@Repository
class PurposeRepository(private val jdbc: JdbcClient) {
    fun purposes(org: UUID): List<PurposeRow> = jdbc
        .sql("SELECT id, label, category FROM org_purposes WHERE org_id = :org ORDER BY created_at, id")
        .param("org", org)
        .query { rs, _ -> PurposeRow(rs.getLong("id"), rs.getString("label"), rs.getString("category")) }
        .list()

    /** 같은 이름이 있으면 false. */
    fun insertPurpose(org: UUID, label: String, category: String): Boolean = try {
        jdbc.sql("INSERT INTO org_purposes (org_id, label, category) VALUES (:org, :label, :category)")
            .param("org", org).param("label", label).param("category", category).update()
        true
    } catch (e: DuplicateKeyException) {
        false
    }

    fun purpose(org: UUID, id: Long): PurposeRow? = purposes(org).firstOrNull { it.id == id }

    fun deletePurpose(org: UUID, id: Long): Boolean = jdbc.sql(
        "DELETE FROM org_purposes WHERE org_id = :org AND id = :id",
    ).param("org", org).param("id", id).update() >
        0

    fun rules(org: UUID): List<PurposeRuleRow> = jdbc
        .sql(
            "SELECT rule_id, origin_place_id, dest_place_id, purpose FROM org_purpose_rules WHERE org_id = :org ORDER BY created_at",
        )
        .param("org", org)
        .query { rs, _ ->
            PurposeRuleRow(
                rs.getObject("rule_id", UUID::class.java),
                rs.getObject("origin_place_id", UUID::class.java),
                rs.getObject("dest_place_id", UUID::class.java),
                rs.getString("purpose"),
            )
        }.list()

    /** 같은 출발·도착 규칙이 있으면 false. */
    fun insertRule(org: UUID, row: PurposeRuleRow): Boolean = try {
        jdbc
            .sql(
                """
                INSERT INTO org_purpose_rules (rule_id, org_id, origin_place_id, dest_place_id, purpose)
                VALUES (:id, :org, :origin, :dest, :purpose)
                """.trimIndent(),
            ).param("id", row.ruleId)
            .param("org", org)
            .param("origin", row.originPlaceId)
            .param("dest", row.destPlaceId)
            .param("purpose", row.purpose)
            .update()
        true
    } catch (e: DuplicateKeyException) {
        false
    }

    fun deleteRule(org: UUID, id: UUID): Boolean = jdbc
        .sql("DELETE FROM org_purpose_rules WHERE org_id = :org AND rule_id = :id")
        .param("org", org).param("id", id).update() > 0

    fun deleteRulesUsing(org: UUID, purpose: String): Int = jdbc
        .sql("DELETE FROM org_purpose_rules WHERE org_id = :org AND purpose = :purpose")
        .param("org", org).param("purpose", purpose).update()

    fun placeIds(org: UUID): Set<UUID> = jdbc
        .sql("SELECT place_id FROM places WHERE org_id = :org")
        .param("org", org)
        .query(UUID::class.java)
        .set()
}
