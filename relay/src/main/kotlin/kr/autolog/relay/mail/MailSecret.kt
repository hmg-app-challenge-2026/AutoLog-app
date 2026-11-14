package kr.autolog.relay.mail

import kr.autolog.relay.common.ApiException
import kr.autolog.relay.common.RelayProperties
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 메일 앱 비밀번호 암호화 (AES-256-GCM). 키는 `MAIL_SECRET_KEY`(아무 길이의 비밀 문자열)를 SHA-256 한 값.
 * DB에는 `v1:<base64(iv 12바이트 + 암호문)>`만 남는다. 키가 비어 있으면 저장·복호화를 모두 거절한다(평문 저장 금지).
 * 키를 바꾸면 이미 저장된 비밀번호는 풀리지 않는다 — 회사 관리자가 다시 넣어야 한다.
 */
@Component
class MailSecret(props: RelayProperties) {
    private val key: SecretKeySpec? = props.mail.secretKey.takeIf { it.isNotBlank() }?.let {
        SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()), "AES")
    }
    private val random = SecureRandom()

    /** 키가 없으면 503. SMTP 확인처럼 오래 걸리는 일 전에 먼저 부른다. */
    fun requireKey() {
        if (key == null) throw notConfigured()
    }

    fun encrypt(plain: String): String {
        val k = key ?: throw notConfigured()
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(ALGO).apply { init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(TAG_BITS, iv)) }
        return PREFIX + Base64.getEncoder().encodeToString(iv + cipher.doFinal(plain.toByteArray()))
    }

    /** 키가 없거나 바뀌어서 풀 수 없으면 null. */
    fun decrypt(stored: String): String? {
        val k = key ?: return null
        if (!stored.startsWith(PREFIX)) return null
        return runCatching {
            val raw = Base64.getDecoder().decode(stored.removePrefix(PREFIX))
            val cipher = Cipher.getInstance(ALGO).apply {
                init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
            }
            String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES))
        }.getOrNull()
    }

    private fun notConfigured() = ApiException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "MAIL_SECRET_MISSING",
        "서버에 메일 암호화 키(MAIL_SECRET_KEY)가 없어 메일 계정을 저장할 수 없습니다",
    )

    private companion object {
        const val ALGO = "AES/GCM/NoPadding"
        const val PREFIX = "v1:"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
