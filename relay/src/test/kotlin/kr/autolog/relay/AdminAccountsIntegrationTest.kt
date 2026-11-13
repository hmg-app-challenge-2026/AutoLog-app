package kr.autolog.relay

import kr.autolog.relay.support.TEST_OPS_TOKEN
import kr.autolog.relay.support.TEST_PASSWORD
import kr.autolog.relay.support.adminSession
import kr.autolog.relay.support.login
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

/** 회사별 관리자 계정: 운영자가 회사·관리자를 만들고, 관리자는 로그인해서 자기 회사 데이터만 본다. Docker 없으면 건너뜀. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["autolog.ops.token=$TEST_OPS_TOKEN"],
)
@Testcontainers(disabledWithoutDocker = true)
class AdminAccountsIntegrationTest {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired lateinit var http: TestRestTemplate

    private fun headers(vararg pairs: Pair<String, String>) = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        pairs.forEach { (k, v) -> set(k, v) }
    }

    private fun admin(token: String) = headers("X-Admin-Token" to token)

    private fun post(path: String, body: String, h: HttpHeaders) =
        http.postForEntity(path, HttpEntity(body, h), String::class.java)

    private fun get(path: String, h: HttpHeaders) =
        http.exchange(path, HttpMethod.GET, HttpEntity<Void>(h), String::class.java)

    @Test
    fun `두 회사 관리자는 서로의 차량을 보거나 연결 코드를 만들 수 없다`() {
        val a = http.adminSession("가 회사", "admin@a.example")
        val b = http.adminSession("나 회사", "admin@b.example")

        val carA = Regex("\"vehicleId\":\"([^\"]+)\"")
            .find(post("/admin/vehicles", """{"plateNo":"11가1111"}""", admin(a)).body!!)!!.groupValues[1]
        post("/admin/vehicles", """{"plateNo":"22나2222"}""", admin(b))

        val listA = get("/admin/vehicles", admin(a)).body!!
        val listB = get("/admin/vehicles", admin(b)).body!!
        assertTrue(listA.contains("11가1111") && !listA.contains("22나2222"))
        assertTrue(listB.contains("22나2222") && !listB.contains("11가1111"))

        // 나 회사 관리자가 가 회사 차량에 연결 코드 → 없는 차량
        assertEquals(HttpStatus.NOT_FOUND, post("/admin/vehicles/$carA/connect-code", "", admin(b)).statusCode)
        assertEquals(HttpStatus.OK, post("/admin/vehicles/$carA/connect-code", "", admin(a)).statusCode)

        // 내 정보
        val me = get("/admin/auth/me", admin(a)).body!!
        assertTrue(me.contains("admin@a.example") && me.contains("가 회사"))
    }

    @Test
    fun `로그인 실패·로그아웃·세션 없는 요청`() {
        http.adminSession("다 회사", "admin@c.example")
        val wrong = post("/admin/auth/login", """{"email":"admin@c.example","password":"틀린비밀번호"}""", headers())
        assertEquals(HttpStatus.UNAUTHORIZED, wrong.statusCode)
        assertTrue(wrong.body!!.contains("LOGIN_FAILED"))
        // 없는 이메일도 같은 답
        assertEquals(
            HttpStatus.UNAUTHORIZED,
            post("/admin/auth/login", """{"email":"nobody@c.example","password":"whatever1"}""", headers()).statusCode,
        )

        // 대소문자·공백은 무시하고 로그인
        val token = http.login("  Admin@C.example ", TEST_PASSWORD)
        assertEquals(HttpStatus.OK, get("/admin/vehicles", admin(token)).statusCode)

        post("/admin/auth/logout", "", admin(token))
        assertEquals(HttpStatus.FORBIDDEN, get("/admin/vehicles", admin(token)).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, get("/admin/vehicles", headers()).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, get("/admin/vehicles", admin("made-up")).statusCode)
    }

    @Test
    fun `5번 틀리면 잠시 잠긴다`() {
        http.adminSession("라 회사", "admin@d.example")
        repeat(5) { post("/admin/auth/login", """{"email":"admin@d.example","password":"wrong-pass"}""", headers()) }

        val locked = post("/admin/auth/login", """{"email":"admin@d.example","password":"$TEST_PASSWORD"}""", headers())
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, locked.statusCode)
    }

    @Test
    fun `비밀번호를 바꾸면 다른 세션은 끊기고 새 비밀번호로만 들어간다`() {
        val first = http.adminSession("마 회사", "admin@e.example")
        val second = http.login("admin@e.example", TEST_PASSWORD)

        val weak =
            post("/admin/auth/password", """{"currentPassword":"$TEST_PASSWORD","newPassword":"short"}""", admin(first))
        assertEquals(HttpStatus.BAD_REQUEST, weak.statusCode)
        val changed = post(
            "/admin/auth/password",
            """{"currentPassword":"$TEST_PASSWORD","newPassword":"new-password-2"}""",
            admin(first),
        )
        assertEquals(HttpStatus.NO_CONTENT, changed.statusCode)

        assertEquals(HttpStatus.OK, get("/admin/vehicles", admin(first)).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, get("/admin/vehicles", admin(second)).statusCode)
        assertEquals(
            HttpStatus.OK,
            post(
                "/admin/auth/login",
                """{"email":"admin@e.example","password":"new-password-2"}""",
                headers(),
            ).statusCode,
        )
    }

    @Test
    fun `운영자 API는 운영자 토큰이 있어야 하고 이름·이메일 중복을 막는다`() {
        val ops = headers("X-Ops-Token" to TEST_OPS_TOKEN)
        assertEquals(HttpStatus.FORBIDDEN, get("/ops/orgs", headers()).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, get("/ops/orgs", headers("X-Ops-Token" to "nope")).statusCode)
        // 회사 관리자 세션으로는 /ops를 못 쓴다
        val adminToken = http.adminSession("바 회사", "admin@f.example")
        assertEquals(HttpStatus.FORBIDDEN, get("/ops/orgs", headers("X-Ops-Token" to adminToken)).statusCode)

        assertEquals(HttpStatus.CONFLICT, post("/ops/orgs", """{"name":"바 회사"}""", ops).statusCode)
        val orgId = Regex(
            "\"orgId\":\"([^\"]+)\"",
        ).find(post("/ops/orgs", """{"name":"사 회사"}""", ops).body!!)!!.groupValues[1]
        assertEquals(
            HttpStatus.CONFLICT,
            post(
                "/ops/orgs/$orgId/admins",
                """{"email":"ADMIN@f.example","password":"password-123"}""",
                ops,
            ).statusCode,
        )
        assertEquals(
            HttpStatus.BAD_REQUEST,
            post("/ops/orgs/$orgId/admins", """{"email":"not-an-email","password":"password-123"}""", ops).statusCode,
        )
        assertEquals(
            HttpStatus.BAD_REQUEST,
            post("/ops/orgs/$orgId/admins", """{"email":"new@g.example","password":"short"}""", ops).statusCode,
        )
        assertTrue(get("/ops/orgs", ops).body!!.contains("사 회사"))
    }

    @Test
    fun `회사가 직접 가입하면 바로 로그인되고 자기 회사만 본다`() {
        val res = post(
            "/admin/auth/signup",
            """{"orgName":"  아자 회사 ","email":"Owner@Aja.example","password":"signup-pass-1"}""",
            headers(),
        )
        assertEquals(HttpStatus.OK, res.statusCode)
        assertTrue(res.body!!.contains("\"orgName\":\"아자 회사\"") && res.body!!.contains("owner@aja.example"))
        val token = Regex("\"token\":\"([^\"]+)\"").find(res.body!!)!!.groupValues[1]

        assertEquals("[]", get("/admin/vehicles", admin(token)).body)
        assertEquals(HttpStatus.OK, post("/admin/vehicles", """{"plateNo":"77아7777"}""", admin(token)).statusCode)
        // 가입한 비밀번호로 다시 로그인
        assertTrue(http.login("owner@aja.example", "signup-pass-1").isNotEmpty())
    }

    @Test
    fun `가입 — 같은 회사 이름·이메일과 잘못된 입력은 막는다`() {
        post(
            "/admin/auth/signup",
            """{"orgName":"차카 회사","email":"a@chaka.example","password":"signup-pass-1"}""",
            headers(),
        )

        val sameOrg =
            post(
                "/admin/auth/signup",
                """{"orgName":"차카 회사","email":"b@chaka.example","password":"signup-pass-1"}""",
                headers(),
            )
        assertEquals(HttpStatus.CONFLICT, sameOrg.statusCode)
        assertTrue(sameOrg.body!!.contains("ORG_EXISTS"))
        val sameEmail =
            post(
                "/admin/auth/signup",
                """{"orgName":"타파 회사","email":"A@chaka.example","password":"signup-pass-1"}""",
                headers(),
            )
        assertEquals(HttpStatus.CONFLICT, sameEmail.statusCode)
        assertTrue(sameEmail.body!!.contains("ADMIN_EXISTS"))

        assertEquals(
            HttpStatus.BAD_REQUEST,
            post(
                "/admin/auth/signup",
                """{"orgName":" ","email":"c@chaka.example","password":"signup-pass-1"}""",
                headers(),
            ).statusCode,
        )
        assertEquals(
            HttpStatus.BAD_REQUEST,
            post(
                "/admin/auth/signup",
                """{"orgName":"하 회사","email":"not-email","password":"signup-pass-1"}""",
                headers(),
            ).statusCode,
        )
        assertEquals(
            HttpStatus.BAD_REQUEST,
            post(
                "/admin/auth/signup",
                """{"orgName":"하 회사","email":"d@ha.example","password":"short"}""",
                headers(),
            ).statusCode,
        )
        // 실패한 가입은 회사를 남기지 않는다
        assertTrue(!get("/ops/orgs", headers("X-Ops-Token" to TEST_OPS_TOKEN)).body!!.contains("하 회사"))
    }
}
