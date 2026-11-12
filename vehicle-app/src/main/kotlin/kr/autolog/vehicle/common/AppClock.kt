package kr.autolog.vehicle.common

/** 현재 시각(epoch millis, UTC). 테스트에서 바꿔 끼운다(Backend CONVENTIONS 7.4). */
fun interface AppClock {
    fun nowMs(): Long

    companion object {
        val System = AppClock { java.lang.System.currentTimeMillis() }
    }
}
