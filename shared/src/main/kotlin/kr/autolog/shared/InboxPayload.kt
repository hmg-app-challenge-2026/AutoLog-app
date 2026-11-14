package kr.autolog.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 서버 우체통(inbox) 편지 종류. 서버 → 차 단방향. */
@Serializable
enum class InboxKind { RECEIPT, USERS, PLACES, SCHEDULES, RECIPIENTS, MAIL_ACCOUNT }

/** GET /v1/inbox 응답의 한 항목. */
@Serializable
data class InboxItem(
    val id: Long,
    val kind: InboxKind,
    val payload: InboxPayload,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
sealed interface InboxPayload

// ---- RECEIPT: 영수증 OCR 결과 (F-41 → F-19) ----

@Serializable
@SerialName("RECEIPT")
data class ReceiptPayload(
    val receiptId: String,
    val sha256: String,
    val thumbUrl: String,
    val takenAt: String?,
    val fields: ReceiptFields,
    val confidence: Map<String, Double>,
    val arithOk: Boolean?,
    val ocrStatus: OcrStatus,
) : InboxPayload

@Serializable
data class ReceiptFields(
    val merchant: String?,
    val datetime: String?,
    val fuelType: String?,
    val liters: Double?,
    val unitPrice: Int?,
    val discountWon: Int?,
    val amountWon: Int?,
    /** 카드번호는 끝 4자리만. 전체 번호는 어디에도 두지 않는다. */
    val cardLast4: String?,
)

@Serializable
enum class OcrStatus { OK, FAILED }

// ---- 기준 정보 배포 (F-43): 항상 전체 목록 ----

@Serializable
@SerialName("USERS")
data class UsersPayload(val users: List<UserDto>) : InboxPayload

@Serializable
@SerialName("PLACES")
data class PlacesPayload(val places: List<PlaceDto>) : InboxPayload

@Serializable
@SerialName("SCHEDULES")
data class SchedulesPayload(val schedules: List<ScheduleDto>) : InboxPayload

@Serializable
@SerialName("RECIPIENTS")
data class RecipientsPayload(val recipients: List<RecipientDto>) : InboxPayload

/**
 * 회사 메일 발신 계정 (F-24, F-54). 관리자 웹에서 정하면 회사의 모든 차에 간다. [account]가 null이면 회사 계정을 지웠다는 뜻.
 * 차는 이 계정으로 운행기록부를 **직접** 보낸다(엑셀은 서버를 거치지 않는다).
 */
@Serializable
@SerialName("MAIL_ACCOUNT")
data class MailAccountPayload(val account: MailAccountDto?) : InboxPayload

/**
 * SMTP 접속 정보. [password]는 서버 inbox 표에 평문으로 남기지 않으려고 저장할 때는 null이고,
 * 차가 편지를 가져가는 순간 서버가 채운다. [toString]은 비밀번호를 가린다(CONVENTIONS 7.6).
 */
@Serializable
data class MailAccountDto(
    val host: String,
    val port: Int = 587,
    val username: String,
    val password: String? = null,
    val fromName: String? = null,
    val startTls: Boolean = true,
) {
    override fun toString(): String =
        "MailAccountDto(host=$host, port=$port, username=$username, password=***, fromName=$fromName, startTls=$startTls)"
}

@Serializable
data class UserDto(val userId: String, val name: String, val dept: String?, val email: String?)

@Serializable
enum class PlaceKind { OFFICE, BRANCH, CLIENT, GAS_STATION, HOME }

@Serializable
data class PlaceDto(
    val placeId: String,
    val label: String,
    val kind: PlaceKind,
    val lat: Double,
    val lng: Double,
    val radiusM: Int = 150,
    val defaultPurpose: String? = null,
)

@Serializable
data class ScheduleDto(
    val scheduleId: String,
    val vehicleId: String,
    val userId: String?,
    val startsAt: String,
    val endsAt: String,
    val destPlaceId: String?,
    val purposeCategory: String?,
    val purposeDetail: String?,
)

@Serializable
data class RecipientDto(val email: String, val name: String?)

// ---- 기기 등록 (F-40) ----

@Serializable
data class DeviceRegisterRequest(val code: String)

@Serializable
data class DeviceRegisterResponse(
    @SerialName("device_token") val deviceToken: String,
    @SerialName("vehicle_id") val vehicleId: String,
    @SerialName("org_id") val orgId: String,
    /** 차량 번호판. 운행기록부 파일명·메일 제목·설정 화면 표시에 쓴다. 옛 서버면 null. */
    @SerialName("plate_no") val plateNo: String? = null,
    /** 차종. 설정 화면 표시용. */
    val model: String? = null,
)

// ---- 영수증 QR (F-30) ----

/** POST /v1/qr-tokens 응답. 차는 [url]을 QR로 보여 준다. */
@Serializable
data class QrTokenResponse(val url: String, @SerialName("expires_at") val expiresAt: String)
