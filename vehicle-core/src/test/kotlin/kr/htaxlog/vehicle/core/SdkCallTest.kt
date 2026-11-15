package kr.htaxlog.vehicle.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SdkCallTest {
    @Test
    fun `onComplete 콜백 값이 suspend 결과로 돌아온다`() {
        // Given
        val fakeSdkGet: ((Double) -> Unit, (Exception) -> Unit) -> Unit = { onComplete, _ -> onComplete(18420.5) }

        // When
        val km = runBlocking { sdkCall(fakeSdkGet) }

        // Then
        assertEquals(18420.5, km, 0.0)
    }

    @Test(expected = IllegalStateException::class)
    fun `onFailed 콜백은 예외로 던져진다`() {
        runBlocking {
            sdkCall<Double> { _, onFailed -> onFailed(IllegalStateException("Vehicle Service 미연결")) }
        }
    }
}
