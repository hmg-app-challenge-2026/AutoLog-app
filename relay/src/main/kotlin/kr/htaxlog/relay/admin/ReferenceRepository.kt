package kr.htaxlog.relay.admin

import kr.htaxlog.shared.PlaceKind
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class UserRow(val userId: UUID, val name: String, val dept: String?, val email: String?)

data class PlaceRow(
    val placeId: UUID,
    val label: String,
    val kind: PlaceKind,
    val lat: Double,
    val lng: Double,
    val radiusM: Int,
    val defaultPurpose: String?,
)

data class ScheduleRow(
    val scheduleId: UUID,
    val vehicleId: UUID,
    val userId: UUID?,
    val startsAt: Instant,
    val endsAt: Instant,
    val destPlaceId: UUID?,
    val purposeCategory: String?,
    val purposeDetail: String?,
)

data class RecipientRow(val id: Long, val email: String, val name: String?)

/** 직원·장소·배차·수신자 (F-51~54). 모든 쿼리는 조직(org_id) 범위 안에서만 동작한다. */
@Repository
class ReferenceRepository(private val jdbc: JdbcClient) {
    // ---- 직원 ----
    fun users(org: UUID): List<UserRow> = jdbc
        .sql("SELECT user_id, name, dept, email FROM users WHERE org_id = :org ORDER BY name")
        .param("org", org)
        .query { rs, _ ->
            UserRow(rs.uuid("user_id")!!, rs.getString("name"), rs.getString("dept"), rs.getString("email"))
        }
        .list()

    fun upsertUser(org: UUID, u: UserRow) {
        jdbc
            .sql(
                """
                INSERT INTO users (user_id, org_id, name, dept, email) VALUES (:id, :org, :name, :dept, :email)
                ON CONFLICT (user_id) DO UPDATE SET name = :name, dept = :dept, email = :email WHERE users.org_id = :org
                """.trimIndent(),
            ).param("id", u.userId)
            .param("org", org)
            .param("name", u.name)
            .param("dept", u.dept)
            .param("email", u.email)
            .update()
    }

    /** 직원을 지우고, 그 직원의 배차는 운전자 미정으로 남긴다. 바뀐 배차의 차량 id를 돌려준다. */
    fun deleteUser(org: UUID, id: UUID): Pair<Boolean, List<UUID>> {
        val vehicles =
            jdbc
                .sql("UPDATE schedules SET user_id = NULL WHERE org_id = :org AND user_id = :id RETURNING vehicle_id")
                .param("org", org)
                .param("id", id)
                .query { rs, _ -> rs.uuid("vehicle_id")!! }
                .list()
                .distinct()
        val deleted =
            jdbc.sql(
                "DELETE FROM users WHERE org_id = :org AND user_id = :id",
            ).param("org", org).param("id", id).update() >
                0
        return deleted to vehicles
    }

    // ---- 장소 ----
    fun places(org: UUID): List<PlaceRow> = jdbc
        .sql(
            "SELECT place_id, label, kind, lat, lng, radius_m, default_purpose FROM places WHERE org_id = :org ORDER BY label",
        )
        .param("org", org)
        .query { rs, _ ->
            PlaceRow(
                rs.uuid("place_id")!!,
                rs.getString("label"),
                PlaceKind.valueOf(rs.getString("kind")),
                rs.getDouble("lat"),
                rs.getDouble("lng"),
                rs.getInt("radius_m"),
                rs.getString("default_purpose"),
            )
        }.list()

    fun upsertPlace(org: UUID, p: PlaceRow) {
        jdbc
            .sql(
                """
                INSERT INTO places (place_id, org_id, label, kind, lat, lng, radius_m, default_purpose)
                VALUES (:id, :org, :label, :kind, :lat, :lng, :r, :dp)
                ON CONFLICT (place_id) DO UPDATE SET label = :label, kind = :kind, lat = :lat, lng = :lng, radius_m = :r,
                  default_purpose = :dp WHERE places.org_id = :org
                """.trimIndent(),
            ).param("id", p.placeId)
            .param("org", org)
            .param("label", p.label)
            .param("kind", p.kind.name)
            .param("lat", p.lat)
            .param("lng", p.lng)
            .param("r", p.radiusM)
            .param("dp", p.defaultPurpose)
            .update()
    }

    fun deletePlace(org: UUID, id: UUID): Pair<Boolean, List<UUID>> {
        val vehicles =
            jdbc
                .sql(
                    "UPDATE schedules SET dest_place_id = NULL WHERE org_id = :org AND dest_place_id = :id RETURNING vehicle_id",
                )
                .param("org", org)
                .param("id", id)
                .query { rs, _ -> rs.uuid("vehicle_id")!! }
                .list()
                .distinct()
        val deleted =
            jdbc.sql(
                "DELETE FROM places WHERE org_id = :org AND place_id = :id",
            ).param("org", org).param("id", id).update() >
                0
        return deleted to vehicles
    }

