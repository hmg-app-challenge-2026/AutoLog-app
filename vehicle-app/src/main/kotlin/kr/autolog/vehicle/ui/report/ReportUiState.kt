package kr.autolog.vehicle.ui.report

import kr.autolog.vehicle.core.MailError
import java.time.YearMonth

/**
 * 운행기록부 내보내기(스펙 7.7, F-21·F-22). 한 달을 골라 → [운행기록부 만들기] → [보내기 · N명] — 진입 후 2탭으로 발송.
 * 상태 계산은 `ReportReducer.kt`의 순수 함수.
 */
data class ReportUiState(
    val month: YearMonth,
    val canGoNext: Boolean = false,
    /** 고른 달 요약. null = 불러오는 중. */
    val summary: ReportSummaryUi? = null,
    /** 받는 사람(서버 배포분 먼저). 처음엔 모두 고른 상태. */
    val recipients: List<RecipientUi> = emptyList(),
    val step: ReportStep = ReportStep.Ready,
    /** 메일 계정이 연결돼 있는지 — 아니면 보내기 대신 [설정에서 메일 계정 연결]. */
    val smtpConfigured: Boolean = true,
    /** 사내 서버가 연결돼 있으면 "사내 서버로도 자동 전송돼요 · 마지막 전송 10:24"(스펙 8장). 연결 안 됐으면 null. */
    val serverUpload: ServerUploadUi? = null,
) {
    val selectedCount: Int get() = recipients.count { it.selected }
}

/** [businessPercent] = 업무 거리 / 총 거리(반올림). 거리를 아는 운행이 없으면 null. */
data class ReportSummaryUi(val tripCount: Int, val totalKm: String, val businessPercent: Int?, val reviewCount: Int)

data class RecipientUi(val email: String, val name: String?, val fromServer: Boolean, val selected: Boolean)

/** [lastUploadTime] = "10:24"(오늘이 아니어도 시각만), 아직 보낸 적 없으면 null. */
data class ServerUploadUi(val lastUploadTime: String?)

/** 만들기 → 보내기 단계. 달을 바꾸면 [Ready]로 돌아간다(다른 달의 파일을 보내지 않게). */
sealed interface ReportStep {
    data object Ready : ReportStep

    data object Building : ReportStep

    /** [reason]은 원천이 준 문장 그대로. */
    data class BuildFailed(val reason: String) : ReportStep

    data object Built : ReportStep

    data object Sending : ReportStep

    data class Sent(val count: Int) : ReportStep

    data class SendFailed(val error: MailError) : ReportStep
}

sealed interface ReportAction {
    data object PrevMonth : ReportAction

    data object NextMonth : ReportAction

    /** 받는 사람 체크 켜고 끄기. */
    data class ToggleRecipient(val email: String) : ReportAction

    /** 맨 아래 주 버튼 하나 — 단계에 따라 만들기 · 보내기 · 다시 보내기 · 설정으로 · 완료. */
    data object Primary : ReportAction

    /** 화면에 다시 들어왔을 때(설정에서 메일 계정을 연결하고 돌아온 경우 등) 메일 설정을 다시 읽는다. */
    data object Resume : ReportAction

    data object Back : ReportAction
}
