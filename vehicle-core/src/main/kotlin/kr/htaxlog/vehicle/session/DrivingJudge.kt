package kr.htaxlog.vehicle.session

import kr.htaxlog.vehicle.core.VehicleMotion
import kr.htaxlog.vehicle.vehicle.Gear

/**
 * 차의 움직임 판정 (F-13, 프론트 스펙 1.1.1). 순수 함수. 화면의 사용 3단계와 정차 감지가 이 결과를 쓴다.
 *
 * - 기어가 P면 주차.
 * - 차속이 [MOVING_SPEED_KPH]를 넘으면 주행(계기판을 기다리지 않는다 — 0.1 km 단위라 저속에서는 10초 넘게 안 바뀐다).
 * - 기어가 P가 아니면 계기판이 최근 [MOVING_WINDOW_MS] 안에 늘었으면 주행, 아니면 정차.
 * - 기어 신호가 없으면(최후 규칙) 거리 불변 [PARKED_FALLBACK_MS] 이상이거나 관찰 시작 뒤 한 번도 안 움직였으면 주차.
 * - 계기판도 모르면 판정 불가(null) — 화면은 주행으로 본다(보수적).
 */
object DrivingJudge {
    /** 계기판 변화가 이 안에 있으면 주행 중. 기능명세 용어 "정차 상태 = 변화 없음 ≥ 10초". */
    const val MOVING_WINDOW_MS = 10_000L

    /** 기어 신호가 없을 때 이만큼 거리 불변이면 주차로 본다(T_PARKED_FALLBACK_SEC = 60, 프론트 스펙 1.1.1). */
    const val PARKED_FALLBACK_MS = 60_000L

    /** 이 속도(km/h)를 넘으면 움직이는 중. 정지 상태의 센서 잡음(0.x km/h)을 거른다. */
    const val MOVING_SPEED_KPH = 3.0

    fun isMovingSpeed(speedKph: Double?): Boolean = speedKph != null && speedKph > MOVING_SPEED_KPH

    /**
     * @param gear 마지막 기어. 기어 신호가 아예 없으면 null.
     * @param odometerKnown 계기판을 한 번이라도 읽었는지.
     * @param lastMovedAt 마지막으로 움직인 시각(계기판 증가 또는 차속). 관찰 시작 뒤 안 움직였으면 null.
     * @param speedKph 마지막 차속. 차속 신호가 없으면 null.
     */
    fun judge(
        gear: Gear?,
        odometerKnown: Boolean,
        lastMovedAt: Long?,
        now: Long,
        speedKph: Double? = null,
    ): VehicleMotion? {
        if (gear == Gear.PARK) return VehicleMotion.PARKED
        if (isMovingSpeed(speedKph)) return VehicleMotion.MOVING
        if (!odometerKnown) return null
        val sinceMove = lastMovedAt?.let { now - it }
        val moving = sinceMove != null && sinceMove < MOVING_WINDOW_MS
        return when {
            moving -> VehicleMotion.MOVING
            gear != null && gear != Gear.UNKNOWN -> VehicleMotion.STOPPED
            sinceMove == null || sinceMove >= PARKED_FALLBACK_MS -> VehicleMotion.PARKED
            else -> VehicleMotion.STOPPED
        }
    }
}