    // ---- 배차 ----
    fun schedules(org: UUID, vehicleId: UUID?, from: Instant, to: Instant): List<ScheduleRow> = jdbc
        .sql(
            """
            SELECT * FROM schedules WHERE org_id = :org AND (CAST(:v AS UUID) IS NULL OR vehicle_id = :v)
              AND ends_at >= :from AND starts_at <= :to ORDER BY starts_at
            """.trimIndent(),
        ).param("org", org)
        .param("v", vehicleId)
        .param("from", Timestamp.from(from))
        .param("to", Timestamp.from(to))
        .query { rs, _ -> rs.schedule() }
        .list()

    fun schedule(org: UUID, id: UUID): ScheduleRow? = jdbc
        .sql("SELECT * FROM schedules WHERE org_id = :org AND schedule_id = :id")
        .param("org", org)
        .param("id", id)
        .query { rs, _ -> rs.schedule() }
        .optional()
        .orElse(null)

    fun upsertSchedule(org: UUID, s: ScheduleRow) {
        jdbc
            .sql(
                """
                INSERT INTO schedules (schedule_id, org_id, vehicle_id, user_id, starts_at, ends_at, dest_place_id,
                  purpose_category, purpose_detail)
                VALUES (:id, :org, :v, :u, :s, :e, :d, :pc, :pd)
                ON CONFLICT (schedule_id) DO UPDATE SET vehicle_id = :v, user_id = :u, starts_at = :s, ends_at = :e,
                  dest_place_id = :d, purpose_category = :pc, purpose_detail = :pd WHERE schedules.org_id = :org
                """.trimIndent(),
            ).param("id", s.scheduleId)
            .param("org", org)
            .param("v", s.vehicleId)
            .param("u", s.userId)
            .param("s", Timestamp.from(s.startsAt))
            .param("e", Timestamp.from(s.endsAt))
            .param("d", s.destPlaceId)
            .param("pc", s.purposeCategory)
            .param("pd", s.purposeDetail)
            .update()
    }

    fun deleteSchedule(org: UUID, id: UUID): Boolean = jdbc.sql(
        "DELETE FROM schedules WHERE org_id = :org AND schedule_id = :id",
    ).param("org", org).param("id", id).update() >
        0

    // ---- 수신자 ----
    fun recipients(org: UUID): List<RecipientRow> = jdbc
        .sql("SELECT id, email, name FROM recipients WHERE org_id = :org ORDER BY email")
        .param("org", org)
        .query { rs, _ -> RecipientRow(rs.getLong("id"), rs.getString("email"), rs.getString("name")) }
        .list()

    /** 같은 이메일이 있으면(대소문자 무시) false. */
    fun insertRecipient(org: UUID, email: String, name: String?): Boolean = jdbc
        .sql(
            """
            INSERT INTO recipients (org_id, email, name) VALUES (:org, :email, :name)
            ON CONFLICT (org_id, lower(email)) DO NOTHING
            """.trimIndent(),
        ).param("org", org)
        .param("email", email)
        .param("name", name)
        .update() > 0

    fun deleteRecipient(org: UUID, id: Long): Boolean =
        jdbc.sql("DELETE FROM recipients WHERE org_id = :org AND id = :id").param("org", org).param("id", id).update() >
            0

    // ---- 차량 ----
    fun vehicleIds(org: UUID): List<UUID> = jdbc
        .sql("SELECT vehicle_id FROM vehicles WHERE org_id = :org")
        .param("org", org)
        .query { rs, _ -> rs.uuid("vehicle_id")!! }
        .list()

    /** 번호판 → 차량 id (CSV 배차 업로드). 공백은 무시한다. */
    fun vehicleIdsByPlate(org: UUID): Map<String, UUID> = jdbc
        .sql("SELECT vehicle_id, plate_no FROM vehicles WHERE org_id = :org")
        .param("org", org)
        .query { rs, _ -> rs.getString("plate_no").replace(" ", "") to rs.uuid("vehicle_id")!! }
        .list()
        .toMap()

    private fun ResultSet.uuid(column: String): UUID? = getObject(column, UUID::class.java)

    private fun ResultSet.schedule() = ScheduleRow(
        uuid("schedule_id")!!,
        uuid("vehicle_id")!!,
        uuid("user_id"),
        getTimestamp("starts_at").toInstant(),
        getTimestamp("ends_at").toInstant(),
        uuid("dest_place_id"),
        getString("purpose_category"),
        getString("purpose_detail"),
    )
}
