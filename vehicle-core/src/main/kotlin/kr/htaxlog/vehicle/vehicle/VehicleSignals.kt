package kr.htaxlog.vehicle.vehicle

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.math.BigDecimal

/**
 * 세션(session/)이 쓰는 차량 신호. [VehicleGateway]가 Pleos Vehicle SDK로 구현하고, 테스트는 가짜로 바꾼다.
 * SDK 타입은 이 경계 밖으로 나가지 않는다. 실패는 예외 대신 null·빈 흐름으로 알린다(CONVENTIONS 7.5).
 */
interface VehicleSignals {
    /** 지금 계기판 누적거리 1회. 못 읽으면 null — 호출자는 거리를 추정하지 않고 UNAVAILABLE로 둔다(F-05). */
    suspend fun readOdometer(): OdometerReading?

    /** 계기판 누적거리 변화. collect하는 동안만 SDK 리스너가 등록된다. */
    val odometer: Flow<OdometerReading>

    /** 지금 기어 1회. 못 읽거나 차가 모르면 null. */
    suspend fun readGear(): Gear?

    /** 기어 변화. [Gear.UNKNOWN]은 흘려보내지 않는다. */
    val gear: Flow<Gear>

    /** 시동이 꺼지는 순간(OFF·ACC·LOCK). 신호가 없는 차면 아무것도 오지 않는다. */
    val ignitionOff: Flow<Unit> get() = emptyFlow()

    /** 차속(km/h). 움직임 판단에만 쓴다(거리는 계기판). 신호·권한이 없는 차면 아무것도 오지 않는다. */
    val speedKph: Flow<Double> get() = emptyFlow()
}

/** 계기판 누적거리 한 건. [km]는 SDK 원본 값을 10진수 표기 그대로 옮긴 것이다. */
data class OdometerReading(val km: Double, val at: Long)

/** HTaxLog가 구분하는 기어. 수동 1~9단은 [DRIVE]로 본다. */
enum class Gear { PARK, DRIVE, REVERSE, NEUTRAL, UNKNOWN }

/**
 * SDK의 Float km를 10진수 표기 그대로 Double로 바꾼다.
 * `18443.9f.toDouble()`은 18443.900390625가 되지만 이 함수는 18443.9를 돌려준다. NaN·무한대·음수는 null.
 */
fun Float.toOdometerKm(): Double? {
    if (isNaN() || isInfinite() || this < 0f) return null
    return BigDecimal(toString()).toDouble()
}

/**
 * [block]이 값을 줄 때까지 [attempts]번 시도하고, 사이마다 [intervalMs]만큼 기다린다. 끝내 없으면 null.
 * Vehicle 서비스는 `initialize()`에 완료 콜백이 없어서, 첫 조회가 성공할 때를 연결 완료로 본다(F-01 "5초×12").
 */
suspend fun <T : Any> retryUntilValue(attempts: Int, intervalMs: Long, block: suspend () -> T?): T? {
    repeat(attempts) { i ->
        block()?.let { return it }
        if (i < attempts - 1) delay(intervalMs)
    }
    return null
}
