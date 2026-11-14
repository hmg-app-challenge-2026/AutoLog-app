package kr.autolog.relay.admin

import kr.autolog.relay.common.ApiException
import kr.autolog.relay.common.Csv
import kr.autolog.relay.common.CsvRow
import kr.autolog.shared.PlaceKind
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

// ---- 요청 (운영 웹 → 서버). PATCH는 null인 필드를 그대로 두고, ""는 지운다. ----

data class UserRequest(val name: String? = null, val dept: String? = null, val email: String? = null)

data class PlaceRequest(
    val label: String? = null,
    /** PlaceKind 이름 또는 한글(본사·지점·거래처·주유소). */
    val kind: String? = null,
    /** 좌표가 없으면 이 주소로 지오코딩한다. */
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val radiusM: Int? = null,
    val defaultPurpose: String? = null,
)

data class ScheduleRequest(
    val vehicleId: UUID? = null,
    val userId: String? = null,
    /** ISO 8601 (+09:00). */
    val startsAt: String? = null,
    val endsAt: String? = null,
    val destPlaceId: String? = null,
    val purposeCategory: String? = null,
    val purposeDetail: String? = null,
)

data class RecipientRequest(val email: String, val name: String? = null)

// ---- 응답 ----

data class ScheduleView(
    val scheduleId: UUID,
    val vehicleId: UUID,
    val userId: UUID?,
    val startsAt: String,
    val endsAt: String,
    val destPlaceId: UUID?,
    val purposeCategory: String?,
    val purposeDetail: String?,
)

data class ImportError(val line: Int, val message: String)

data class ImportResult(val imported: Int)

/** CSV에 오류가 하나라도 있으면 아무것도 저장하지 않고 줄별 오류를 돌려준다. */
class ImportFailed(val errors: List<ImportError>) : RuntimeException("CSV 오류 ${errors.size}건")

/**
 * 운영 웹 기준 정보 관리 (F-51 직원, F-52 장소, F-53 배차, F-54 수신자) + 배포(F-43).
 * 바꾼 뒤에는 [ReferencePublisher]로 차 inbox에 전체 목록을 다시 넣는다.
 */
