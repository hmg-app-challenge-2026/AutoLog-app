package kr.autolog.relay.mail

import kr.autolog.relay.common.ApiException
import kr.autolog.relay.common.RelayProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MailSecretTest {
    private fun secret(key: String) = MailSecret(RelayProperties(mail = RelayProperties.Mail(key)))

    @Test
    fun `암호화한 값은 평문이 보이지 않고 같은 키로만 풀린다`() {
        val s = secret("key-one")

        val a = s.encrypt("app-pass-123")
        val b = s.encrypt("app-pass-123")

        assertFalse(a.contains("app-pass-123"))
        assertNotEquals(a, b) // 매번 다른 IV
        assertEquals("app-pass-123", s.decrypt(a))
        assertNull(secret("key-two").decrypt(a))
    }

    @Test
    fun `키가 없으면 저장을 거절하고 풀지도 않는다`() {
        val s = secret("")

        assertThrows<ApiException> { s.encrypt("x") }
        assertThrows<ApiException> { s.requireKey() }
        assertNull(s.decrypt("v1:AAAA"))
    }
}
