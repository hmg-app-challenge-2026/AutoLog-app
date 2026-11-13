package kr.autolog.relay.support

import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

/** 통합 테스트용 운영자 토큰. 테스트 클래스의 `autolog.ops.token`과 같아야 한다. */
const val TEST_OPS_TOKEN = "test-ops"

const val TEST_PASSWORD = "test-password-1"

/**
 * 운영자 API로 회사·관리자를 만들고 로그인해서 관리자 세션 토큰(`X-Admin-Token`)을 돌려준다.
 * 회사 이름·이메일은 테스트마다 달라야 한다(유일 제약).
 */
fun TestRestTemplate.adminSession(orgName: String, email: String): String {
    // JUnit은 테스트마다 클래스를 새로 만들지만 Spring 컨텍스트·DB는 공유한다 — 이미 있으면 로그인만
    tryLogin(email, TEST_PASSWORD)?.let { return it }
    val ops = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        set("X-Ops-Token", TEST_OPS_TOKEN)
    }
    val org = postForEntity("/ops/orgs", HttpEntity("""{"name":"$orgName"}""", ops), Map::class.java)
    val orgId = checkNotNull(org.body?.get("orgId")) { "회사 만들기 실패: ${org.statusCode}" }
    val admin = postForEntity(
        "/ops/orgs/$orgId/admins",
        HttpEntity("""{"email":"$email","password":"$TEST_PASSWORD"}""", ops),
        Map::class.java,
    )
    check(admin.statusCode.is2xxSuccessful) { "관리자 만들기 실패: ${admin.statusCode}" }
    return login(email, TEST_PASSWORD)
}

fun TestRestTemplate.login(email: String, password: String): String =
    checkNotNull(tryLogin(email, password)) { "로그인 실패: $email" }

fun TestRestTemplate.tryLogin(email: String, password: String): String? {
    val res = postForEntity(
        "/admin/auth/login",
        HttpEntity(
            """{"email":"$email","password":"$password"}""",
            HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON },
        ),
        Map::class.java,
    )
    return if (res.statusCode.is2xxSuccessful) res.body?.get("token") as? String else null
}
