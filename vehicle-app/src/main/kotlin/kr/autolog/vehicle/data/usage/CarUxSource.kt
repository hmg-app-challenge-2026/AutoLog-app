package kr.autolog.vehicle.data.usage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface CarUxSource {
    /** null = 연결 실패 → 보수적으로 PEEK까지만. */
    val state: StateFlow<CarUxState?>
}

/** demo flavor·테스트용. 기본값은 "제한 없음"(일반 에뮬레이터에서 전체 앱을 쓸 수 있게). */
class StaticCarUxSource(initial: CarUxState? = CarUxState(false, false)) : CarUxSource {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<CarUxState?> = _state.asStateFlow()

    fun set(state: CarUxState?) {
        _state.value = state
    }
}
