package kr.htaxlog.relay.device

import kr.htaxlog.relay.common.ApiException
import kr.htaxlog.relay.common.Tokens
import kr.htaxlog.relay.support.TestClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import java.util.UUID

class DeviceServiceTest {
    private val clock = TestClock()
    private val vehicleId = UUID.randomUUID()
    private val orgId = UUID.randomUUID()
    private val repo = FakeDeviceRepository(vehicleId, orgId)
    private val service = DeviceService(repo, clock)

    @Test
    fun `유효한 연결 코드로 등록하면 토큰을 받고 DB에는 해시만 남는다`() {
        repo.codes["ABC234"] = ConnectCode("ABC234", vehicleId, clock.instant().plus(Duration.ofHours(24)), null)

        val res = service.register("abc234")

        assertEquals(vehicleId.toString(), res.vehicleId)
        assertEquals("12가3456", res.plateNo)
        assertEquals(Tokens.sha256Hex(res.deviceToken), repo.tokenHash)
        assertNotEquals(res.deviceToken, repo.tokenHash)
    }

    @Test
    fun `같은 코드로 두 번 등록하면 두 번째는 거부된다`() {
        repo.codes["ABC234"] = ConnectCode("ABC234", vehicleId, clock.instant().plus(Duration.ofHours(24)), null)
        service.register("ABC234")

        val e = assertThrows<ApiException> { service.register("ABC234") }

        assertEquals("CONNECT_CODE_INVALID", e.code)
    }

    @Test
    fun `만료된 코드는 거부된다`() {
        repo.codes["ABC234"] = ConnectCode("ABC234", vehicleId, clock.instant().plus(Duration.ofHours(24)), null)
        clock.advance(Duration.ofHours(24))

        assertThrows<ApiException> { service.register("ABC234") }
    }

    @Test
    fun `등록한 토큰으로 인증하면 내 차가 나온다`() {
        repo.codes["ABC234"] = ConnectCode("ABC234", vehicleId, clock.instant().plus(Duration.ofHours(1)), null)
        val token = service.register("ABC234").deviceToken

        assertEquals(vehicleId, service.authenticate("Bearer $token").vehicleId)
    }

    @Test
    fun `토큰이 없거나 틀리면 401`() {
        assertEquals("DEVICE_UNAUTHORIZED", assertThrows<ApiException> { service.authenticate(null) }.code)
        assertEquals("DEVICE_UNAUTHORIZED", assertThrows<ApiException> { service.authenticate("Bearer wrong") }.code)
    }
}

private class FakeDeviceRepository(private val vehicleId: UUID, private val orgId: UUID) : DeviceRepository {
    val codes = mutableMapOf<String, ConnectCode>()
    var tokenHash: String? = null

    override fun findConnectCode(code: String) = codes[code]

    override fun markCodeUsed(code: String, at: Instant): Boolean {
        val c = codes[code] ?: return false
        if (c.usedAt != null) return false
        codes[code] = c.copy(usedAt = at)
        return true
    }

    override fun saveDeviceToken(vehicleId: UUID, tokenHash: String, at: Instant): DeviceVehicle {
        this.tokenHash = tokenHash
        return DeviceVehicle(vehicleId, orgId, "code1234", "12가3456", "아이오닉5")
    }

    override fun findByTokenHash(tokenHash: String) =
        if (tokenHash == this.tokenHash) DeviceVehicle(vehicleId, orgId, "code1234") else null
}
