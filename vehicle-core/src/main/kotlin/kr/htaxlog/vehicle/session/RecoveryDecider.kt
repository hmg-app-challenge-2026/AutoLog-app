package kr.htaxlog.vehicle.session

/**
 * 앱 시작 때 할 일 결정 (F-10, 기술명세 2.12). 순수 함수.
 * 시동 OFF 콜백은 받지 못한다고 가정하고, 다음 시작 때 계기판 값으로 판단한다.
 */
object RecoveryDecider {
    /** 체크포인트와 지금 계기판 차이가 이보다 작으면 "그 뒤로 안 달렸다". */
    const val SAME_ODOMETER_KM = 0.1

    /** 기록 없이 이만큼 늘었으면 놓친 운행으로 묻는다. */
    const val MISSED_TRIP_KM = 1.0

    sealed interface Decision {
        /** 할 일 없음. */
        data object Nothing : Decision

        /** 계기판을 못 읽었다. 끝나지 않은 운행이 있으면 그대로 기록을 이어 간다. */
        data object ContinueRecording : Decision

        /** 체크포인트 = 지금 → 묻지 않고 그 값으로 운행을 끝낸다(0탭). */
        data class AutoFinalize(val endKm: Double) : Decision

        /** 앱이 죽은 동안 [extraKm]만큼 더 달렸다 → 복구 다이얼로그. */
        data class AskRecovery(val extraKm: Double) : Decision

        /** 운행 기록 없이 [gapKm]만큼 늘었다 → 놓친 운행 배너. */
        data class MissedTrip(val gapKm: Double) : Decision
    }

    /**
     * @param recordingLastKm 끝나지 않은 운행의 마지막 계기판(체크포인트, 없으면 시작값). 그런 운행이 없으면 null.
     * @param hasRecording 끝나지 않은 운행이 있는지.
     */
    fun decide(
        hasRecording: Boolean,
        recordingLastKm: Double?,
        nowKm: Double?,
        lastKnownKm: Double?,
        ignoredKm: Double?,
    ): Decision {
        if (nowKm == null) return if (hasRecording) Decision.ContinueRecording else Decision.Nothing
        if (hasRecording) {
            if (recordingLastKm == null) return Decision.ContinueRecording
            val extra = DistancePolicy.difference(recordingLastKm, nowKm)
            return if (extra < SAME_ODOMETER_KM) Decision.AutoFinalize(nowKm) else Decision.AskRecovery(extra)
        }
        if (lastKnownKm == null || ignoredKm == nowKm) return Decision.Nothing
        val gap = DistancePolicy.difference(lastKnownKm, nowKm)
        return if (gap >= MISSED_TRIP_KM) Decision.MissedTrip(gap) else Decision.Nothing
    }
}
