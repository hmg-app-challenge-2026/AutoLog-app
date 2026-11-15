package kr.htaxlog.vehicle.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kr.htaxlog.shared.PlaceKind
import kr.htaxlog.vehicle.core.ExpenseSource
import kr.htaxlog.vehicle.core.Origin

// 기준 정보(기술명세 3.1). SERVER 분은 inbox 수신 때 통째로 바뀌고, LOCAL(차 안 입력)분은 남는다.

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val name: String,
    val dept: String?,
    val email: String?,
    val origin: Origin,
)

/** 운전자 선택 기록. 최근 순 정렬에 쓴다. */
@Entity(tableName = "recent_users")
data class RecentUserEntity(@PrimaryKey val userId: String, val lastUsedAt: Long)

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey val placeId: String,
    val label: String,
    val kind: PlaceKind,
    val lat: Double,
    val lng: Double,
    val radiusM: Int = 150,
    val defaultPurpose: String?,
    val origin: Origin,
)

@Entity(tableName = "schedules", indices = [Index("vehicleId", "startsAt")])
data class ScheduleEntity(
    @PrimaryKey val scheduleId: String,
    val vehicleId: String,
    val userId: String?,
    val startsAt: Long,
    val endsAt: Long,
    val destPlaceId: String?,
    val purposeDetail: String?,
)

@Entity(tableName = "recipients")
data class RecipientEntity(@PrimaryKey val email: String, val name: String?, val origin: Origin)

// 비용(F-18, F-19). 운행·비용은 어디로도 업로드하지 않는다.

@Entity(tableName = "expenses", indices = [Index("occurredAt"), Index("receiptSha256")])
data class ExpenseEntity(
    @PrimaryKey val expenseId: String,
    val kind: String = "FUEL",
    val occurredAt: Long,
    val amountWon: Int,
    val liters: Double?,
    val unitPrice: Int?,
    val discountWon: Int,
    val fuelType: String?,
    val merchant: String?,
    /** 끝 4자리 숫자만. */
    val cardLast4: String?,
    val source: ExpenseSource,
    val receiptId: String?,
    val receiptSha256: String?,
    val thumbUrl: String?,
    /** OCR 확신도 원본(JSON). */
    val ocrConfidenceJson: String?,
    /** 사용자가 고친 필드 이름(쉼표 구분). 원본 OCR 값은 영수증 payload에 남는다. */
    val editedFields: String?,
    val createdAt: Long,
)

@Entity(tableName = "trip_expense_links", primaryKeys = ["expenseId", "tripId"], indices = [Index("tripId")])
data class TripExpenseLinkEntity(val expenseId: String, val tripId: String)

/**
 * inbox로 받은 영수증(F-19). payload는 shared DTO JSON 그대로.
 * 확정하면 [confirmedAt]을 찍고 화면에서 사라진다. 서버 ack가 성공하면 행을 지우고, 실패하면 InboxSyncWorker가 다시 ack한다.
 */
@Entity(tableName = "receipt_inbox")
data class ReceiptInboxEntity(
    @PrimaryKey val receiptId: String,
    val payloadJson: String,
    val receivedAt: Long,
    /** 서버 inbox 편지 id(ack용). v2에서 추가. */
    val inboxId: Long? = null,
    /** 사용자가 확정한 시각. null = 아직 확인 전. v2에서 추가. */
    val confirmedAt: Long? = null,
)

/** 이미 처리한 inbox 편지. ack가 실패해도 같은 편지를 두 번 반영하지 않는다(F-11). */
@Entity(tableName = "inbox_seen")
data class InboxSeenEntity(@PrimaryKey val inboxId: Long, val seenAt: Long)

/** 만든 운행기록부 파일(F-21). */
@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey val reportId: String,
    /** "2026-11". */
    val yearMonth: String,
    val filePath: String,
    val createdAt: Long,
    /** 같은 데이터로 다시 만들지 않기 위한 해시. */
    val dataHash: String,
)
