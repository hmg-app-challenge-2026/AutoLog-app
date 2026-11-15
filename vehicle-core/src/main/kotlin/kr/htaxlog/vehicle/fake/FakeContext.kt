package kr.htaxlog.vehicle.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.htaxlog.vehicle.core.ArrivalInfo
import kr.htaxlog.vehicle.core.ContextApi
import kr.htaxlog.vehicle.core.DestinationInfo
import kr.htaxlog.vehicle.core.LocationFix
import kr.htaxlog.vehicle.core.NaviCurrentLocation

/**
 * [ContextApi] 가짜. 판교 거래처로 가는 중인 상태로 시작한다.
 * 화면·테스트에서 [setDestination], [setFix], [arrive]로 상황을 바꾼다.
 */
class FakeContext(
    destination: DestinationInfo? = SAMPLE_DESTINATION,
    fix: LocationFix? = SAMPLE_FIX,
    private val recent: List<DestinationInfo> = SAMPLE_RECENT,
) : ContextApi {
    private val _destination = MutableStateFlow(destination)
    private val _latestFix = MutableStateFlow(fix)
    private val _arrivals = MutableSharedFlow<ArrivalInfo>(extraBufferCapacity = 8)
    private var naviLocation: NaviCurrentLocation? = null

    override val destination: StateFlow<DestinationInfo?> = _destination.asStateFlow()
    override val latestFix: StateFlow<LocationFix?> = _latestFix.asStateFlow()
    override val arrivals: Flow<ArrivalInfo> = _arrivals.asSharedFlow()

    override suspend fun freshFix(): LocationFix? = _latestFix.value

    override suspend fun currentNaviLocation(): NaviCurrentLocation? = naviLocation

    override suspend fun recentDestinations(): List<DestinationInfo> = recent

    fun setDestination(d: DestinationInfo?) {
        _destination.value = d
    }

    fun setFix(f: LocationFix?) {
        _latestFix.value = f
    }

    fun setNaviLocation(location: NaviCurrentLocation?) {
        naviLocation = location
    }

    fun arrive(at: Long = System.currentTimeMillis()) {
        val d = _destination.value
        _arrivals.tryEmit(ArrivalInfo(d?.label, d?.address, at))
    }

    companion object {
        val SAMPLE_DESTINATION = DestinationInfo("판교 테크노밸리 거래처", "경기 성남시 분당구 판교역로 1", 37.401, 127.108)
        val SAMPLE_FIX = LocationFix(37.395, 127.111, 30f, 0.9f, 0L)
        val SAMPLE_RECENT =
            listOf(
                SAMPLE_DESTINATION,
                DestinationInfo("본사", "서울 강남구 테헤란로 1", 37.498, 127.028),
                DestinationInfo("수원 지점", "경기 수원시 팔달구 1", 37.266, 127.000),
            )
    }
}
