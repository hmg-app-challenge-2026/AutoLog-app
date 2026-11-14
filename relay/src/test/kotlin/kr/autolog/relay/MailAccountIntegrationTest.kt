package kr.autolog.relay

import kotlinx.serialization.builtins.ListSerializer
import kr.autolog.relay.mail.SmtpFailure
import kr.autolog.relay.mail.SmtpVerifier
import kr.autolog.relay.support.TEST_OPS_TOKEN
import kr.autolog.relay.support.adminSession
import kr.autolog.shared.AutoLogJson
import kr.autolog.shared.DeviceRegisterResponse
import kr.autolog.shared.InboxItem
import kr.autolog.shared.InboxKind
import kr.autolog.shared.MailAccountPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import org.springframework.jdbc.core.simple.JdbcClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/** 회사 메일 계정: 관리자 웹에서 저장 → 차 inbox(비밀번호는 가져갈 때만) → 지우기. Docker 없으면 건너뜀. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["autolog.ops.token=$TEST_OPS_TOKEN", "autolog.mail.secret-key=test-mail-key"],
)
@Testcontainers(disabledWithoutDocker = true)
class MailAccountIntegrationTest {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    /** 실제 SMTP 대신: 비밀번호가 "right-pass"면 로그인 성공. */
    @TestConfiguration
    class FakeSmtp {
        @Bean
        @Primary
        fun verifier() = SmtpVerifier { _, _, _, password, _ ->
            if (password == "right-pass") null else SmtpFailure.AUTH
        }
    }

    @Autowired lateinit var http: TestRestTemplate

    @Autowired lateinit var jdbc: JdbcClient

    private fun h(admin: String? = null, bearer: String? = null) = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        admin?.let { set("X-Admin-Token", it) }
        bearer?.let { setBearerAuth(it) }
    }

    private fun call(method: HttpMethod, path: String, headers: HttpHeaders, body: String? = null) =
        http.exchange(path, method, HttpEntity(body, headers), String::class.java)

    private fun registerCar(admin: String, plate: String): String {
        val vehicleId = Regex("\"vehicleId\":\"([^\"]+)\"")
            .find(call(HttpMethod.POST, "/admin/vehicles", h(admin), """{"plateNo":"$plate"}""").body!!)!!
            .groupValues[1]
        val code = Regex("\"code\":\"([^\"]+)\"")
            .find(call(HttpMethod.POST, "/admin/vehicles/$vehicleId/connect-code", h(admin)).body!!)!!.groupValues[1]
        val reg = call(HttpMethod.POST, "/v1/devices", h(), """{"code":"$code"}""")
        return AutoLogJson.decodeFromString(DeviceRegisterResponse.serializer(), reg.body!!).deviceToken
    }

    private fun mailLetters(device: String): List<MailAccountPayload> {
        val body = call(HttpMethod.GET, "/v1/inbox", h(bearer = device)).body!!
        return AutoLogJson.decodeFromString(ListSerializer(InboxItem.serializer()), body)
            .filter { it.kind == InboxKind.MAIL_ACCOUNT }
            .map { it.payload as MailAccountPayload }
    }

    @Test
    fun `메일 계정 비밀번호는 차가 가져갈 때만 채워지고 DB에는 평문이 없다`() {
        val admin = http.adminSession("메일 회사", "mail-admin@test.example")
        val car = registerCar(admin, "33다3333")
        val account =
            """{"host":"smtp.example.com","port":587,"username":"autolog@example.com","password":"right-pass"}"""

        // 틀린 비밀번호는 저장하지 않는다
        val wrong = call(HttpMethod.PUT, "/admin/mail-account", h(admin), account.replace("right-pass", "nope"))
        assertEquals(HttpStatus.BAD_REQUEST, wrong.statusCode)
        assertTrue(wrong.body!!.contains("MAIL_AUTH_FAILED"))
        assertEquals(HttpStatus.NO_CONTENT, call(HttpMethod.GET, "/admin/mail-account", h(admin)).statusCode)

        val saved = call(HttpMethod.PUT, "/admin/mail-account", h(admin), account)
        assertEquals(HttpStatus.OK, saved.statusCode)
        assertFalse(saved.body!!.contains("right-pass"))
        assertFalse(call(HttpMethod.GET, "/admin/mail-account", h(admin)).body!!.contains("right-pass"))

        // DB(inbox·계정 표) 어디에도 평문 비밀번호가 없다
        val dump = jdbc.sql("SELECT payload::text FROM inbox").query(String::class.java).list() +
            jdbc.sql("SELECT password_enc FROM org_mail_accounts").query(String::class.java).list()
        assertTrue(dump.isNotEmpty() && dump.none { it.contains("right-pass") })

        // 차는 비밀번호까지 받는다(최신 1통)
        val letter = mailLetters(car).single()
        assertEquals("autolog@example.com", letter.account?.username)
        assertEquals("right-pass", letter.account?.password)

        // 비밀번호 칸을 비우고 다시 저장하면 저장된 비밀번호를 그대로 쓴다
        val renamed = account.replace(",\"password\":\"right-pass\"", "").replace("587", "465")
        assertEquals(HttpStatus.OK, call(HttpMethod.PUT, "/admin/mail-account", h(admin), renamed).statusCode)
        assertEquals(465, mailLetters(car).single().account?.port)

        // 새로 등록한 차도 받는다
        assertEquals("right-pass", mailLetters(registerCar(admin, "44라4444")).single().account?.password)

        // 지우면 차에도 지움 편지
        assertEquals(HttpStatus.NO_CONTENT, call(HttpMethod.DELETE, "/admin/mail-account", h(admin)).statusCode)
        assertNull(mailLetters(car).single().account)
    }

    @Test
    fun `다른 회사의 메일 계정은 보이지 않는다`() {
        val a = http.adminSession("메일 가 회사", "mail-a@test.example")
        val b = http.adminSession("메일 나 회사", "mail-b@test.example")
        call(
            HttpMethod.PUT,
            "/admin/mail-account",
            h(a),
            """{"host":"smtp.example.com","username":"a@example.com","password":"right-pass"}""",
        )

        assertEquals(HttpStatus.NO_CONTENT, call(HttpMethod.GET, "/admin/mail-account", h(b)).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, call(HttpMethod.GET, "/admin/mail-account", h()).statusCode)
    }
}
