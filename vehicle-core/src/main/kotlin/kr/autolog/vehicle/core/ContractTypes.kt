// Contracts.kt 인터페이스가 주고받는 타입. 프론트도 쓰므로 Android·SDK·Room 타입을 넣지 않는다.
package kr.autolog.vehicle.core

import kr.autolog.shared.PlaceKind
import java.io.File
import java.time.YearMonth

// ===== 앱 A·B 공용 =====

/** 운전자가 어떻게 정해졌는지 (기술명세 2.9 TripEntity.driverSource). */
enum class DriverSource {
    DRIVER_SELECTED,
    SCHEDULE,
    ASSIGNMENT,
    PREVIOUS_TRIP,
    SELF_CLAIMED,
    MANAGER_ASSIGNED,
}

/**
 * 추천 엔진에 넘기는 운행 정보. 앱 A가 TripEntity에서 옮겨 담는다.
 * (B가 A의 Room 엔티티에 직접 의존하지 않기 위한 경계 타입.)
 */
data class TripFacts(
    val tripId: String,
    val vehicleId: String,
    /** 운행 시작(epoch millis, UTC). */
    val startTime: Long,
    val endTime: Long? = null,
    val startLat: Double? = null,
    val startLng: Double? = null,
    val endLat: Double? = null,
    val endLng: Double? = null,
    /** 내비 목적지 라벨. 없으면 null. */
    val destinationLabel: String? = null,
    val driverUserId: String? = null,
)

/** 기준 정보가 어디서 왔는지. 서버 배포분은 inbox 수신 때 통째로 바뀌고, 차 안 입력분은 남는다. */
enum class Origin { SERVER, LOCAL }

// ===== DomainStore 경계 타입 (앱 A가 Room에서 옮겨 담는다) =====

data class UserInfo(
    val userId: String,
    val name: String,
    val dept: String? = null,
    val email: String? = null,
    val origin: Origin = Origin.SERVER,
)

/** 운전자 선택 기록(recent_users). */
data class RecentUse(val userId: String, val lastUsedAt: Long)

data class PlaceInfo(
    val placeId: String,
    val label: String,
    val kind: PlaceKind,
    val lat: Double,
    val lng: Double,
    val radiusM: Int = 150,
    /** 이 장소의 기본 세부 목적(PurposeDetail.name). */
    val defaultPurpose: String? = null,
    val origin: Origin = Origin.SERVER,
)

data class ScheduleInfo(
    val scheduleId: String,
    val userId: String,
    val startsAt: Long,
    val endsAt: Long,
    val destPlaceId: String? = null,
    /** PurposeDetail.name. */
    val purposeDetail: String? = null,
)

/** 운행 요약. 추천·매칭에 필요한 값만. */
data class TripSummary(
    val tripId: String,
    val vehicleId: String,
    val userId: String?,
    val startTime: Long,
    val endTime: Long?,
    val distanceKm: Double? = null,
    val originLabel: String? = null,
    val destinationLabel: String? = null,
    val endLat: Double? = null,
    val endLng: Double? = null,
    /** 출발·도착 주소(`광역 기초 읍면동 도로명 번호`). 이름([originLabel])이 없을 때 표시용. 모르면 null. */
    val originAddress: String? = null,
    val destinationAddress: String? = null,
    /** 확정된 세부 목적. 추천만 됐거나 미확정이면 null. */
    val confirmedPurpose: PurposeDetail? = null,
    /** 운행 중 정차 라벨들(StopEntity.label). 주유소 정차 판단에 쓴다. */
    val stopLabels: List<String> = emptyList(),
)

data class RecipientInfo(val email: String, val name: String? = null, val origin: Origin = Origin.LOCAL)

/** 비용을 어떻게 입력했는지 (expenses.source). */
enum class ExpenseSource { OCR, VOICE, TAP }

/** 저장할 주유 비용 (expenses, kind=FUEL). 영수증 확정(OCR) 또는 음성·탭 입력(F-18). */
data class ExpenseDraft(
    val tripId: String?,
    val occurredAt: Long,
    val amountWon: Int,
    val liters: Double?,
    val unitPrice: Int?,
    val discountWon: Int,
    val fuelType: String?,
    val merchant: String?,
    /** 끝 4자리 숫자만. */
    val cardLast4: String?,
    /** 영수증 정보. 음성·탭 입력이면 null. */
    val receiptId: String? = null,
    val receiptSha256: String? = null,
    val thumbUrl: String? = null,
    /** OCR 확신도 원본(필드 이름 → 0~1). 사용자가 고친 필드는 1.0이 아니라 원래 값 그대로 둔다. */
    val ocrConfidence: Map<String, Double> = emptyMap(),
    /** 사용자가 고친 필드 이름. 원본 값은 OCR 결과에 남는다(CONVENTIONS 7.1). */
    val editedFields: Set<String> = emptySet(),
    val source: ExpenseSource = ExpenseSource.OCR,
)

