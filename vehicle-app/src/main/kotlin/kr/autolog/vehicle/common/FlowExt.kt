package kr.autolog.vehicle.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform

/** [periodMs]마다 현재 시각. 카운트다운·경과 시간 표시용. */
fun tickerFlow(clock: AppClock, periodMs: Long = 1_000): Flow<Long> = flow {
    while (true) {
        emit(clock.nowMs())
        delay(periodMs)
    }
}

/** 첫 값은 바로, 이후는 [periodMs]에 최대 한 번(가장 최근 값). 스펙 6.11 거리 표시 1초 스로틀. */
fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> = conflate().transform {
    emit(it)
    delay(periodMs)
}
