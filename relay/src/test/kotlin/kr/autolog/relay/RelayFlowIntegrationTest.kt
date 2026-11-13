package kr.autolog.relay

import kotlinx.serialization.builtins.ListSerializer
import kr.autolog.relay.inbox.InboxService
import kr.autolog.shared.AutoLogJson
import kr.autolog.shared.DeviceRegisterResponse
import kr.autolog.shared.InboxItem
import kr.autolog.shared.InboxKind
import kr.autolog.shared.QrTokenResponse
import kr.autolog.shared.UserDto
import kr.autolog.shared.UsersPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * 실제 PostgreSQL(Testcontainers)로 HTTP 흐름 전체를 확인한다.
 * Docker가 없으면 건너뛴다(로컬). CI(ubuntu)에서는 항상 돈다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["autolog.admin.token=test-admin", "autolog.public-base-url=https://relay.test"],
)
@Testcontainers(disabledWithoutDocker = true)
class RelayFlowIntegrationTest {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired lateinit var http: TestRestTemplate

    @Autowired lateinit var inbox: InboxService

    private fun json(body: String, headers: Map<String, String> = emptyMap()) = HttpEntity(
        body,
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            headers.forEach { (k, v) -> set(k, v) }
        },
    )

    private fun auth(token: String) = HttpEntity<Void>(HttpHeaders().apply { setBearerAuth(token) })

    @Test
    fun `관리자 차량 등록부터 기기 등록, inbox 수신·ack, QR 발급까지`() {
        val admin = mapOf("X-Admin-Token" to "test-admin")

        // 관리자: 차량 등록 → 연결 코드
        val vehicle = http.postForEntity(
            "/admin/vehicles",
            json("""{"plateNo":"12가3456","model":"아이오닉5"}""", admin),
            Map::class.java,
        )
        assertEquals(HttpStatus.OK, vehicle.statusCode)
        val vehicleId = vehicle.body!!["vehicleId"] as String
        val code = http.postForEntity(
            "/admin/vehicles/$vehicleId/connect-code",
            json("", admin),
            Map::class.java,
        ).body!!["code"] as String

        // 차: 연결 코드 → 기기 토큰 (두 번째는 거부)
        val reg = http.postForEntity("/v1/devices", json("""{"code":"$code"}"""), String::class.java)
        assertEquals(HttpStatus.OK, reg.statusCode)
        val device = AutoLogJson.decodeFromString(DeviceRegisterResponse.serializer(), reg.body!!)
        assertEquals(vehicleId, device.vehicleId)
        assertEquals("12가3456", device.plateNo)
        assertEquals("아이오닉5", device.model)
        assertEquals(
            HttpStatus.BAD_REQUEST,
            http.postForEntity("/v1/devices", json("""{"code":"$code"}"""), String::class.java).statusCode,
        )

        // 서버 → 차 편지 → 차가 조회 → ack → 비어 있음
        inbox.deliver(UUID.fromString(vehicleId), UsersPayload(listOf(UserDto("u1", "김운전", "영업", null))))
        val list = http.exchange("/v1/inbox", HttpMethod.GET, auth(device.deviceToken), String::class.java)
        val items = AutoLogJson.decodeFromString(ListSerializer(InboxItem.serializer()), list.body!!)
        // 등록 직후 기준 정보 4종이 오고, USERS는 최신 1통으로 바뀐다(F-43)
        assertEquals(4, items.size)
        assertEquals("김운전", (items.single { it.kind == InboxKind.USERS }.payload as UsersPayload).users.single().name)
        items.forEach { item ->
            val ack = http.exchange(
                "/v1/inbox/${item.id}/ack",
                HttpMethod.POST,
                auth(device.deviceToken),
                Void::class.java,
            )
            assertEquals(HttpStatus.NO_CONTENT, ack.statusCode)
        }
        val again = http.exchange("/v1/inbox", HttpMethod.GET, auth(device.deviceToken), String::class.java)
        assertEquals("[]", again.body)

        // QR 토큰
        val qr = http.exchange("/v1/qr-tokens", HttpMethod.POST, auth(device.deviceToken), String::class.java)
        val qrRes = AutoLogJson.decodeFromString(QrTokenResponse.serializer(), qr.body!!)
        assertTrue(qrRes.url.startsWith("https://relay.test/r/"))

        // 주소 변환: 토큰 없으면 401, 카카오 키가 없는 서버면 503 GEOCODER_DISABLED (차는 내비 주소로 대신한다)
        val geoUrl = "/v1/geo/reverse?lat=37.5665&lng=126.978"
        assertEquals(HttpStatus.UNAUTHORIZED, http.getForEntity(geoUrl, String::class.java).statusCode)
        val geo = http.exchange(geoUrl, HttpMethod.GET, auth(device.deviceToken), String::class.java)
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, geo.statusCode)
        assertTrue(geo.body!!.contains("GEOCODER_DISABLED"))

        // 토큰 회수 후 401
        http.exchange("/admin/vehicles/$vehicleId/device", HttpMethod.DELETE, json("", admin), Void::class.java)
        assertEquals(
            HttpStatus.UNAUTHORIZED,
            http.exchange("/v1/inbox", HttpMethod.GET, auth(device.deviceToken), String::class.java).statusCode,
        )
    }

    @Test
    fun `관리자 토큰이 틀리면 403`() {
        val res = http.postForEntity(
            "/admin/vehicles",
            json("""{"plateNo":"x"}""", mapOf("X-Admin-Token" to "nope")),
            String::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, res.statusCode)
    }
}
