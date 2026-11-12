package kr.autolog.vehicle.session

import kr.autolog.vehicle.core.SessionState
import kr.autolog.vehicle.core.StopReason

/**
 * 세션 상태 → 알림 내용 (F-03, F-16, 프론트 스펙 R-7·7.2.1). 순수 함수 — Android Notification은 서비스가 만든다.
 * 주행 중에는 앱 화면이 없으므로 사람이 보는 것은 이 알림뿐이다.
 */
data class TripNotificationSpec(
    val channel: Channel,
    val title: String,
    val text: String,
    /** 카운트다운 목표 시각(epoch millis). null이면 카운트다운 없음. */
    val countdownTo: Long? = null,
    val actions: List<Action> = emptyList(),
    /** 탭하면 이 운행의 목적 확인 화면으로(DeepLink). null이면 앱 첫 화면. */
    val reviewTripId: String? = null,
) {
    /** LOW = 지속 알림(조용히), ALERT = 헤즈업. */
    enum class Channel { LOW, ALERT }

    enum class Action { RESUME, FINALIZE }

    companion object {
        /**
         * [appVisible] = AutoLog 화면이 보이는가([kr.autolog.vehicle.core.AppPresenceApi]). 보이는 동안엔 헤즈업(소리)을 띄우지 않는다 —
         * 화면이 결과 카드로 같은 것을 이미 보여 주므로 겹친다(프론트 스펙 D-37). 내용은 같고 조용한 채널로만 바꾼다.
         */
        fun from(state: SessionState, appVisible: Boolean = false): TripNotificationSpec =
            spec(state).let { if (appVisible) it.copy(channel = Channel.LOW) else it }

        private fun spec(state: SessionState): TripNotificationSpec = when (state) {
            SessionState.Idle -> TripNotificationSpec(Channel.LOW, "AutoLog", "다음 운행을 기다리는 중")
            is SessionState.Recording -> recording(state.liveKm, state.trip.destinationLabel)
            // 멈춤·내비 도착 대기는 신호 대기마다 생긴다 — 묻지 않고 "기록 중" 그대로(소리·헤즈업 없음, D-37).
            // 3분이 다 되어 끝나면 그때 "운행 종료"로 알린다
            is SessionState.StopCandidate -> if (state.reason in QUIET_STOPS) {
                recording(state.liveKm, state.trip.destinationLabel)
            } else {
                TripNotificationSpec(
                    channel = Channel.ALERT,
                    title = "기록을 마칠까요?",
                    text = line(km(state.liveKm), state.trip.destinationLabel),
                    countdownTo = state.deadlineAt,
                    actions = listOf(Action.RESUME, Action.FINALIZE),
                )
            }
            is SessionState.PendingReview -> TripNotificationSpec(
                channel = Channel.ALERT,
                title = "운행 종료",
                text = line(km(state.distanceKm), "목적을 확인하세요"),
                reviewTripId = state.trip.tripId,
            )
            is SessionState.NeedsRecovery -> TripNotificationSpec(
                channel = Channel.ALERT,
                title = "기록하지 못한 운행이 있어요",
                text = "앱을 열어 기록할지 정해 주세요",
            )
        }

        /** 신호 대기·정체에서도 생기는 종료 대기 — 사람에게 묻지 않는다. */
        private val QUIET_STOPS = setOf(StopReason.NO_MOVEMENT, StopReason.NAVI_ARRIVED)

        private fun recording(liveKm: Double?, destination: String?) =
            TripNotificationSpec(Channel.LOW, "기록 중", line(km(liveKm), destination))

        /** 거리 표시: 소수 1자리, 모르면 "거리 확인 필요"(추정하지 않는다). */
        fun km(value: Double?): String = value?.let { "%.1f km".format(it) } ?: "거리 확인 필요"

        private fun line(vararg parts: String?) = parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")
    }
}
