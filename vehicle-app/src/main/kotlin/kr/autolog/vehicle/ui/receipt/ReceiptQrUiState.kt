package kr.autolog.vehicle.ui.receipt

import kr.autolog.vehicle.ui.design.remainingSeconds

/** 영수증 QR 화면 상태(스펙 7.4). [now]는 1초마다 바뀐다. */
data class ReceiptQrUiState(
    val loading: Boolean = true,
    /** QR에 담을 업로드 주소. 화면 글자로는 보여 주지 않는다. */
    val url: String? = null,
    val expiresAt: Long = 0,
    val now: Long = 0,
    /** 발급 실패(네트워크·서버). */
    val failed: Boolean = false,
) {
    val expired: Boolean get() = url != null && now >= expiresAt

    val remainingSec: Int get() = remainingSeconds(expiresAt, now)
}

sealed interface ReceiptQrAction {
    /** 만료·실패 후 새 QR 발급. */
    data object Retry : ReceiptQrAction

    /** [직접 입력하기] — 빈 입력 창으로. */
    data object ManualEntry : ReceiptQrAction

    data object Back : ReceiptQrAction
}