sealed interface SyncResult {
    data class Ok(val received: Int) : SyncResult

    data class Failed(val reason: String) : SyncResult
}

sealed interface SettingsResult {
    data object Ok : SettingsResult

    data class Invalid(val reason: String) : SettingsResult
}

// ===== 앱 B 제공: 맥락 (F-06, F-07) =====

/** 내비 목적지. */
data class DestinationInfo(
    val label: String,
    val address: String?,
    val lat: Double,
    val lng: Double,
    val poiId: String? = null,
)

/** 내비 도착 알림. */
data class ArrivalInfo(val label: String?, val address: String?, val at: Long)

/**
 * 좌표 한 건. SDK에는 accuracy(m)가 없고 confidence만 있다(FusedLocation 1.0.4).
 * 로그에는 소수 3자리까지만 남긴다(CONVENTIONS 7.6).
 */
data class LocationFix(
    val lat: Double,
    val lng: Double,
    val headingDeg: Float,
    val confidence: Float,
    val timestampMs: Long,
)

/** 내비가 알려 준 현재 위치. 주소가 없을 수도 있다. */
data class NaviCurrentLocation(val lat: Double, val lng: Double, val address: String?)

/** 주소를 어디서 얻었는지 (TripEntity.*AddressSource, CONVENTIONS 7.1 출처). */
enum class AddressSource {
    /** 중계 서버 → 카카오 coord2address. 읍면동까지 정확하다. */
    KAKAO_RELAY,

    /** 서버에 닿지 않아 내비 현재 위치 주소 문자열을 줄여 썼다. 형식은 내비가 주는 대로다. */
    NAVI_HELPER,
}

/** 좌표 → 주소 결과 ([GeocodeApi]). [address]는 `광역 기초 읍면동 도로명 번호`, [placeName]은 건물 이름(없으면 null). */
data class GeocodeResult(val address: String, val placeName: String?, val source: AddressSource)

// ===== 앱 B 제공: 목적·운전자 (F-08, F-15, F-20) =====

/** 국세청 운행기록부 구분 (기획서 3.2). */
enum class PurposeCategory(val labelKo: String) {
    BUSINESS("일반 업무용"),
    COMMUTE("출퇴근용"),
    NON_BUSINESS("비업무용"),
    UNDETERMINED("미확정"),
}

/**
 * 세부 목적. 저장할 때는 [name] 문자열로 쓴다(TripEntity.purposeDetail).
 *
 * 기본 목적은 아래 상수들이고, 회사(관리자 웹)나 차(설정)에서 **추가한 목적**은 [custom]으로 만든다.
 * 추가 목적의 [name]은 `custom:<구분>:<이름>`이라 저장된 문자열만으로 구분·이름을 되살린다
 * — 운행 기록·엑셀이 목적 목록을 몰라도 된다. 나중에 목록에서 지워도 지난 기록의 이름은 그대로 남는다.
 */
class PurposeDetail private constructor(val name: String, val category: PurposeCategory, val labelKo: String) {
    /** 회사·차에서 추가한 목적이면 true. */
    val isCustom: Boolean get() = name.startsWith(CUSTOM_PREFIX)

    override fun equals(other: Any?): Boolean = other is PurposeDetail && other.name == name

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = name

