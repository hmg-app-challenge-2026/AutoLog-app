package kr.autolog.vehicle.session

import kr.autolog.vehicle.core.TripLimits
import java.math.BigDecimal

/**
 * 운행 거리 계산과 검증 (F-05, 기술명세 2.8 FINALIZING). 순수 함수 — Android 의존 없음.
 * 거리는 언제나 계기판 차이(end − start)다. 못 구하면 null이고, 다른 값으로 채우지 않는다(추정 금지).
 */
object DistancePolicy {
    /** 1회 운행 상한. 넘으면 계기판 값 이상으로 본다. */
    const val MAX_TRIP_KM = TripLimits.MAX_TRIP_KM

    /** 이보다 짧고([MIN_DURATION_MS]) 덜 갔으면([MIN_DISTANCE_KM]) "이동 없음". */
    const val MIN_DURATION_MS = 60_000L
    const val MIN_DISTANCE_KM = 0.3

    sealed interface Verdict {
        /** 정상. [distanceKm]는 10진수 뺄셈 결과(18443.9 − 18420.5 = 23.4). */
        data class Ok(val distanceKm: Double) : Verdict

        /** 시작·종료 계기판 중 하나를 못 읽었거나 값이 이상하다 → distanceKm = null, UNAVAILABLE. */
        data class Unavailable(val reason: String) : Verdict

        /** 시동만 걸었다 끈 운행. 기록하지 않는다. */
        data object NoMovement : Verdict
    }

    fun evaluate(startKm: Double?, endKm: Double?, startTime: Long, endTime: Long): Verdict {
        if (startKm == null) return Verdict.Unavailable("start odometer unavailable")
        if (endKm == null) return Verdict.Unavailable("end odometer unavailable")
        val distance = difference(startKm, endKm)
        return when {
            distance < 0.0 -> Verdict.Unavailable("odometer decreased ($startKm → $endKm)")
            distance > MAX_TRIP_KM -> Verdict.Unavailable("distance over $MAX_TRIP_KM km ($distance)")
            endTime - startTime < MIN_DURATION_MS && distance < MIN_DISTANCE_KM -> Verdict.NoMovement
            else -> Verdict.Ok(distance)
        }
    }

    /** 사람이 고친 거리로 받을 수 있는 값: 0 초과 [MAX_TRIP_KM] 이하(F-05 수정). NaN·무한대는 거절. */
    fun isEditableKm(km: Double): Boolean = km > 0.0 && km <= MAX_TRIP_KM

    /** 두 계기판 값의 차이. Double 뺄셈의 꼬리(23.400000000001455)를 없애려고 10진수로 뺀다. */
    fun difference(startKm: Double, endKm: Double): Double =
        BigDecimal(endKm.toString()).subtract(BigDecimal(startKm.toString())).toDouble()
}
