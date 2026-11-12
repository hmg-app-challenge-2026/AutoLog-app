package kr.autolog.vehicle.common

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowExtTest {
    @Test
    fun `1초 안에 여러 번 바뀌면 첫 값과 마지막 값만 내보낸다`() = runTest {
        val source =
            flow {
                emit(1)
                delay(100)
                emit(2)
                delay(100)
                emit(3)
            }

        source.throttleLatest(1_000).test {
            assertEquals(1, awaitItem())
            assertEquals(3, awaitItem())
            awaitComplete()
        }
    }
}