    companion object {
        val CLIENT_VISIT = PurposeDetail("CLIENT_VISIT", PurposeCategory.BUSINESS, "거래처 방문")
        val FIELD_WORK = PurposeDetail("FIELD_WORK", PurposeCategory.BUSINESS, "외근")
        val BUSINESS_TRIP = PurposeDetail("BUSINESS_TRIP", PurposeCategory.BUSINESS, "출장")
        val MEETING = PurposeDetail("MEETING", PurposeCategory.BUSINESS, "회의")
        val BRANCH_MOVE = PurposeDetail("BRANCH_MOVE", PurposeCategory.BUSINESS, "지점 이동")
        val COMMUTE_IN = PurposeDetail("COMMUTE_IN", PurposeCategory.COMMUTE, "출근")
        val COMMUTE_OUT = PurposeDetail("COMMUTE_OUT", PurposeCategory.COMMUTE, "퇴근")
        val PERSONAL = PurposeDetail("PERSONAL", PurposeCategory.NON_BUSINESS, "개인 이동")
        val NEEDS_CHECK = PurposeDetail("NEEDS_CHECK", PurposeCategory.UNDETERMINED, "확인 필요")

        /** 기본 목적(추가 목적 제외). */
        val entries: List<PurposeDetail> = listOf(
            CLIENT_VISIT,
            FIELD_WORK,
            BUSINESS_TRIP,
            MEETING,
            BRANCH_MOVE,
            COMMUTE_IN,
            COMMUTE_OUT,
            PERSONAL,
            NEEDS_CHECK,
        )

        private const val CUSTOM_PREFIX = "custom:"
        const val MAX_LABEL = 30

        /** 추가 목적. 이름 앞뒤 공백은 지운다. 미확정 구분은 받지 않는다. */
        fun custom(label: String, category: PurposeCategory): PurposeDetail {
            val l = label.trim()
            require(l.isNotEmpty() && l.length <= MAX_LABEL) { "목적 이름은 1~${MAX_LABEL}자" }
            require(category != PurposeCategory.UNDETERMINED) { "미확정 구분에는 목적을 추가하지 않는다" }
            return PurposeDetail("$CUSTOM_PREFIX${category.name}:$l", category, l)
        }

        /** 서버·Room 문자열을 안전하게 읽는다. 기본 목적 이름이거나 `custom:구분:이름`이면 값, 모르면 null. */
        fun fromName(name: String?): PurposeDetail? {
            if (name == null) return null
            entries.firstOrNull { it.name == name }?.let { return it }
            if (!name.startsWith(CUSTOM_PREFIX)) return null
            val rest = name.removePrefix(CUSTOM_PREFIX)
            val sep = rest.indexOf(':').takeIf { it > 0 } ?: return null
            val category = PurposeCategory.entries.firstOrNull { it.name == rest.substring(0, sep) } ?: return null
            return runCatching { custom(rest.substring(sep + 1), category) }.getOrNull()
        }
    }
}

/** 새 장소 후보(검색 결과·지금 위치). [name]은 장소 이름(지금 위치면 null — 사람이 붙인다). */
data class PlaceCandidate(val name: String?, val address: String?, val lat: Double, val lng: Double)

sealed interface PlaceSearchResult {
    data class Found(val candidates: List<PlaceCandidate>) : PlaceSearchResult

    /** [reason]은 사람이 읽을 문장(서버 연결 안 됨·검색 꺼짐 등). */
    data class Failed(val reason: String) : PlaceSearchResult
}

sealed interface PlaceAddResult {
    /** 저장했다. 같은 자리에 이미 장소가 있으면 그 장소([existing] = true). */
    data class Added(val placeId: String, val existing: Boolean = false) : PlaceAddResult

    data class Invalid(val reason: String) : PlaceAddResult
}

/** 목적 목록의 한 줄(설정 화면). 기본 목적은 [origin] = null. */
data class PurposeInfo(val detail: PurposeDetail, val origin: Origin?)

/**
 * 출발 장소 → 도착 장소 = 목적 규칙 (F-08). [originPlaceId]가 null이면 출발지는 어디서든.
 * 회사가 정한 것은 [origin] = SERVER(관리자 웹), 차에서 만든 것은 LOCAL.
 */
data class PurposeRouteRule(
    val ruleId: String,
    val originPlaceId: String?,
    val destPlaceId: String,
    val purpose: PurposeDetail,
    val origin: Origin = Origin.LOCAL,
)

/** 설정 화면에 보일 규칙 — 장소 이름을 붙였다. 장소가 지워졌으면 이름 대신 null(화면은 "지운 장소"). */
data class PurposeRuleInfo(
    val ruleId: String,
    /** null = 어디서든. */
    val originLabel: String?,
    val originMissing: Boolean,
    val destLabel: String?,
    val purpose: PurposeDetail,
    val origin: Origin,
)

/** 어느 규칙이 추천했는지. 출처를 남기기 위한 값(CONVENTIONS 7.1). */
enum class PurposeRuleId {
    SCHEDULE,

