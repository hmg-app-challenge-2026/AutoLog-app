package kr.autolog.vehicle.ui.receipt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.common.tickerFlow
import kr.autolog.vehicle.core.QrResult
import kr.autolog.vehicle.data.repository.ReceiptRepository
import kr.autolog.vehicle.nav.Navigator
import kr.autolog.vehicle.nav.Route

/** 가짜 서버(데모)의 무기한 QR은 남은 시간을 보여 줄 수 없어 이 길이로 그린다. */
internal const val DEMO_QR_LIFETIME_MS = 10 * 60_000L

/**
 * 영수증 QR(스펙 7.4). 기존 inbox 항목은 이번 QR의 응답이 아니므로 화면을 열 때 기억만 한다.
 * QR을 발급하고 기다리다가 새 영수증이 도착하면 입력 창으로 바꾼다(같은 경로 하나, 맨 위 화면만 교체).
 */
class ReceiptQrViewModel(
    private val receipts: ReceiptRepository,
    private val navigator: Navigator,
    private val clock: AppClock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReceiptQrUiState(now = clock.nowMs()))
    val uiState: StateFlow<ReceiptQrUiState> = _uiState.asStateFlow()

    /** QR을 한 번 발급했는지 — inbox가 다시 비어도 새로 발급하지 않는다. */
    private var issued = false

    /** 이 화면을 떠났는지 — 떠난 뒤의 inbox 변화에는 반응하지 않는다. */
    private var left = false

    init {
        viewModelScope.launch {
            var knownReceiptIds: Set<String>? = null
            receipts.inbox.collect { inbox ->
                if (left) return@collect
                val currentIds = inbox.map { it.receiptId }.toSet()
                val latest = knownReceiptIds?.let { known ->
                    inbox.filterNot { it.receiptId in known }.maxByOrNull { it.takenAt.orEmpty() }
                }
                knownReceiptIds = currentIds
                when {
                    latest != null -> left = navigator.replaceTop(Route.FuelEntry(latest.receiptId))
                    !issued -> {
                        issued = true
                        issue()
                    }
                }
            }
        }
        viewModelScope.launch {
            // 남은 시간은 이 화면에 있는 동안만 센다 — 떠나면(입력 창으로 바뀜·직접 입력·닫기) 1초 타이머를 멈춘다
            tickerFlow(clock).takeWhile { !left }.collect { now -> _uiState.update { it.copy(now = now) } }
        }
    }

    fun onAction(action: ReceiptQrAction) {
        when (action) {
            ReceiptQrAction.Retry -> viewModelScope.launch { issue() }
            ReceiptQrAction.ManualEntry -> left = navigator.push(Route.FuelEntry())
            ReceiptQrAction.Back -> left = navigator.pop()
        }
    }

    private suspend fun issue() {
        _uiState.update { it.copy(loading = true, failed = false, url = null) }
        when (val result = receipts.issueQr()) {
            is QrResult.Issued -> {
                val now = clock.nowMs()
                val expiresAt = if (result.expiresAt == Long.MAX_VALUE) now + DEMO_QR_LIFETIME_MS else result.expiresAt
                _uiState.update { it.copy(loading = false, url = result.url, expiresAt = expiresAt, now = now) }
            }
            is QrResult.Failed -> _uiState.update { it.copy(loading = false, failed = true) }
        }
    }
}
