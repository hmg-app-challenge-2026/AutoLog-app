package kr.autolog.relay

import kotlinx.serialization.builtins.ListSerializer
import kr.autolog.relay.support.TEST_OPS_TOKEN
import kr.autolog.relay.support.adminSession
import kr.autolog.shared.AutoLogJson
import kr.autolog.shared.DeviceRegisterResponse
import kr.autolog.shared.InboxItem
import kr.autolog.shared.InboxKind
import kr.autolog.shared.PurposesPayload
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

/** 회사 목적·출발→도착 규칙: 관리자 웹 → 차 inbox(PURPOSES). Docker 없으면 건너뜀. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["autolog.ops.token=$TEST_OPS_TOKEN"],
)
@Testcontainers(disabledWithoutDocker = true)
class PurposeIntegrationTest {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired lateinit var http: TestRestTemplate

    private fun h(admin: String? = null, bearer: String? = null) = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        admin?.let { set("X-Admin-Token", it) }
        bearer?.let { setBearerAuth(it) }
    }

    private fun call(method: HttpMethod, path: String, headers: HttpHeaders, body: String? = null) =
        http.exchange(path, method, HttpEntity(body, headers), String::class.java)

    private fun id(json: String, key: String) = Regex("\"$key\":\"?([^\",}]+)").find(json)!!.groupValues[1]

    private fun place(admin: String, label: String, kind: String) = id(
        call(
            HttpMethod.POST,
            "/admin/places",
            h(admin),
            """{"label":"$label","kind":"$kind","lat":37.5,"lng":127.0}""",
        ).body!!,
        "placeId",
    )

    private fun registerCar(admin: String): String {
        val vehicleId =
            id(call(HttpMethod.POST, "/admin/vehicles", h(admin), """{"plateNo":"55마5555"}""").body!!, "vehicleId")
        val code = id(call(HttpMethod.POST, "/admin/vehicles/$vehicleId/connect-code", h(admin)).body!!, "code")
        val reg = call(HttpMethod.POST, "/v1/devices", h(), """{"code":"$code"}""")
        return AutoLogJson.decodeFromString(DeviceRegisterResponse.serializer(), reg.body!!).deviceToken
    }

    private fun purposesLetter(device: String): PurposesPayload? {
        val body = call(HttpMethod.GET, "/v1/inbox", h(bearer = device)).body!!
        return AutoLogJson.decodeFromString(ListSerializer(InboxItem.serializer()), body)
            .lastOrNull { it.kind == InboxKind.PURPOSES }?.payload as? PurposesPayload
    }

    @Test
    fun `회사 목적과 출발→도착 규칙을 정하면 차가 받고 목적을 지우면 그 규칙도 빠진다`() {
        val admin = http.adminSession("목적 회사", "purpose-admin@test.example")
        val office = place(admin, "본사", "OFFICE")
        val depot = place(admin, "물류센터", "CLIENT")
        val car = registerCar(admin)

        val added = call(HttpMethod.POST, "/admin/purposes", h(admin), """{"label":"납품","category":"BUSINESS"}""")
        assertEquals(HttpStatus.CREATED, added.statusCode)
        val purposeId = id(added.body!!, "id")
        assertEquals(
            HttpStatus.CONFLICT,
            call(HttpMethod.POST, "/admin/purposes", h(admin), """{"label":"납품","category":"BUSINESS"}""").statusCode,
        )
        val rule = """{"originPlaceId":"$office","destPlaceId":"$depot","purpose":"custom:BUSINESS:납품"}"""
        assertEquals(HttpStatus.CREATED, call(HttpMethod.POST, "/admin/purpose-rules", h(admin), rule).statusCode)
        assertEquals(HttpStatus.CONFLICT, call(HttpMethod.POST, "/admin/purpose-rules", h(admin), rule).statusCode)
        // 없는 목적은 거절
        val bad = """{"destPlaceId":"$office","purpose":"custom:BUSINESS:없음"}"""
        assertEquals(HttpStatus.BAD_REQUEST, call(HttpMethod.POST, "/admin/purpose-rules", h(admin), bad).statusCode)

        val letter = purposesLetter(car)!!
        assertEquals("납품", letter.purposes.single().label)
        assertEquals(office, letter.rules.single().originPlaceId)
        assertEquals("custom:BUSINESS:납품", letter.rules.single().purpose)
        assertTrue(call(HttpMethod.GET, "/admin/purposes", h(admin)).body!!.contains("거래처 방문"))

        assertEquals(HttpStatus.NO_CONTENT, call(HttpMethod.DELETE, "/admin/purposes/$purposeId", h(admin)).statusCode)
        val after = purposesLetter(car)!!
        assertTrue(after.purposes.isEmpty() && after.rules.isEmpty())
    }
}
