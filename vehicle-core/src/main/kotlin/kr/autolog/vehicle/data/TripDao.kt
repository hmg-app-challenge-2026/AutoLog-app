package kr.autolog.vehicle.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kr.autolog.vehicle.core.AddressSource

/** 운행·정차·차량 상태. 전부 suspend/Flow라 Room이 IO 스레드로 보낸다. */
@Dao
interface TripDao {
    @Upsert
    suspend fun upsert(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE tripId = :tripId")
    suspend fun trip(tripId: String): TripEntity?

    /** 끝나지 않은 운행. 앱이 죽었다 살아나면 여기서 복구한다(F-10). 정상이라면 많아야 1건. */
    @Query("SELECT * FROM trips WHERE status = 'RECORDING' ORDER BY startTime DESC LIMIT 1")
    suspend fun findRecording(): TripEntity?

    /** 10초 체크포인트. 다른 열은 건드리지 않는다. */
    @Query("UPDATE trips SET lastOdometerKm = :km, lastSeenAt = :at WHERE tripId = :tripId AND status = 'RECORDING'")
    suspend fun checkpoint(tripId: String, km: Double, at: Long): Int

    @Query(
        "SELECT * FROM trips WHERE startTime >= :from AND startTime < :to AND status != 'DISCARDED' ORDER BY startTime",
    )
    suspend fun tripsBetween(from: Long, to: Long): List<TripEntity>

    @Query(
        "SELECT * FROM trips WHERE startTime >= :from AND startTime < :to " +
            "AND status NOT IN ('RECORDING', 'DISCARDED') ORDER BY startTime DESC",
    )
    fun observeFinishedBetween(from: Long, to: Long): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE status NOT IN ('RECORDING', 'DISCARDED') ORDER BY startTime DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TripEntity>>

    /** 목적 미확정인 끝난 운행. */
    @Query("SELECT * FROM trips WHERE status IN ('PENDING_REVIEW', 'NEEDS_RECOVERY') ORDER BY startTime DESC")
    fun observePendingReviews(): Flow<List<TripEntity>>

    /**
     * 출발지 이름·주소만 고친다(F-07). 주소는 운행 시작 뒤 비동기로 늦게 오므로, 기록 중 체크포인트 같은 다른 열을 덮지 않게 이 열만 쓴다.
     * 값은 세션이 최신 행을 읽어 합친 결과다(사람이 고른 이름 보존은 세션 몫).
     */
    @Query(
        "UPDATE trips SET originLabel = :label, originAddress = :address, originAddressSource = :source, " +
            "revision = revision + 1 WHERE tripId = :tripId",
    )
    suspend fun setOriginPlace(tripId: String, label: String?, address: String?, source: AddressSource?): Int

    /** 도착지 이름·좌표·주소만 고친다(F-06). [setOriginPlace]와 같은 이유. */
    @Suppress("LongParameterList")
    @Query(
        "UPDATE trips SET destinationLabel = :label, destinationLat = :lat, destinationLng = :lng, " +
            "destinationSource = :placeSource, destinationAddress = :address, destinationAddressSource = :source, " +
            "revision = revision + 1 WHERE tripId = :tripId",
    )
    suspend fun setDestinationPlace(
        tripId: String,
        label: String?,
        lat: Double?,
        lng: Double?,
        placeSource: PlaceSource?,
        address: String?,
        source: AddressSource?,
    ): Int

    /**
     * 서버 주소(카카오)로 다시 채울 끝난 운행: [since] 이후 시작, 좌표는 있는데 주소가 없거나 내비 폴백인 것, 최신 순 [limit]건.
     */
    @Query(
        "SELECT * FROM trips WHERE startTime >= :since AND status NOT IN ('RECORDING', 'DISCARDED') AND (" +
            "(originLat IS NOT NULL AND (originAddressSource IS NULL OR originAddressSource = 'NAVI_HELPER')) OR " +
            "(destinationLat IS NOT NULL AND " +
            "(destinationAddressSource IS NULL OR destinationAddressSource = 'NAVI_HELPER'))" +
            ") ORDER BY startTime DESC LIMIT :limit",
    )
    suspend fun tripsNeedingAddress(since: Long, limit: Int): List<TripEntity>

    /** 검토 표시(F-23)를 남긴다. 엑셀 생성 때마다 다시 계산한다. */
    @Query("UPDATE trips SET reviewFlags = :flags WHERE tripId = :tripId")
    suspend fun setReviewFlags(tripId: String, flags: String?)

    @Insert
    suspend fun insertStop(stop: StopEntity): Long

    @Query("SELECT * FROM stops WHERE tripId = :tripId ORDER BY startTime")
    suspend fun stops(tripId: String): List<StopEntity>

    @Query("SELECT * FROM vehicle_state WHERE id = 0")
    suspend fun vehicleState(): VehicleStateEntity?

    @Upsert
    suspend fun saveVehicleState(state: VehicleStateEntity)

    /** 운전자 선택 기록(최근 순 정렬용). */
    @Upsert
    suspend fun upsertRecentUser(user: RecentUserEntity)
}
