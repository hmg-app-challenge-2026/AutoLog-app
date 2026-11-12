package kr.autolog.vehicle

import kr.autolog.vehicle.fake.FakeContext
import org.junit.Assert.assertEquals
import org.junit.Test

class ContractsVisibleTest {
    @Test
    fun `backend vehicle-core의 계약과 Fake를 앱에서 쓸 수 있다`() {
        val context = FakeContext()

        val label = context.destination.value?.label

        assertEquals("판교 테크노밸리 거래처", label)
    }
}
