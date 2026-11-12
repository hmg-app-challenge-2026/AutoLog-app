package kr.autolog.vehicle.ui.report

import kr.autolog.vehicle.common.AppZone
import kr.autolog.vehicle.core.Origin
import kr.autolog.vehicle.core.RecipientInfo
import kr.autolog.vehicle.data.repository.MonthSummary
import kr.autolog.vehicle.data.repository.ServerLink
import kr.autolog.vehicle.data.repository.UploadInfo
import kr.autolog.vehicle.ui.design.formatKm
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/*
 * 운행기록부 내보내기 상태 계산(순수 함수, ReportReducerTest 대상). 입력은 Repository 값 타입만.
 */

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

private const val PERCENT = 100

@Suppress("LongParameterList") // 화면 하나가 모으는 원천이 이만큼이다 — 묶음 타입을 따로 두면 오히려 읽기 어렵다
internal fun reduceReport(
    month: YearMonth,
    current: YearMonth,
    summary: MonthSummary?,
    recipients: List<RecipientInfo>,
    deselected: Set<String>,
    step: ReportStep,
    smtpConfigured: Boolean,
    link: ServerLink,
    upload: UploadInfo,
): ReportUiState = ReportUiState(
    month = month,
    canGoNext = month < current,
    summary = summary?.let {
        ReportSummaryUi(
            tripCount = it.tripCount,
            totalKm = formatKm(it.totalKm).orEmpty(),
            businessPercent = if (it.totalKm > 0) (it.businessKm / it.totalKm * PERCENT).roundToInt() else null,
            reviewCount = it.reviewCount,
        )
    },
    // 서버 배포분(회사가 정한 수신자) 먼저, 같은 출처 안에서는 원래 순서
    recipients = recipients.sortedBy { it.origin != Origin.SERVER }.map {
        RecipientUi(it.email, it.name, it.origin == Origin.SERVER, it.email !in deselected)
    },
    step = step,
    smtpConfigured = smtpConfigured,
    // 업로드가 실제로 켜져 있을 때만 — 서버에 연결만 돼 있고 전송 계약이 없으면(실제 앱 지금, G-04) "자동 전송돼요"는 사실이 아니다
    serverUpload = if (link is ServerLink.Linked && upload.enabled) {
        ServerUploadUi(upload.lastUploadAt?.let { Instant.ofEpochMilli(it).atZone(AppZone).format(TIME) })
    } else {
        null
    },
)
