package kr.autolog.vehicle.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kr.autolog.shared.PlaceKind
import kr.autolog.vehicle.core.DomainStore
import kr.autolog.vehicle.core.DriverCandidate
import kr.autolog.vehicle.core.Origin
import kr.autolog.vehicle.core.PlaceInfo
import kr.autolog.vehicle.core.PurposeCategory
import kr.autolog.vehicle.core.PurposeDetail
import kr.autolog.vehicle.core.PurposeEngine
import kr.autolog.vehicle.core.PurposeRouteRule
import kr.autolog.vehicle.core.PurposeRuleId
import kr.autolog.vehicle.core.SettingsResult
import kr.autolog.vehicle.core.Suggestion
import kr.autolog.vehicle.core.TripFacts
import kr.autolog.vehicle.core.UnassignedTrip
import kr.autolog.vehicle.core.UserCard
import java.util.UUID
import kotlin.math.abs

/**
 * [PurposeEngine] 구현 (F-08, F-15, F-17, F-20). 입력은 전부 차 안 Room([DomainStore])에서 읽는다.
 * 저장소 오류가 나도 예외를 밖으로 내지 않고 "확인 필요" / null로 돌려준다(CONVENTIONS 7.5).
 */
class DefaultPurposeEngine(
    private val store: DomainStore,
    private val rules: PurposeRules = PurposeRules(),
    private val newId: () -> String = { "local-${UUID.randomUUID()}" },
    private val log: (String) -> Unit = {},
    /** 출발→도착 목적 규칙. 실제 앱은 [PurposeBook.rules]. */
    private val routeRules: () -> List<PurposeRouteRule> = { emptyList() },
) : PurposeEngine {
    override suspend fun suggest(trip: TripFacts): Suggestion = try {
        val places = store.places().map { it.toPlace() }
        val origin = at(trip.startLat, trip.startLng, places)
        val destination = at(trip.endLat, trip.endLng, places)
        val window = PurposeRules.SCHEDULE_WINDOW_MS
        val schedules =
            store.schedules(trip.vehicleId, trip.startTime - window, trip.startTime + window).map {
                ScheduleCandidate(it.scheduleId, it.startsAt, it.destPlaceId, it.purposeDetail)
            }
        val past =
            if (destination == null) {
                emptyList()
            } else {
                store
                    .trips(trip.startTime - PAST_LOOKBACK_MS, trip.startTime - 1)
                    .filter { t -> t.confirmedPurpose != null && at(t.endLat, t.endLng, listOf(destination)) != null }
                    .mapNotNull { it.confirmedPurpose }
            }
        rules.suggest(TripContext(trip.startTime, origin, destination, schedules, past, routeRules()))
    } catch (e: Exception) {
        log("목적 추천 실패 trip=${trip.tripId}: ${e.message}")
        Suggestion(PurposeDetail.NEEDS_CHECK, FALLBACK_CONFIDENCE, PurposeRuleId.FALLBACK)
    }

    override suspend fun driverCandidate(trip: TripFacts): DriverCandidate? = try {
        val w = DriverResolver.SCHEDULE_WINDOW_MS
        val schedules = store.schedules(trip.vehicleId, trip.startTime - w, trip.startTime + w)
        val earlier = store.trips(trip.startTime - PREVIOUS_LOOKBACK_MS, trip.startTime - 1)
        val previous = DriverResolver.previousTrip(earlier.filter { it.tripId != trip.tripId }, trip.startTime)
        DriverResolver.resolve(trip, schedules, previous, store.users().first())
    } catch (e: Exception) {
        log("운전자 추천 실패 trip=${trip.tripId}: ${e.message}")
        null
    }

    override fun orderedUsers(vehicleId: String, now: Long): Flow<List<UserCard>> = store.users().map { users ->
        val w = UserOrdering.SCHEDULE_WINDOW_MS
        val scheduled =
            runCatching { store.schedules(vehicleId, now - w, now + w) }
                .getOrDefault(emptyList())
                .sortedBy { abs(it.startsAt - now) }
                .map { it.userId }
        val previous =
            runCatching { DriverResolver.previousTrip(store.trips(now - PREVIOUS_LOOKBACK_MS, now), now)?.userId }
                .getOrNull()
        val recent = runCatching { store.recentUsers(RECENT_LIMIT) }.getOrDefault(emptyList())
        UserOrdering.order(users, scheduled, previous, recent)
    }

    override suspend fun shouldProposePlace(trip: TripFacts, detail: PurposeDetail): Boolean {
        if (detail.category != PurposeCategory.BUSINESS) return false
        val lat = trip.endLat ?: return false
        val lng = trip.endLng ?: return false
        val places = runCatching { store.places() }.getOrElse { return false }
        return PlaceMatcher.match(lat, lng, places.map { it.toPlace() }) == null
    }

    override suspend fun placeAt(lat: Double, lng: Double): PlaceInfo? = try {
        val places = store.places()
        PlaceMatcher.match(lat, lng, places.map { it.toPlace() })?.let { m -> places.first { it.placeId == m.placeId } }
    } catch (e: Exception) {
        log("등록 장소 찾기 실패: ${e.message}")
        null
    }

    override suspend fun registerClientPlace(trip: TripFacts, label: String): SettingsResult {
        val name = label.trim()
        if (name.isEmpty()) return SettingsResult.Invalid("거래처 이름을 입력해 주세요")
        val lat = trip.endLat
        val lng = trip.endLng
        if (lat == null || lng == null) return SettingsResult.Invalid("도착 위치를 알 수 없어 등록할 수 없습니다")
        return try {
            val existing = PlaceMatcher.match(lat, lng, store.places().map { it.toPlace() })
            if (existing != null) return SettingsResult.Invalid("이미 '${existing.label}'(으)로 등록된 곳입니다")
            store.savePlace(
                PlaceInfo(
                    newId(),
                    name,
                    PlaceKind.CLIENT,
                    lat,
                    lng,
                    defaultPurpose = PurposeDetail.CLIENT_VISIT.name,
                    origin = Origin.LOCAL,
                ),
            )
            SettingsResult.Ok
        } catch (e: Exception) {
            log("거래처 등록 실패: ${e.message}")
            SettingsResult.Invalid("저장하지 못했습니다")
        }
    }

    override suspend fun unassignedTrips(vehicleId: String, now: Long): List<UnassignedTrip> = try {
        val since = now - UNASSIGNED_LOOKBACK_MS
        // 직전 운행 규칙을 쓰려면 기간 시작보다 하루 더 앞까지 읽는다.
        val trips = store.trips(since - PREVIOUS_LOOKBACK_MS, now).filter { it.vehicleId == vehicleId }
        val users = store.users().first()
        trips
            .filter { it.userId == null && it.endTime != null && it.startTime >= since }
            .sortedByDescending { it.startTime }
            .map { t ->
                val facts =
                    TripFacts(t.tripId, t.vehicleId, t.startTime, t.endTime, endLat = t.endLat, endLng = t.endLng)
                val w = DriverResolver.SCHEDULE_WINDOW_MS
                val schedules = store.schedules(vehicleId, t.startTime - w, t.startTime + w)
                val previous = DriverResolver.previousTrip(trips.filter { it.tripId != t.tripId }, t.startTime)
                UnassignedTrip(
                    tripId = t.tripId,
                    startTime = t.startTime,
                    endTime = t.endTime,
                    distanceKm = t.distanceKm,
                    label = ReceiptMatcher.label(t, withDistance = true),
                    candidate = DriverResolver.resolve(facts, schedules, previous, users),
                )
            }
    } catch (e: Exception) {
        log("미확정 운행 조회 실패: ${e.message}")
        emptyList()
    }

    private fun at(lat: Double?, lng: Double?, places: List<Place>): Place? =
        if (lat == null || lng == null) null else PlaceMatcher.match(lat, lng, places)

    companion object {
        const val FALLBACK_CONFIDENCE = 0.5
        const val RECENT_LIMIT = 10

        /** 과거 확정 목적을 볼 기간 (규칙 f). */
        const val PAST_LOOKBACK_MS = 90L * 24 * 60 * 60 * 1000

        /** F-20 질문에 올릴 미확정 운행 기간. */
        const val UNASSIGNED_LOOKBACK_MS = 7L * 24 * 60 * 60 * 1000

        /** 직전 운행을 찾을 기간. 2시간 규칙보다 넉넉히 읽고 DriverResolver가 거른다. */
        const val PREVIOUS_LOOKBACK_MS = 24L * 60 * 60 * 1000
    }
}

internal fun PlaceInfo.toPlace() = Place(placeId, label, kind, lat, lng, radiusM)
