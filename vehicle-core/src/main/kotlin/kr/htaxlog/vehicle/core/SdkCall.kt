package kr.htaxlog.vehicle.core

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Pleos SDK의 onComplete/onFailed 콜백 API를 suspend 함수로 바꾼다. (기술명세 2.3)
 * 모든 Gateway(앱 A·B)가 공용으로 쓴다.
 */
suspend fun <T> sdkCall(block: (onComplete: (T) -> Unit, onFailed: (Exception) -> Unit) -> Unit): T =
    suspendCancellableCoroutine { cont ->
        block(
            { value -> if (cont.isActive) cont.resume(value) },
            { e -> if (cont.isActive) cont.resumeWithException(e) },
        )
    }

/** 리스너형 SDK API를 Flow로 바꾼다. collect가 끝나면 unregister 된다. */
fun <L, T> sdkListenerFlow(
    register: (L) -> Unit,
    unregister: (L) -> Unit,
    makeListener: (emit: (T) -> Unit, fail: (Exception) -> Unit) -> L,
): Flow<T> = callbackFlow {
    val listener = makeListener({ trySend(it) }, { close(it) })
    register(listener)
    awaitClose { unregister(listener) }
}
