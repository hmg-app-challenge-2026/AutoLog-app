package kr.autolog.relay.receipt

import kr.autolog.relay.common.ApiException
import kr.autolog.relay.common.RelayProperties
import kr.autolog.relay.device.DeviceVehicle
import kr.autolog.relay.support.TestClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import java.util.UUID

class QrTokenServiceTest {
    private val clock = TestClock()
    private val repo = FakeQrTokenRepository()
    private val service = QrTokenService(repo, RelayProperties(publicBaseUrl = "https://relay.example/"), clock)
    private val car = DeviceVehicle(UUID.randomUUID(), UUID.randomUUID(), "abcd2345")

    private fun issueToken(): String = service.issue(car).url.substringAfter("?t=")

    @Test
    fun `QR 주소는 차량 코드와 1회 토큰을 담는다`() {
        val res = service.issue(car)

        assertTrue(res.url.startsWith("https://relay.example/r/abcd2345?t="))
    }

    @Test
    fun `토큰은 한 번 쓰면 끝이다`() {
        val token = issueToken()

        assertEquals(car.vehicleId, service.consume("abcd2345", token))
        assertEquals("QR_TOKEN_INVALID", assertThrows<ApiException> { service.consume("abcd2345", token) }.code)
    }

    @Test
    fun `10분이 지나면 쓸 수 없다`() {
        val token = issueToken()
        clock.advance(Duration.ofMinutes(10))

        assertFalse(service.isValid("abcd2345", token))
        assertThrows<ApiException> { service.consume("abcd2345", token) }
    }

    @Test
    fun `다른 차량 코드로는 쓸 수 없다`() {
        val token = issueToken()

        assertThrows<ApiException> { service.consume("zzzz9999", token) }
    }
}

private class FakeQrTokenRepository : QrTokenRepository {
    private data class Row(val vehicleId: UUID, val expiresAt: Instant, var usedAt: Instant? = null)

    private val rows = mutableMapOf<String, Row>()

    override fun insert(tokenHash: String, vehicleId: UUID, expiresAt: Instant) {
        rows[tokenHash] = Row(vehicleId, expiresAt)
    }

    override fun consume(vehicleCode: String, tokenHash: String, now: Instant): UUID? {
        if (!isValid(vehicleCode, tokenHash, now)) return null
        return rows.getValue(tokenHash).also { it.usedAt = now }.vehicleId
    }

    override fun isValid(vehicleCode: String, tokenHash: String, now: Instant): Boolean {
        val r = rows[tokenHash] ?: return false
        return vehicleCode == "abcd2345" && r.usedAt == null && r.expiresAt.isAfter(now)
    }

    override fun vehicleOf(vehicleCode: String, tokenHash: String): UUID? =
        rows[tokenHash]?.vehicleId?.takeIf { vehicleCode == "abcd2345" }
}
