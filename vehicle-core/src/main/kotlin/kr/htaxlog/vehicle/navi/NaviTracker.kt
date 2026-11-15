package kr.htaxlog.vehicle.navi

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock

/**
 * 내비 이벤트로 목적지·경로 상태를 관리한다. SDK와 Android에 의존하지 않아 JVM 테스트가 된다.
 *
 * NaviHelper의 get*() 함수는 값을 돌려주지 않고 리스너로 응답한다.
 * 그래서 "요청 → 다음 응답 이벤트 기다리기"를 [requestAndAwait]로 묶는다.
 */
class NaviTracker(
    private val clock: Clock = Clock.systemUTC(),
    private val historyLimit: Int = DEFAULT_HISTORY_LIMIT,
) {
    private val _destination = MutableStateFlow<DestinationSnapshot?>(null)

    /** 현재 내비 목적지. 경로가 없으면 null. */
    val destination: StateFlow<DestinationSnapshot?> = _destination.asStateFlow()

    private val _history = MutableStateFlow<List<DestinationChange>>(emptyList())

    /** 목적지 변경 이력(최근 [historyLimit]건, 오래된 순). 같은 목적지가 다시 오면 기록하지 않는다. */
    val history: StateFlow<List<DestinationChange>> = _history.asStateFlow()

    private val _isRouting = MutableStateFlow(false)
    val isRouting: StateFlow<Boolean> = _isRouting.asStateFlow()

    private val _arrivals = MutableSharedFlow<ArrivalEvent>(extraBufferCapacity = EVENT_BUFFER)

    /** 내비 도착 이벤트. 앱 A가 정차 감지 신호로 구독한다. */
    val arrivals: SharedFlow<ArrivalEvent> = _arrivals.asSharedFlow()

    private val _events = MutableSharedFlow<NaviEvent>(extraBufferCapacity = EVENT_BUFFER)

    /** 가공 전 이벤트 전체. 응답 대기와 디버그 로그에 쓴다. */
    val events: SharedFlow<NaviEvent> = _events.asSharedFlow()

    fun onEvent(event: NaviEvent) {
        when (event) {
            is NaviEvent.DestinationInfoReceived -> updateDestination(event.destination)
            NaviEvent.RouteCleared -> {
                updateDestination(null)
                _isRouting.value = false
            }
            is NaviEvent.RoutingStateReceived -> _isRouting.value = event.isRouting
            is NaviEvent.Arrived -> _arrivals.tryEmit(ArrivalEvent(event.label, event.address, clock.millis()))
            NaviEvent.DestinationChanged,
            is NaviEvent.CurrentLocationReceived,
            is NaviEvent.RecentDestinationsReceived,
            is NaviEvent.Error,
            -> Unit
        }
        _events.tryEmit(event)
    }

    /**
     * [request]를 보내고 [T] 타입 응답 이벤트를 기다린다. [timeoutMs] 안에 안 오면 null.
     * 응답을 놓치지 않도록 구독을 먼저 시작한 뒤 요청한다.
     */
    suspend inline fun <reified T : NaviEvent> requestAndAwait(
        timeoutMs: Long = DEFAULT_RESPONSE_TIMEOUT_MS,
        crossinline request: () -> Unit,
    ): T? = coroutineScope {
        val response = async(start = CoroutineStart.UNDISPATCHED) { events.filterIsInstance<T>().first() }
        request()
        withTimeoutOrNull(timeoutMs) { response.await() }.also { response.cancel() }
    }

    private fun updateDestination(next: DestinationSnapshot?) {
        val previous = _destination.value
        _destination.value = next
        if (previous?.isSamePlace(next) == true || (previous == null && next == null)) return
        _history.value = (_history.value + DestinationChange(next, clock.millis())).takeLast(historyLimit)
    }

    /** 남은 거리처럼 계속 바뀌는 값은 무시하고 같은 목적지인지 본다. */
    private fun DestinationSnapshot.isSamePlace(other: DestinationSnapshot?): Boolean =
        other != null && label == other.label && lat == other.lat && lng == other.lng

    companion object {
        const val DEFAULT_HISTORY_LIMIT = 20
        const val DEFAULT_RESPONSE_TIMEOUT_MS = 3_000L
        private const val EVENT_BUFFER = 64
    }
}
