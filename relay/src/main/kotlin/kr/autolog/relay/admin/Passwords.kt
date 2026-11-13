package kr.autolog.relay.admin

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 관리자 비밀번호 해시. JDK 내장 PBKDF2-HMAC-SHA256(의존성 추가 없음).
 * 저장 형식: `pbkdf2$반복횟수$솔트(base64)$해시(base64)` — 반복 횟수를 올려도 예전 해시를 그대로 검증한다.
 */
object Passwords {
    private const val PREFIX = "pbkdf2"
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    const val MIN_LENGTH = 8

    private val random = SecureRandom()
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()

    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val key = derive(password, salt, ITERATIONS)
        return listOf(
            PREFIX,
            ITERATIONS.toString(),
            b64.encodeToString(salt),
            b64.encodeToString(key),
        ).joinToString("$")
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        val salt = runCatching { unb64.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { unb64.decode(parts[3]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, derive(password, salt, iterations))
    }

    /** 비밀번호 규칙: 8자 이상. 맞지 않으면 이유, 맞으면 null. */
    fun problem(password: String): String? =
        if (password.length < MIN_LENGTH) "비밀번호는 ${MIN_LENGTH}자 이상이어야 합니다" else null

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
