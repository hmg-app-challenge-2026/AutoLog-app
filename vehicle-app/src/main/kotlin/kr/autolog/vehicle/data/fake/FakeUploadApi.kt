package kr.autolog.vehicle.data.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.data.upload.UploadApi
import kr.autolog.vehicle.data.upload.UploadResult
import kr.autolog.vehicle.data.upload.UploadStatus

/** 사내 서버 전송 가짜(스펙 8장). 대기 2건으로 시작하고 [uploadNow]는 항상 성공한다. 실패 화면은 [nextFailure]로. */
class FakeUploadApi(private val clock: AppClock, var nextFailure: String? = null) : UploadApi {
    private val initial =
        UploadStatus(enabled = true, pendingCount = 2, lastUploadAt = clock.nowMs() - HOUR, lastError = null)
    private val _status = MutableStateFlow(initial)
    override val status: StateFlow<UploadStatus> = _status.asStateFlow()

    override suspend fun uploadNow(): UploadResult {
        nextFailure?.let {
            _status.value = _status.value.copy(lastError = it)
            return UploadResult.Failed(it)
        }
        val sent = _status.value.pendingCount
        _status.value = _status.value.copy(pendingCount = 0, lastUploadAt = clock.nowMs(), lastError = null)
        return UploadResult.Uploaded(sent)
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
