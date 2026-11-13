package kr.autolog.relay

import kotlinx.serialization.builtins.ListSerializer
import kr.autolog.relay.admin.GeoPoint
import kr.autolog.relay.admin.Geocoder
import kr.autolog.relay.support.TEST_OPS_TOKEN
import kr.autolog.relay.support.adminSession
import kr.autolog.shared.AutoLogJson
import kr.autolog.shared.DeviceRegisterResponse
import kr.autolog.shared.InboxItem
import kr.autolog.shared.InboxKind
import kr.autolog.shared.PlaceKind
import kr.autolog.shared.PlacesPayload
import kr.autolog.shared.RecipientsPayload
import kr.autolog.shared.SchedulesPayload
import kr.autolog.shared.UsersPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.charset.Charset

/** 운영 웹 기준 정보 API → 차 inbox 배포 (F-43, F-51~54). 실제 PostgreSQL. Docker 없으면 건너뜀. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["autolog.ops.token=$TEST_OPS_TOKEN"],
)
@Testcontainers(disabledWithoutDocker = true)
class AdminReferenceIntegrationTest {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    /** 지오코딩은 가짜: "판교역로 166"만 안다. */
    @TestConfiguration
    class FakeGeo {
        @Bean
        @Primary
        fun geocoder() = Geocoder { if (it.contains("판교역로 166")) GeoPoint(37.402, 127.1108) else null }
    }

    @Autowired lateinit var http: TestRestTemplate

    private val adminToken by lazy { http.adminSession("기준정보 테스트 회사", "ref-admin@test.example") }

    private val adminHeaders by lazy {
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Admin-Token", adminToken)
        }
    }

    private fun post(path: String, body: String): ResponseEntity<String> =
        http.postForEntity(path, HttpEntity(body, adminHeaders), String::class.java)

    private fun csv(path: String, bytes: ByteArray): ResponseEntity<String> = http.postForEntity(
        path,
        HttpEntity(
            bytes,
            HttpHeaders().apply {
                contentType = MediaType("text", "csv")
                set("X-Admin-Token", adminToken)
            },
        ),
        String::class.java,
    )

    private fun get(path: String) =
        http.exchange(path, HttpMethod.GET, HttpEntity<Void>(adminHeaders), List::class.java).body!!

    private fun field(json: String, name: String) = Regex("\"$name\":\"([^\"]+)\"").find(json)!!.groupValues[1]

    /** 차량 등록 → 연결 코드 → 기기 등록. (vehicleId, 기기 토큰) */
    private fun newCar(plate: String): Pair<String, String> {
        val vehicleId = field(post("/admin/vehicles", """{"plateNo":"$plate"}""").body!!, "vehicleId")
        val code = field(post("/admin/vehicles/$vehicleId/connect-code", "").body!!, "code")
        val reg =
            http.postForEntity(
                "/v1/devices",
                HttpEntity("""{"code":"$code"}""", HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
                String::class.java,
            )
        return vehicleId to AutoLogJson.decodeFromString(DeviceRegisterResponse.serializer(), reg.body!!).deviceToken
    }

    private fun inbox(token: String): List<InboxItem> = AutoLogJson.decodeFromString(
        ListSerializer(InboxItem.serializer()),
        http.exchange(
            "/v1/inbox",
            HttpMethod.GET,
            HttpEntity<Void>(
                HttpHeaders().apply {
                    setBearerAuth(token)
                },
            ),
            String::class.java,
        ).body!!,
    )

    private inline fun <reified T> List<InboxItem>.latest(kind: InboxKind): T = single { it.kind == kind }.payload as T

    @Test
    fun `직원·장소·배차·수신자를 등록하면 차 inbox에 최신 전체 목록이 간다`() {
        val (vehicleId, token) = newCar("12가1111")

        assertEquals(
            HttpStatus.CREATED,
            post("/admin/users", """{"name":"김영업","dept":"영업1팀","email":"Kim@Example.com"}""").statusCode,
        )
        val userId = field(post("/admin/users", """{"name":"이현장"}""").body!!, "userId")
        val place = post("/admin/places", """{"label":"판교 거래처","kind":"거래처","address":"경기 성남시 판교역로 166"}""")
        assertEquals(HttpStatus.CREATED, place.statusCode)
        val placeId = field(place.body!!, "placeId")
        val schedule =
            post(
                "/admin/schedules",
                """{"vehicleId":"$vehicleId","userId":"$userId","startsAt":"2099-01-05T09:00:00+09:00","endsAt":"2099-01-05T12:00:00+09:00","destPlaceId":"$placeId","purposeDetail":"meeting"}""",
            )
        // 배차는 30일 창 밖(2099년)이라 inbox에는 빈 목록 — 창 안 배차는 아래 CSV에서 확인
        assertEquals(HttpStatus.CREATED, schedule.statusCode)
        assertEquals(
            HttpStatus.CREATED,
            post("/admin/recipients", """{"email":"acc@example.com","name":"회계팀"}""").statusCode,
        )
        assertEquals(HttpStatus.CONFLICT, post("/admin/recipients", """{"email":"ACC@example.com"}""").statusCode)

        val items = inbox(token)
        // 같은 조직을 다른 테스트와 공유하므로 이 테스트가 넣은 것만 확인한다
        val users = items.latest<UsersPayload>(InboxKind.USERS).users
        assertTrue(users.map { it.name }.containsAll(listOf("김영업", "이현장")))
        assertEquals("kim@example.com", users.single { it.name == "김영업" }.email)
        val p = items.latest<PlacesPayload>(InboxKind.PLACES).places.single { it.label == "판교 거래처" }
        assertEquals(PlaceKind.CLIENT, p.kind)
        assertEquals(37.402, p.lat, 1e-9)
        assertTrue(
            items.latest<RecipientsPayload>(InboxKind.RECIPIENTS).recipients.any {
                it.email == "acc@example.com"
            },
        )
        assertEquals(4, items.size, "종류마다 최신 1통만")
    }

    @Test
    fun `자택·좌표 없는 주소·잘못된 시각은 400`() {
        val (vehicleId, _) = newCar("12가2222")

        assertEquals(
            HttpStatus.BAD_REQUEST,
            post("/admin/places", """{"label":"집","kind":"HOME","lat":37.5,"lng":127.0}""").statusCode,
        )
        assertEquals(
            HttpStatus.BAD_REQUEST,
            post("/admin/places", """{"label":"어딘가","kind":"CLIENT","address":"모르는 주소"}""").statusCode,
        )
        val badTime =
            post(
                "/admin/schedules",
                """{"vehicleId":"$vehicleId","startsAt":"2099-01-05 12:00","endsAt":"2099-01-05 09:00"}""",
            )
        assertEquals(HttpStatus.BAD_REQUEST, badTime.statusCode)
        assertTrue(badTime.body!!.contains("종료 시각"))
    }

    @Test
    fun `CSV에 틀린 줄이 있으면 아무것도 저장하지 않고 줄 번호를 알려준다`() {
        val res =
            csv(
                "/admin/users/import",
                "name,email\n박관리,park@example.com\n,no-name@example.com\n최회계,not-email\n".toByteArray(),
            )

        assertEquals(HttpStatus.BAD_REQUEST, res.statusCode)
        assertTrue(res.body!!.contains("IMPORT_INVALID"))
        assertTrue(res.body!!.contains("\"line\":3"))
        assertTrue(res.body!!.contains("\"line\":4"))
        assertTrue(get("/admin/users").none { (it as Map<*, *>)["name"] == "박관리" })
    }

    @Test
    fun `엑셀 CP949 CSV로 장소·배차를 올리면 해당 차에 배차가 간다`() {
        val (_, token) = newCar("34나5678")
        csv("/admin/users/import", "이름,부서,이메일\n정배차,물류팀,jung@example.com\n".toByteArray(Charset.forName("MS949")))
        val places =
            csv(
                "/admin/places/import",
                "이름,종류,주소,lat,lng\n수원 지점,지점,,37.266,127.0\n".toByteArray(Charset.forName("MS949")),
            )
        assertEquals(HttpStatus.OK, places.statusCode, places.body)

        val tomorrow = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(1)
        val res =
            csv(
                "/admin/schedules/import",
                "차량번호,운전자,시작,종료,목적지,목적\n34나 5678,jung@example.com,$tomorrow 09:00,$tomorrow 18:00,수원 지점,BRANCH_MOVE\n"
                    .toByteArray(Charset.forName("MS949")),
            )
        assertEquals(HttpStatus.OK, res.statusCode, res.body)

        val s = inbox(token).latest<SchedulesPayload>(InboxKind.SCHEDULES).schedules.single()
        assertEquals("BRANCH_MOVE", s.purposeDetail)
        assertTrue(s.startsAt.endsWith("+09:00"))
    }

    @Test
    fun `직원을 지우면 그 직원의 배차는 운전자 미정으로 다시 배포된다`() {
        val (vehicleId, token) = newCar("56다7890")
        val userId = field(post("/admin/users", """{"name":"한퇴사"}""").body!!, "userId")
        val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.ofHours(9)).plusHours(2).withNano(0)
        post(
            "/admin/schedules",
            """{"vehicleId":"$vehicleId","userId":"$userId","startsAt":"$now","endsAt":"${now.plusHours(1)}"}""",
        )

        http.exchange("/admin/users/$userId", HttpMethod.DELETE, HttpEntity<Void>(adminHeaders), Void::class.java)

        val items = inbox(token)
        assertNull(items.latest<SchedulesPayload>(InboxKind.SCHEDULES).schedules.single().userId)
        assertTrue(items.latest<UsersPayload>(InboxKind.USERS).users.none { it.name == "한퇴사" })
    }
}
