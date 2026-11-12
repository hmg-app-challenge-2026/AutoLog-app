package kr.autolog.vehicle.overlay

import kr.autolog.vehicle.core.SessionState
import kr.autolog.vehicle.ui.design.formatKm

/** 알약 한 줄의 값. [km] = 표시용 거리(계기판을 못 읽으면 null → "거리 미확정"), [elapsedMin] = 기록 시작부터 지난 분. */
data class RecordingPillContent(val km: String?, val elapsedMin: Long)

/**
 * 기록이 아직 끝나지 않았을 때(기록 중·종료 대기) 값이 있다 — 끝남·대기에서는 알약을 그리지 않는다.
 * 종료 대기도 보인다: 신호에 10초 넘게 서면 앱 A가 "움직임 없음" 대기에 들어가는데, 그때마다 알약이 사라지면 기록이 멈춘 줄 안다(D-37).
 */
fun recordingPillContent(state: SessionState, nowMs: Long): RecordingPillContent? {
    val (trip, liveKm) = when (state) {
        is SessionState.Recording -> state.trip to state.liveKm
        is SessionState.StopCandidate -> state.trip to state.liveKm
        else -> return null
    }
    val km = liveKm?.takeIf { it.isFinite() && it >= 0.0 }
    return RecordingPillContent(formatKm(km), (nowMs - trip.startTime).coerceAtLeast(0) / MS_PER_MIN)
}

/** 알약을 띄우는가: 기록 중 · AutoLog 화면이 안 보임 · 화면 위 표시 권한 있음. */
fun shouldShowRecordingPill(content: RecordingPillContent?, appVisible: Boolean, allowed: Boolean): Boolean =
    content != null && !appVisible && allowed

private const val MS_PER_MIN = 60_000L
