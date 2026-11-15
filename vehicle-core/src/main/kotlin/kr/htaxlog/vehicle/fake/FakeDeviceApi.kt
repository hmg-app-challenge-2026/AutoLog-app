package kr.htaxlog.vehicle.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.htaxlog.vehicle.core.ConnectionState
import kr.htaxlog.vehicle.core.DeviceApi
import kr.htaxlog.vehicle.core.RegisterResult
import java.time.Clock

/**
 * [DeviceApi] 가짜 (프론트 초안에서 옮김). [registered]면 사내 서버에 연결된 상태로 시작한다.
 * 연결 코드 형식은 relay와 같다: 6자리, 숫자 2–9와 영문 대문자(0·1·I·O 제외). 소문자도 받는다.
 */
class FakeDeviceApi(private val clock: Clock = Clock.systemUTC(), registered: Boolean = true) : DeviceApi {
    private val _connection = MutableStateFlow(
        if (registered) {
            ConnectionState.Registered(VEHICLE, "https://htaxlog.acme.co.kr", clock.millis() - 600_000)
        } else {
            ConnectionState.NotRegistered
        },
    )
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    override suspend fun register(serverUrl: String, code: String): RegisterResult {
        if (!serverUrl.startsWith("https://") && !serverUrl.startsWith("http://")) {
            return RegisterResult.Invalid("서버 주소는 https:// 로 시작해야 해요")
        }
        if (!CODE.matches(code.uppercase())) return RegisterResult.Invalid("연결 코드 6자리를 확인해 주세요")
        _connection.value = ConnectionState.Registered(VEHICLE, serverUrl.trimEnd('/'), clock.millis())
        return RegisterResult.Ok
    }

    /** 데모: 서버 끊김. */
    fun setUnreachable() {
        val r = _connection.value as? ConnectionState.Registered ?: return
        _connection.value = ConnectionState.Unreachable(r.vehicleLabel, r.serverUrl)
    }

    companion object {
        const val VEHICLE = "12가3456 · 아이오닉 6"
        val CODE = Regex("[2-9A-HJ-NP-Z]{6}")
    }
}
