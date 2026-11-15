package kr.htaxlog.vehicle.fake

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.htaxlog.vehicle.core.PurposeDetail
import kr.htaxlog.vehicle.core.VoiceApi
import kr.htaxlog.vehicle.core.VoiceFuel
import kr.htaxlog.vehicle.core.VoicePurpose
import kr.htaxlog.vehicle.core.VoiceResult

/** [VoiceApi] 가짜. [listenMs]만큼 듣는 척한 뒤 정해 둔 결과를 준다. */
class FakeVoiceApi(
    var purpose: VoiceResult<VoicePurpose> = VoiceResult.Recognized("거래처 미팅", VoicePurpose(PurposeDetail.CLIENT_VISIT)),
    var fuel: VoiceResult<VoiceFuel> = VoiceResult.Recognized("휘발유 칠만 원 사십 리터", VoiceFuel(70_000, 40.0)),
    private val listenMs: Long = 1_500,
    isAvailable: Boolean = true,
) : VoiceApi {
    override val available: StateFlow<Boolean> = MutableStateFlow(isAvailable)

    override suspend fun listenPurpose(): VoiceResult<VoicePurpose> {
        delay(listenMs)
        return purpose
    }

    override suspend fun listenFuel(): VoiceResult<VoiceFuel> {
        delay(listenMs)
        return fuel
    }
}