@Service
class AdminReferenceService(
    private val repo: ReferenceRepository,
    private val publisher: ReferencePublisher,
    private val geocoder: Geocoder,
    private val purposes: kr.autolog.relay.purpose.PurposeService,
) {
    // ===== 직원 (F-51) =====

    fun users(org: UUID) = repo.users(org)

    @Transactional
    fun createUser(org: UUID, r: UserRequest): UserRow {
        val u = validUser(UUID.randomUUID(), r.name, r.dept, r.email)
        repo.upsertUser(org, u)
        publisher.publishUsers(org)
        return u
    }

    @Transactional
    fun updateUser(org: UUID, id: UUID, r: UserRequest): UserRow {
        val old = repo.users(org).firstOrNull { it.userId == id } ?: throw notFound("USER_NOT_FOUND", "직원이 없습니다")
        val u = validUser(id, r.name ?: old.name, r.dept.patch(old.dept), r.email.patch(old.email))
        repo.upsertUser(org, u)
        publisher.publishUsers(org)
        return u
    }

    @Transactional
    fun deleteUser(org: UUID, id: UUID) {
        val (deleted, vehicles) = repo.deleteUser(org, id)
        if (!deleted) throw notFound("USER_NOT_FOUND", "직원이 없습니다")
        publisher.publishUsers(org)
        publisher.publishSchedules(org, vehicles)
    }

    /** CSV: name, dept, email */
    @Transactional
    fun importUsers(org: UUID, csv: ByteArray): ImportResult {
        val rows = Csv.parse(csv)
        val users = collect(rows) { row ->
            validUser(
                UUID.randomUUID(),
                row["name"] ?: row["이름"],
                row["dept"] ?: row["부서"],
                row["email"] ?: row["이메일"],
            )
        }
        users.forEach { repo.upsertUser(org, it) }
        publisher.publishUsers(org)
        return ImportResult(users.size)
    }

    // ===== 장소 (F-52) =====

    fun places(org: UUID) = repo.places(org)

    @Transactional
    fun createPlace(org: UUID, r: PlaceRequest): PlaceRow {
        val p = validPlace(UUID.randomUUID(), r, old = null)
        repo.upsertPlace(org, p)
        publisher.publishPlaces(org)
        return p
    }

    @Transactional
    fun updatePlace(org: UUID, id: UUID, r: PlaceRequest): PlaceRow {
        val old = repo.places(org).firstOrNull { it.placeId == id } ?: throw notFound("PLACE_NOT_FOUND", "장소가 없습니다")
        val p = validPlace(id, r, old)
        repo.upsertPlace(org, p)
        publisher.publishPlaces(org)
        return p
    }

    @Transactional
    fun deletePlace(org: UUID, id: UUID) {
        val (deleted, vehicles) = repo.deletePlace(org, id)
        if (!deleted) throw notFound("PLACE_NOT_FOUND", "장소가 없습니다")
        publisher.publishPlaces(org)
        publisher.publishSchedules(org, vehicles)
        purposes.publish(org) // 이 장소를 쓰던 목적 규칙은 DB에서 함께 지워졌다(FK) — 차에도 알린다
    }

    /** CSV: label, kind, address, lat, lng, radius_m, default_purpose (좌표가 비면 주소로 지오코딩) */
    @Transactional
    fun importPlaces(org: UUID, csv: ByteArray): ImportResult {
        val places =
            collect(Csv.parse(csv)) { row ->
                validPlace(
                    UUID.randomUUID(),
                    PlaceRequest(
                        label = row["label"] ?: row["이름"],
                        kind = row["kind"] ?: row["종류"],
                        address = row["address"] ?: row["주소"],
                        lat = row["lat"]?.toDoubleOrNull(),
                        lng = row["lng"]?.toDoubleOrNull(),
                        radiusM = row["radius_m"]?.toIntOrNull(),
                        defaultPurpose = row["default_purpose"],
                    ),
                    old = null,
                )
            }
        places.forEach { repo.upsertPlace(org, it) }
        publisher.publishPlaces(org)
        return ImportResult(places.size)
    }

    // ===== 배차 (F-53) =====

    fun schedules(org: UUID, vehicleId: UUID?, from: String?, to: String?): List<ScheduleView> {
        val f = from?.let { parseTime(it) } ?: Instant.EPOCH
        val t = to?.let { parseTime(it) } ?: Instant.parse("9999-12-31T00:00:00Z")
        return repo.schedules(org, vehicleId, f, t).map { it.view() }
    }

    @Transactional
    fun createSchedule(org: UUID, r: ScheduleRequest): ScheduleView {
        val s = validSchedule(org, UUID.randomUUID(), r, old = null)
        repo.upsertSchedule(org, s)
        publisher.publishSchedules(org, listOf(s.vehicleId))
        return s.view()
    }

    @Transactional
    fun updateSchedule(org: UUID, id: UUID, r: ScheduleRequest): ScheduleView {
        val old = repo.schedule(org, id) ?: throw notFound("SCHEDULE_NOT_FOUND", "배차가 없습니다")
        val s = validSchedule(org, id, r, old)
        repo.upsertSchedule(org, s)
        publisher.publishSchedules(org, listOf(old.vehicleId, s.vehicleId))
        return s.view()
    }

    @Transactional
    fun deleteSchedule(org: UUID, id: UUID) {
        val old = repo.schedule(org, id) ?: throw notFound("SCHEDULE_NOT_FOUND", "배차가 없습니다")
        repo.deleteSchedule(org, id)
        publisher.publishSchedules(org, listOf(old.vehicleId))
    }

    /** CSV: plate_no, driver(이름 또는 이메일), starts_at, ends_at, destination(장소 이름), purpose_detail */
    @Transactional
    fun importSchedules(org: UUID, csv: ByteArray): ImportResult {
        val plates = repo.vehicleIdsByPlate(org)
        val users = repo.users(org)
        val places = repo.places(org)
        val schedules =
            collect(Csv.parse(csv)) { row ->
                val plate = (row["plate_no"] ?: row["차량번호"]) ?: throw bad("plate_no(차량번호)가 비었습니다")
                val vehicleId = plates[plate.replace(" ", "")] ?: throw bad("등록되지 않은 차량번호입니다: $plate")
                val driver = row["driver"] ?: row["운전자"]
                val userId =
                    driver?.let { d ->
                        val found = users.filter { it.name == d || it.email.equals(d, ignoreCase = true) }
                        when (found.size) {
                            1 -> found.single().userId.toString()
                            0 -> throw bad("직원 목록에 없는 운전자입니다: $d")
                            else -> throw bad("같은 이름의 직원이 여럿입니다. 이메일로 적어 주세요: $d")
                        }
                    }
                val dest = row["destination"] ?: row["목적지"]
                val destId =
                    dest?.let { label ->
                        places.firstOrNull { it.label == label }?.placeId?.toString()
                            ?: throw bad("등록되지 않은 장소입니다: $label")
                    }
                validSchedule(
                    org,
                    UUID.randomUUID(),
                    ScheduleRequest(
                        vehicleId,
                        userId,
                        row["starts_at"] ?: row["시작"],
                        row["ends_at"] ?: row["종료"],
                        destId,
                        row["purpose_category"],
                        row["purpose_detail"] ?: row["목적"],
                    ),
                    old = null,
                    knownUsers = users.map { it.userId }.toSet(),
                    knownPlaces = places.map { it.placeId }.toSet(),
                    knownVehicles = plates.values.toSet(),
                )
            }
        schedules.forEach { repo.upsertSchedule(org, it) }
        publisher.publishSchedules(org, schedules.map { it.vehicleId })
        return ImportResult(schedules.size)
    }

    // ===== 수신자 (F-54) =====

    fun recipients(org: UUID) = repo.recipients(org)

    @Transactional
    fun addRecipient(org: UUID, r: RecipientRequest) {
        val email = validEmail(r.email) ?: throw bad("이메일을 입력해 주세요")
        if (!repo.insertRecipient(org, email, r.name?.trim()?.ifEmpty { null })) {
            throw ApiException(HttpStatus.CONFLICT, "RECIPIENT_DUPLICATE", "이미 등록된 수신자입니다")
        }
        publisher.publishRecipients(org)
    }

    @Transactional
    fun deleteRecipient(org: UUID, id: Long) {
        if (!repo.deleteRecipient(org, id)) throw notFound("RECIPIENT_NOT_FOUND", "수신자가 없습니다")
        publisher.publishRecipients(org)
    }

    /** CSV: email, name. 이미 있는 이메일은 건너뛴다. */
    @Transactional
    fun importRecipients(org: UUID, csv: ByteArray): ImportResult {
        val rows =
            collect(Csv.parse(csv)) { row ->
                (validEmail(row["email"] ?: row["이메일"]) ?: throw bad("이메일이 비었습니다")) to
                    (row["name"] ?: row["이름"])
            }
        val added = rows.count { (email, name) -> repo.insertRecipient(org, email, name) }
        publisher.publishRecipients(org)
        return ImportResult(added)
    }

    // ===== 검증 =====

    private fun validUser(id: UUID, name: String?, dept: String?, email: String?): UserRow {
        val n = name?.trim().orEmpty()
        if (n.isEmpty() || n.length > MAX_NAME) throw bad("이름은 1~${MAX_NAME}자로 입력해 주세요")
        return UserRow(id, n, dept?.trim()?.ifEmpty { null }, validEmail(email))
    }

    private fun validPlace(id: UUID, r: PlaceRequest, old: PlaceRow?): PlaceRow {
        val label = (r.label ?: old?.label)?.trim().orEmpty()
        if (label.isEmpty()) throw bad("장소 이름을 입력해 주세요")
        val kind = r.kind?.let(::parseKind) ?: old?.kind ?: throw bad("장소 종류를 입력해 주세요 (본사·지점·거래처·주유소)")
        if (kind == PlaceKind.HOME) throw bad("자택은 운영 웹에서 등록할 수 없습니다 (본인만 차 안에서 등록)")
        val point =
            when {
                r.lat != null && r.lng != null -> GeoPoint(r.lat, r.lng)
                !r.address.isNullOrBlank() ->
                    geocoder.geocode(r.address)
                        ?: throw bad("주소로 좌표를 찾지 못했습니다. 위도·경도를 직접 입력해 주세요: ${r.address}")
                old != null -> GeoPoint(old.lat, old.lng)
                else -> throw bad("주소 또는 위도·경도를 입력해 주세요")
            }
        if (point.lat !in -90.0..90.0 || point.lng !in -180.0..180.0) throw bad("좌표 범위가 올바르지 않습니다")
        val radius = r.radiusM ?: old?.radiusM ?: DEFAULT_RADIUS_M
        if (radius !in MIN_RADIUS_M..MAX_RADIUS_M) throw bad("반경은 $MIN_RADIUS_M~$MAX_RADIUS_M m로 입력해 주세요")
        val purpose = r.defaultPurpose.patch(old?.defaultPurpose)?.uppercase()
        if (purpose != null && !CODE.matches(purpose)) throw bad("기본 목적 코드가 올바르지 않습니다: $purpose")
        return PlaceRow(id, label, kind, point.lat, point.lng, radius, purpose)
    }

    private fun validSchedule(
        org: UUID,
        id: UUID,
        r: ScheduleRequest,
        old: ScheduleRow?,
        knownUsers: Set<UUID>? = null,
        knownPlaces: Set<UUID>? = null,
        knownVehicles: Set<UUID>? = null,
    ): ScheduleRow {
        val vehicleId = r.vehicleId ?: old?.vehicleId ?: throw bad("차량을 선택해 주세요")
        if (!(knownVehicles ?: repo.vehicleIds(org).toSet()).contains(vehicleId)) throw bad("조직에 없는 차량입니다")
        val userId = uuidOrNull(r.userId.patch(old?.userId?.toString()), "운전자")
        if (userId != null &&
            !(knownUsers ?: repo.users(org).map { it.userId }.toSet()).contains(userId)
        ) {
            throw bad("직원 목록에 없는 운전자입니다")
        }
        val destId = uuidOrNull(r.destPlaceId.patch(old?.destPlaceId?.toString()), "목적지")
        if (destId != null &&
            !(knownPlaces ?: repo.places(org).map { it.placeId }.toSet()).contains(destId)
        ) {
            throw bad("등록되지 않은 목적지입니다")
        }
        val starts = r.startsAt?.let(::parseTime) ?: old?.startsAt ?: throw bad("시작 시각을 입력해 주세요")
        val ends = r.endsAt?.let(::parseTime) ?: old?.endsAt ?: throw bad("종료 시각을 입력해 주세요")
        if (!ends.isAfter(starts)) throw bad("종료 시각은 시작 시각보다 뒤여야 합니다")
        if (Duration.between(starts, ends) > MAX_SCHEDULE) throw bad("배차 하나는 7일을 넘을 수 없습니다")
        return ScheduleRow(
            id,
            vehicleId,
            userId,
            starts,
            ends,
            destId,
            r.purposeCategory.patch(old?.purposeCategory)?.uppercase(),
            r.purposeDetail.patch(old?.purposeDetail)?.uppercase(),
        )
    }

    private fun validEmail(raw: String?): String? {
        val e = raw?.trim()?.lowercase()?.ifEmpty { null } ?: return null
        if (!EMAIL.matches(e)) throw bad("이메일 형식이 올바르지 않습니다: $raw")
        return e
    }

    private fun uuidOrNull(raw: String?, what: String): UUID? =
        raw?.let { runCatching { UUID.fromString(it) }.getOrElse { throw bad("$what id 형식이 올바르지 않습니다") } }

    /** CSV 줄마다 검증하고, 하나라도 틀리면 [ImportFailed]. */
    private fun <T> collect(rows: List<CsvRow>, each: (CsvRow) -> T): List<T> {
        if (rows.isEmpty()) throw bad("CSV에 데이터 행이 없습니다 (첫 줄은 헤더)")
        if (rows.size > MAX_IMPORT_ROWS) throw bad("CSV는 한 번에 ${MAX_IMPORT_ROWS}행까지 올릴 수 있습니다")
        val errors = mutableListOf<ImportError>()
        val out = rows.mapNotNull { row ->
            try {
                each(row)
            } catch (e: ApiException) {
                errors += ImportError(row.line, e.message)
                null
            }
        }
        if (errors.isNotEmpty()) throw ImportFailed(errors)
        return out
    }

    private fun ScheduleRow.view() = ScheduleView(
        scheduleId,
        vehicleId,
        userId,
        ReferencePublisher.iso(startsAt),
        ReferencePublisher.iso(endsAt),
        destPlaceId,
        purposeCategory,
        purposeDetail,
    )

    companion object {
        const val MAX_NAME = 50
        const val DEFAULT_RADIUS_M = 150
        const val MIN_RADIUS_M = 30
        const val MAX_RADIUS_M = 2_000
        const val MAX_IMPORT_ROWS = 2_000
        val MAX_SCHEDULE: Duration = Duration.ofDays(7)
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        private val CODE = Regex("^[A-Z_]{1,30}$")
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
        private val LOCAL_FORMATS =
            listOf(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))

        private val KIND_NAMES =
            mapOf(
                "본사" to PlaceKind.OFFICE,
                "사무실" to PlaceKind.OFFICE,
                "지점" to PlaceKind.BRANCH,
                "거래처" to PlaceKind.CLIENT,
                "고객사" to PlaceKind.CLIENT,
                "주유소" to PlaceKind.GAS_STATION,
                "자택" to PlaceKind.HOME,
                "기타" to PlaceKind.OTHER,
            )

        fun parseKind(raw: String): PlaceKind = KIND_NAMES[raw.trim()]
            ?: PlaceKind.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
            ?: throw bad("장소 종류가 올바르지 않습니다: $raw (본사·지점·거래처·주유소)")

        /** ISO 8601(+09:00 등) 또는 "2026-11-10 09:00"(서울 시각). */
        fun parseTime(raw: String): Instant {
            val s = raw.trim()
            runCatching { return OffsetDateTime.parse(s).toInstant() }
            for (f in LOCAL_FORMATS) {
                try {
                    return LocalDateTime.parse(s, f).atZone(KST).toInstant()
                } catch (e: DateTimeParseException) {
                    // 다음 형식 시도
                }
            }
            throw bad("시각 형식이 올바르지 않습니다: $raw (예: 2026-11-10 09:00)")
        }

        /** PATCH: null이면 기존 값, ""이면 지움. */
        private fun String?.patch(old: String?): String? = when {
            this == null -> old
            isBlank() -> null
            else -> trim()
        }

        private fun bad(message: String) = ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message)

        private fun notFound(code: String, message: String) = ApiException(HttpStatus.NOT_FOUND, code, message)
    }
}
