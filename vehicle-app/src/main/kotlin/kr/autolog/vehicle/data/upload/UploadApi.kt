package kr.autolog.vehicle.data.upload

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// 사내 서버 자동 취합(기능명세 ②, 스펙 8장)의 차 → 서버 운행 사본 전송. **백엔드에 아직 계약이 없다**(R-4, core `UploadState` 주석:
// "업로드는 2차 합의 전이라 지금은 항상 null"). 그래서 프론트가 쓰는 모양만 여기 두고, 실제 앱은 [UnavailableUploadApi]를 쓴다.
// 계약이 core에 생기면 이 파일을 지우고 그 계약으로 바꾼다(docs/integration/backend-link.md G-04).

data class UploadStatus(val enabled: Boolean, val pendingCount: Int, val lastUploadAt: Long?, val lastError: String?)

sealed interface UploadResult {
    data class Uploaded(val count: Int) : UploadResult

    data class Failed(val reason: String) : UploadResult
}

/** 사내 서버 자동 취합용 운행 사본 전송 (기능명세 ②, 스펙 8장, R-4). */
interface UploadApi {
    val status: StateFlow<UploadStatus>

    suspend fun uploadNow(): UploadResult
}

/** 실제 앱(vehicle flavor): 업로드 계약이 없어 꺼 둔다 — 대기 0건, [uploadNow]는 이유와 함께 실패. 가짜 숫자를 보여 주지 않는다. */
class UnavailableUploadApi : UploadApi {
    override val status: StateFlow<UploadStatus> =
        MutableStateFlow(
            UploadStatus(enabled = false, pendingCount = 0, lastUploadAt = null, lastError = null),
        ).asStateFlow()

    override suspend fun uploadNow(): UploadResult = UploadResult.Failed(REASON)

    companion object {
        /** 화면에 그대로 보일 수 있는 이유 문장(Repository 결과 문구 규칙과 같다 — 원천이 문장을 준다). */
        const val REASON = "사내 서버 전송은 아직 준비 중이에요"
    }
}