    /** 출발 장소·도착 장소가 둘 다 맞는 목적 규칙. */
    ROUTE,

    /** 출발지 "어디서든" + 도착 장소가 맞는 목적 규칙. */
    ROUTE_ANY_ORIGIN,
    COMMUTE_IN,
    COMMUTE_OUT,
    CLIENT,
    BRANCH,
    PAST_CONFIRMED,
    WEEKEND_UNKNOWN,
    FALLBACK,
}

/** 목적 추천 결과. 화면은 [detail]을 큰 버튼으로 보여 주고 사용자가 1탭으로 확정한다. */
data class Suggestion(
    val detail: PurposeDetail,
    val confidence: Double,
    val rule: PurposeRuleId,
    /** 배차에서 온 경우 그 배차 id. */
    val scheduleId: String? = null,
) {
    val category: PurposeCategory get() = detail.category

    /** 확신이 낮으면 화면이 추천 버튼 대신 선택지를 먼저 보여 준다(기획서 3.2 "확신도가 낮으면 반드시 묻는다"). */
    val isConfident: Boolean get() = confidence >= CONFIDENT_THRESHOLD

    companion object {
        const val CONFIDENT_THRESHOLD = 0.8
    }
}

/** 운전자 후보. */
data class DriverCandidate(val userId: String, val name: String, val source: DriverSource, val confidence: Double)

/** 운전자가 정해지지 않은 지난 운행 (F-20). */
data class UnassignedTrip(
    val tripId: String,
    val startTime: Long,
    val endTime: Long?,
    val distanceKm: Double?,
    /** 화면 표시용. 예: "09:00 본사 → 판교 거래처 · 23.4 km". */
    val label: String,
    /** 배차·직전 운행으로 찾은 후보. 있으면 화면이 그 사람을 먼저 보여 준다. */
    val candidate: DriverCandidate?,
)

/** 운전자 선택 화면의 카드 한 장. */
data class UserCard(
    val userId: String,
    val name: String,
    val dept: String?,
    /** 위로 올라온 이유. 화면이 "배차" "최근" 배지로 보여 준다. 이름순이면 null. */
    val badge: UserBadge? = null,
)

enum class UserBadge { SCHEDULED, RECENT }

// ===== 앱 B 제공: 음성 (F-09, F-18) =====

/** 음성 인식 결과. */
sealed interface VoiceResult<out T> {
    /** 알아듣고 값까지 뽑았다. [text]는 인식한 원문(화면에 보여 준다). */
    data class Recognized<T>(val text: String, val value: T) : VoiceResult<T>

    /** 말은 받았지만 값을 못 뽑았다. 화면은 탭 입력으로 넘어간다. */
    data class NotUnderstood(val text: String) : VoiceResult<Nothing>

    /** 마이크·STT 실패나 사용자가 말하지 않음. */
    data class Failed(val reason: String) : VoiceResult<Nothing>
}

data class VoicePurpose(val detail: PurposeDetail, val note: String? = null)

data class VoiceFuel(val amountWon: Int?, val liters: Double?, val unitPrice: Int? = null)

// ===== 앱 B 제공: 영수증 (F-19, F-30) =====

sealed interface QrResult {
    /** [url]을 QR로 그린다. [expiresAt](epoch millis)이 지나면 다시 발급한다. */
    data class Issued(val url: String, val expiresAt: Long) : QrResult

    /** 네트워크·서버 오류. 화면은 "연결을 확인해 주세요". */
    data class Failed(val reason: String) : QrResult
}

/** 영수증을 붙일 운행 후보. */
data class TripCandidate(
    val tripId: String,
    val startTime: Long,
    val endTime: Long?,
    /** 화면 표시용 한 줄. 예: "09:12 본사 → 판교 거래처". */
    val label: String,
    /** 주유소에서 정차한 운행이면 true. */
    val hasFuelStop: Boolean,
)

sealed interface ConfirmResult {
    data class Saved(val expenseId: String) : ConfirmResult

    /** 검산 실패 등으로 저장하지 않았다(F-19 "검산 실패 시 확정 비활성"). */
    data class Rejected(val reason: String) : ConfirmResult
}

// ===== 앱 B 제공: 메일 (F-22, F-24) =====

/**
 * 앱 A가 만든 운행기록부 파일과 메일 제목·본문에 넣을 요약 (F-22).
 * 제목 "[AutoLog] 2026-11 운행기록부 — 12가3456", 본문 "운행 N건 · 총 X km · 검토 필요 N건".
 */
