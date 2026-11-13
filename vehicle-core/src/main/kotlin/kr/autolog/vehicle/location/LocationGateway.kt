package kr.autolog.vehicle.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kr.autolog.vehicle.core.LocationFix
import kr.autolog.vehicle.core.sdkListenerFlow
import java.time.Clock

/**
 * Fused Location SDK 게이트웨이 (F-07). FusedLocation SDK 1.1.3 기준.
 * 1.0.4는 시스템 서비스 `IPlacesenseService/default`를 찾는데 Pleos Connect v2.1.0에는 없어(binder null) 콜백이 오지 않았다.
 * 1.1.3은 `FusedLocationService`에 바인딩한다(2026-11-13 에뮬레이터 확인, sdk-findings 4).
 *
 * 경로는 저장하지 않는다. 최신 좌표 1건만 메모리에 두고, 운행 시작·종료 때 [snapshot]으로 1건씩 꺼낸다.
 * 권한: pleos.car.permission.FUSED_LOCATION (런타임 요청 필요 여부는 [검증 필요])
 */
class LocationGateway(
    context: Context,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    private val log: (String) -> Unit = {},
) {
    private val fused = FusedLocation(context.applicationContext)
    private var collectJob: Job? = null

    private val _latestFix = MutableStateFlow<LocationFix?>(null)
    val latestFix: StateFlow<LocationFix?> = _latestFix.asStateFlow()

    /** 좌표 콜백 스트림. collect하는 동안만 SDK 콜백이 등록된다. */
    val fixes: Flow<LocationFix> =
        sdkListenerFlow<FusedLocationListener, LocationFix>(
            register = { fused.registerFusedLocationCallback(it) },
            unregister = { fused.unregisterFusedLocationCallback(it) },
            makeListener = { emit, _ ->
                object : FusedLocationListener {
                    // 1.1.3은 null을 줄 수 있다(Kotlin 메타데이터 `LocationData?`) — 좌표가 없으면 건너뛴다
                    override fun onChanged(location: LocationData?) {
                        location?.let { emit(it.toFix()) }
                    }

                    // 흐름을 닫으면 다시 받지 못한다 — 남기고 계속 듣는다(다음 좌표가 오면 그대로 쓴다)
                    override fun onError(exception: Exception) {
                        log("fused error: ${exception.message}")
                    }
                }
            },
        )

    fun start() {
        fused.initialize()
        collectJob = scope.launch { fixes.collect { _latestFix.value = it } }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        fused.release()
    }

    /**
     * 출발·도착 스냅샷용 좌표 1건. 최신 값이 신선하면 바로, 아니면 다음 콜백을 [timeoutMs]까지 기다린다.
     * 그래도 없으면 null → 호출자가 NaviHelper 현재 위치 → 직전 도착지 → 사용자 선택으로 폴백(F-07).
     */
    suspend fun snapshot(timeoutMs: Long = DEFAULT_SNAPSHOT_TIMEOUT_MS): LocationFix? {
        FixSelector.freshOrNull(_latestFix.value, clock.millis())?.let { return it }
        return withTimeoutOrNull(timeoutMs) {
            _latestFix.filterNotNull().first { FixSelector.freshOrNull(it, clock.millis()) != null }
        }
    }

    private fun LocationData.toFix() = LocationFix(
        lat = latitude,
        lng = longitude,
        headingDeg = heading,
        confidence = confidence,
        timestampMs = timestampMillis,
    )

    private companion object {
        const val DEFAULT_SNAPSHOT_TIMEOUT_MS = 3_000L
    }
}
