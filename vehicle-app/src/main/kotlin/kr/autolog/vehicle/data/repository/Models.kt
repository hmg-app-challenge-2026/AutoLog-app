package kr.autolog.vehicle.data.repository

import kr.autolog.vehicle.core.PurposeDetail
import kr.autolog.vehicle.core.ReportFile

// 화면(ui/)이 쓰는 Repository 값 타입. core 서비스 계약이 바뀌어도 이 타입은 유지한다(스펙 6.6).

/** 성공·실패만 있는 동작 결과. [reason]은 화면에 그대로 보여 줄 수 있는 문장. */
sealed interface Outcome {
    data object Ok : Outcome

    data class Failed(val reason: String) : Outcome
}

enum class UploadMark { PENDING, DONE, FAILED }

/** 이력 한 줄. 비어 있는 값은 미확정. */
data class HistoryItem(
    val tripId: String,
    val startTime: Long,
    val endTime: Long?,
    val driverName: String?,
    val driverUnset: Boolean,
    val origin: String?,
    val destination: String?,
    val distanceKm: Double?,
    val purpose: PurposeDetail?,
    val upload: UploadMark?,
)

/** 운행기록부 요약(스펙 7.7). 거리 미확정은 km 합계에서 빠지고 [reviewCount]에 들어간다. */
data class MonthSummary(val tripCount: Int, val totalKm: Double, val businessKm: Double, val reviewCount: Int)

sealed interface BuildOutcome {
    data class Built(val file: ReportFile) : BuildOutcome

    data class Failed(val reason: String) : BuildOutcome
}

/** 사내 서버 연결(스펙 8장). */
sealed interface ServerLink {
    data object NotLinked : ServerLink

    data class Linked(val vehicleLabel: String, val serverUrl: String, val lastSyncAt: Long?) : ServerLink

    data class Unreachable(val vehicleLabel: String, val serverUrl: String) : ServerLink
}

data class UploadInfo(val enabled: Boolean, val pendingCount: Int, val lastUploadAt: Long?, val lastError: String?)