data class ReportFile(
    val month: YearMonth,
    val file: File,
    val plateNo: String,
    val tripCount: Int,
    /** 거리를 아는 운행만 더한 값. 거리 미확정 운행은 [reviewCount]에 들어간다. */
    val totalKm: Double,
    /** 엑셀 "검토" 열에 표시된 건수 (F-23). */
    val reviewCount: Int,
)

/** SMTP 설정. [toString]에 비밀번호가 찍히지 않게 가린다(CONVENTIONS 7.6). */
data class SmtpConfig(
    val host: String,
    val port: Int = 587,
    val username: String,
    val password: String,
    val fromName: String? = null,
    val startTls: Boolean = true,
) {
    override fun toString(): String =
        "SmtpConfig(host=$host, port=$port, username=$username, password=***, fromName=$fromName, startTls=$startTls)"
}

sealed interface MailResult {
    /** 보냈다(send) 또는 저장했다(saveSmtp). */
    data object Ok : MailResult

    data class Failed(val error: MailError, val message: String) : MailResult
}

enum class MailError { NOT_CONFIGURED, AUTH, NETWORK, INVALID_RECIPIENT, UNKNOWN }

// ===== 앱 A 제공: 운행 세션 (F-02–05, F-10, F-13, F-16) =====

/** 자동 종료 대기 시간. 이유별로 다르다(프론트 스펙 6.6, 기술명세 2.8). 앱 A 소유 값. */
object TripTiming {
    /** 기어 P → 자동 종료까지. P는 "주차했다"는 강한 신호라 짧다. */
    const val T_GEAR_P_SEC = 5L

    /** 움직임 없음·내비 도착 → 자동 종료까지 (T_stop). */
    const val T_STOP_SEC = 180L
}

/** 운행 값의 한계. 앱 A(계기판 검증)와 프론트(거리 입력 검사)가 같은 값을 쓴다. */
object TripLimits {
    /** 1회 운행 거리 상한(km). 계기판 차이가 이보다 크면 이상값, 사람이 고친 거리도 이 이하만 받는다. */
    const val MAX_TRIP_KM = 1_500.0
}

/** 차의 움직임 판정 결과. 화면 사용 3단계와 입력 차단(F-13)의 기준. 판정 불가는 null로 표현한다. */
enum class VehicleMotion {
    /** 기어 P. 전체 앱. */
    PARKED,

    /** P가 아니고 계기판 거리가 멈춤. 읽기 전용 Peek. */
    STOPPED,

    /** 계기판 거리가 늘고 있음. 화면 없음(백그라운드). */
    MOVING,
}

/** 자동 종료 대기에 들어간 이유. 대기 시간이 다르다([TripTiming]). */
enum class StopReason { GEAR_P, NO_MOVEMENT, NAVI_ARRIVED, USER_REQUEST }

/** 목적을 어떻게 입력했는지 (TripEntity.purposeInputMode). */
enum class InputMode { TAP, VOICE }

/** 강제 종료 후 복구 다이얼로그의 선택 (F-10). */
enum class RecoveryChoice {
    /** 지금 계기판 값으로 운행을 끝내고 목적 확인으로. */
    RECORD,

    /** 아직 운행 중 — 이어서 기록. */
    CONTINUE,

    /** 운행을 지운다. */
    DELETE,
}

/** 진행 중(또는 방금 끝난) 운행. 화면 표시에 필요한 값만. */
data class LiveTrip(
    val tripId: String,
    /** 운행 시작(epoch millis, UTC). */
    val startTime: Long,
    /** 시작 계기판 값. 못 읽었으면 null(거리 미확정). */
    val startOdometerKm: Double?,
    /** 내비 목적지 이름. 없으면 null. */
    val destinationLabel: String?,
    val driverUserId: String?,
    /** 출발지 이름(등록 장소 이름, 없으면 건물 이름). 모르면 null (R-9). 주소는 [originAddress]. */
    val originLabel: String? = null,
    /** 출발지 주소(`광역 기초 읍면동 도로명 번호`). 운행 시작 직후 비동기로 채워진다. 모르면 null. */
    val originAddress: String? = null,
    /** 내비 목적지 주소. 없으면 null. */
    val destinationAddress: String? = null,
)

sealed interface SessionState {
    data object Idle : SessionState

