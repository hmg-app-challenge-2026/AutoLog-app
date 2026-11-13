package kr.autolog.vehicle.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kr.autolog.vehicle.core.AddressSource
import kr.autolog.vehicle.core.DriverSource

/** 거리가 어디서 왔는지 (CONVENTIONS 7.1). 사용자가 고친 값은 원본을 덮지 않고 [TripEntity.editedDistanceKm]에. */
enum class DistanceSource { VEHICLE_ODOMETER, UNAVAILABLE }

/** 출발·도착 위치가 어디서 왔는지. */
enum class PlaceSource { NAVI_HELPER, FUSED_LOCATION, NAVI_CURRENT, PREVIOUS_TRIP_END, USER_SELECTED }

/** 목적이 어떻게 정해졌는지. 추천만 됐으면 RULE_SUGGESTED, 사람이 확정해야 USER_CONFIRMED. */
enum class PurposeSource { UNSET, RULE_SUGGESTED, USER_CONFIRMED }

/** 운행 상태 (기술명세 2.8). */
enum class TripStatus { RECORDING, PENDING_REVIEW, CONFIRMED, NEEDS_RECOVERY, DISCARDED }

/**
 * 운행 한 건 — 장부의 원본(기술명세 2.9). 시작·10초 체크포인트·기어 P·종료 때마다 즉시 커밋한다(CONVENTIONS 7.1-4).
 * 시각은 UTC epoch millis, 거리는 km(원본 그대로), 화면·엑셀에서만 변환한다.
 */
@Entity(tableName = "trips", indices = [Index("startTime"), Index("status")])
data class TripEntity(
    @PrimaryKey val tripId: String,
    val vehicleId: String,
    val userId: String?,
    val driverSource: DriverSource? = null,
    val startTime: Long,
    val endTime: Long? = null,
    /** 앱 미실행 중 운행(F-10)처럼 시각이 추정이면 true. 거리는 언제나 계기판 차이다. */
    val timeEstimated: Boolean = false,
    val startOdometerKm: Double?,
    val endOdometerKm: Double? = null,
    /** 마지막 체크포인트. 앱이 죽어도 여기서 복구한다. */
    val lastOdometerKm: Double? = null,
    val lastSeenAt: Long? = null,
    /** end − start. 조회 실패면 null + [DistanceSource.UNAVAILABLE] — 추정 금지. */
    val distanceKm: Double? = null,
    val distanceSource: DistanceSource = DistanceSource.VEHICLE_ODOMETER,
    val editedDistanceKm: Double? = null,
    val originLabel: String? = null,
    val originLat: Double? = null,
    val originLng: Double? = null,
    val originSource: PlaceSource? = null,
    val destinationLabel: String? = null,
    val destinationLat: Double? = null,
    val destinationLng: Double? = null,
    val destinationSource: PlaceSource? = null,
    /**
     * 출발·도착 주소(`광역 기초 읍면동 도로명 번호`, open-issues I-05). 이름([originLabel]: 등록 장소·내비 목적지·건물 이름)과 따로 둔다.
     * 운행 시작·종료 직후 비동기로 채운다 — 서버(카카오) → 실패하면 내비 현재 위치 주소. 못 채운 것은 동기화 때 서버로 다시 채운다.
     */
    val originAddress: String? = null,
    val originAddressSource: AddressSource? = null,
    val destinationAddress: String? = null,
    val destinationAddressSource: AddressSource? = null,
    /** PurposeDetail.name. 확정 전에는 null. */
    val purposeDetail: String? = null,
    /** STT 원문 또는 메모. */
    val purposeNote: String? = null,
    /** InputMode.name (TAP | VOICE). */
    val purposeInputMode: String? = null,
    val purposeSource: PurposeSource = PurposeSource.UNSET,
    /** 앱 B 추천(PurposeDetail.name)·신뢰도·규칙. 확정과 따로 남긴다. */
    val suggestedPurpose: String? = null,
    val suggestedConfidence: Double? = null,
    val suggestedRule: String? = null,
    val status: TripStatus = TripStatus.RECORDING,
    /** 검토 표시(F-23) 코드 목록, 쉼표 구분. 엑셀 생성 직전에 다시 계산한다. */
    val reviewFlags: String? = null,
    /** SDK 원본 값·버전(감사용). */
    val rawSnapshotJson: String? = null,
    /** 고칠 때마다 1 증가. 사본 업로드(로드맵)의 멱등 키로 쓴다. */
    val revision: Int = 0,
)

/** 운행 중 정차 (자동 종료 대기 안에 다시 출발). */
@Entity(tableName = "stops", indices = [Index("tripId")])
data class StopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: String,
    val startTime: Long,
    val endTime: Long?,
    val odometerKm: Double?,
    /** 등록 장소 이름(예: 주유소). 주유 증빙 검토(F-23)에 쓴다. */
    val label: String? = null,
    /** StopReason.name. */
    val reason: String? = null,
)

/** 놓친 운행 판정 기준값과 차량 상태 (1행, id = 0). 운행 종료·앱 시작·체크포인트마다 갱신. */
@Entity(tableName = "vehicle_state")
data class VehicleStateEntity(
    @PrimaryKey val id: Int = 0,
    val lastKnownOdometerKm: Double? = null,
    val lastKnownAt: Long? = null,
    /** [무시]한 놓친 운행의 계기판 값. 같은 값으로는 다시 묻지 않는다. */
    val ignoredOdometerKm: Double? = null,
    /** 다음 운행의 운전자(운전자 선택 화면). null = 나중에 선택. */
    val currentDriverUserId: String? = null,
    val vin: String? = null,
)
