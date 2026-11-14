package kr.autolog.relay.mail

import jakarta.mail.AuthenticationFailedException
import jakarta.mail.MessagingException
import jakarta.mail.Session
import org.springframework.stereotype.Component
import java.util.Properties

/** 저장 전에 SMTP 로그인만 해 본다(메일은 보내지 않는다). 테스트에서 바꿔 끼우려고 인터페이스로 둔다. */
fun interface SmtpVerifier {
    /** 성공하면 null, 실패하면 사람이 읽을 이유. */
    fun verify(host: String, port: Int, username: String, password: String, startTls: Boolean): SmtpFailure?
}

enum class SmtpFailure { AUTH, NETWORK }

@Component
class JakartaSmtpVerifier : SmtpVerifier {
    override fun verify(host: String, port: Int, username: String, password: String, startTls: Boolean): SmtpFailure? {
        val ssl = port == SSL_PORT
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.connectiontimeout", TIMEOUT_MS)
            put("mail.smtp.timeout", TIMEOUT_MS)
            put("mail.smtp.writetimeout", TIMEOUT_MS)
            if (ssl) {
                put("mail.smtp.ssl.enable", "true")
            } else if (startTls) {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
        }
        return try {
            Session.getInstance(props).getTransport("smtp").use { it.connect(host, port, username, password) }
            null
        } catch (e: AuthenticationFailedException) {
            SmtpFailure.AUTH
        } catch (e: MessagingException) {
            SmtpFailure.NETWORK
        }
    }

    private companion object {
        const val SSL_PORT = 465
        const val TIMEOUT_MS = "10000"
    }
}