    /** [liveKm] null = 계기판을 못 읽음(거리 미확정, 추정 금지). [lastSavedAt] = 마지막 Room 체크포인트 시각. */
    data class Recording(val trip: LiveTrip, val liveKm: Double?, val lastSavedAt: Long) : SessionState

    /** 자동 종료 대기. [deadlineAt](epoch millis)이 유일한 시계다 — 화면은 남은 시간만 계산한다. */
    data class StopCandidate(
        val trip: LiveTrip,
        val liveKm: Double?,
        val reason: StopReason,
        val since: Long,
        val deadlineAt: Long,
    ) : SessionState

    /** 운행이 끝났고 목적 확인을 기다린다. [distanceKm] null = 거리 미확정. [suggestion]은 앱 B 추천. */
    data class PendingReview(val trip: LiveTrip, val distanceKm: Double?, val suggestion: Suggestion?) : SessionState

    /** 앱이 죽었다 살아났는데 그 사이 계기판이 [extraKm]만큼 늘었다 (F-10). 화면은 복구 다이얼로그를 띄운다. */
    data class NeedsRecovery(val trip: LiveTrip, val extraKm: Double?) : SessionState
}

/** 앱이 기록하지 않았지만 계기판 차이로 확인된 운행 (F-10). */
data class MissedTrip(val gapKm: Double, val lastKnownAt: Long)

sealed interface StartResult {
    data class Started(val tripId: String) : StartResult

    data class Failed(val reason: String) : StartResult
}

/** 알림 → 화면 딥링크 Intent extra 키 (R-3). 앱 A의 알림과 프론트 Navigator가 같이 쓴다. */
object DeepLink {
    const val KEY_ROUTE = "autolog.route"
    const val KEY_TRIP_ID = "autolog.tripId"

    /** [KEY_ROUTE] 값: 목적 확인 화면. [KEY_TRIP_ID]가 함께 온다. */
    const val ROUTE_REVIEW = "review"
}

// ===== 앱 A 제공: 기록 조회·운행기록부·서버 연결 (F-01, F-12, F-21) =====

/** 사내 서버 전송 상태. 업로드(R-4)는 2차 합의 전이라 지금은 항상 null(전송 안 씀). */
enum class UploadState { PENDING, DONE, FAILED }

/**
 * 운행 기록 한 줄. 비어 있는 값은 "미확정"이다 — [purpose] null = 목적 확인 필요,
 * [driverUserId] null = 운전자 미확정, [distanceKm] null = 거리 미확정(추정 금지).
 * 출발지·도착지는 이름([originLabel]: 등록 장소·내비 목적지·건물 이름)과 주소([originAddress])를 따로 준다 — 화면은 이름 + 주소 2줄.
 */
data class TripRow(
    val tripId: String,
    val startTime: Long,
    val endTime: Long?,
    val driverUserId: String?,
    val originLabel: String?,
    val destinationLabel: String?,
    /** 사람이 고친 거리가 있으면 그 값, 없으면 계기판 차이. */
    val distanceKm: Double?,
    val purpose: PurposeDetail?,
    val note: String? = null,
    val upload: UploadState? = null,
    val originAddress: String? = null,
    val destinationAddress: String? = null,
    /** [distanceKm]가 사람이 고친 값이면 true. 화면은 "수정됨"을 붙인다. */
    val isDistanceEdited: Boolean = false,
)

sealed interface ReportBuildResult {
    data class Built(val file: ReportFile) : ReportBuildResult

    /** 저장 공간 부족 등. 화면은 [reason]을 보여 준다. */
    data class Failed(val reason: String) : ReportBuildResult
}

sealed interface ConnectionState {
    data object NotRegistered : ConnectionState

    /** [vehicleLabel] 예: "12가3456 · 아이오닉 6". [lastSyncAt] = 마지막 inbox 수신 성공. */
    data class Registered(val vehicleLabel: String, val serverUrl: String, val lastSyncAt: Long?) : ConnectionState

    /** 등록은 됐지만 최근 inbox 수신이 실패했다. 로컬 기능은 그대로 동작한다. */
    data class Unreachable(val vehicleLabel: String, val serverUrl: String) : ConnectionState
}

sealed interface RegisterResult {
    data object Ok : RegisterResult

    /** 주소 형식 오류, 코드 무효·만료, 서버 연결 실패. 화면은 [reason]을 보여 준다. */
    data class Invalid(val reason: String) : RegisterResult
}
