package kr.htaxlog.vehicle.mail

import kr.htaxlog.vehicle.core.MailError
import kr.htaxlog.vehicle.core.MailResult
import kr.htaxlog.vehicle.core.SmtpConfig
import java.io.File
import java.io.IOException
import java.util.Properties
import javax.mail.AuthenticationFailedException
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.SendFailedException
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.AddressException
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

/** 보낼 메일 한 통. */
data class OutgoingMail(
    val to: List<String>,
    val subject: String,
    val body: String,
    val attachment: File,
    val attachmentName: String,
    val attachmentMime: String,
)

/**
 * Jakarta Mail(android-mail)로 SMTP 발송 (F-22, 기술명세 3.6). 블로킹 함수라 IO 디스패처에서 부른다.
 * 587은 STARTTLS(필수), 465는 SSL로 접속한다. 예외는 밖으로 던지지 않고 [MailResult]로 돌려준다.
 *
 * [검증 필요] 차량 LTE에서 587이 열려 있는지. 막혀 있으면 HTTPS 메일 API 폴백을 붙인다(기능명세 F-22 예외).
 */
class SmtpMailer(private val timeoutMs: Int = DEFAULT_TIMEOUT_MS) {
    fun send(config: SmtpConfig, mail: OutgoingMail): MailResult {
        val recipients =
            parseRecipients(mail.to)
                ?: return MailResult.Failed(MailError.INVALID_RECIPIENT, "받는 사람 주소가 올바르지 않습니다")
        return runCatchingMail {
            val session = session(config)
            val msg =
                MimeMessage(session).apply {
                    setFrom(InternetAddress(config.username, config.fromName ?: DEFAULT_FROM_NAME, UTF8))
                    setRecipients(Message.RecipientType.TO, recipients)
                    setSubject(mail.subject, UTF8)
                    setContent(
                        MimeMultipart().apply {
                            addBodyPart(MimeBodyPart().apply { setText(mail.body, UTF8) })
                            addBodyPart(
                                MimeBodyPart().apply {
                                    attachFile(mail.attachment, mail.attachmentMime, "base64")
                                    // 한글 파일명은 RFC 2231(filename*=UTF-8''…)로 넣는다. "=?UTF-8?B?…?=" 형식은
                                    // Gmail 등이 첨부 이름으로 풀지 않아 깨져 보인다.
                                    fileName = mail.attachmentName
                                },
                            )
                        },
                    )
                    saveChanges()
                }
            session.getTransport("smtp").use { t ->
                t.connect(config.host, config.port, config.username, config.password)
                t.sendMessage(msg, msg.allRecipients)
            }
        }
    }

    /** 설정 저장 전에 접속·로그인만 해 본다 (F-24). */
    fun verify(config: SmtpConfig): MailResult = runCatchingMail {
        session(config).getTransport("smtp").use { t ->
            t.connect(config.host, config.port, config.username, config.password)
        }
    }

    private fun session(c: SmtpConfig): Session {
        val ssl = c.port == SSL_PORT
        val props =
            Properties().apply {
                put("mail.smtp.host", c.host)
                put("mail.smtp.port", c.port.toString())
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", (c.startTls && !ssl).toString())
                put("mail.smtp.starttls.required", (c.startTls && !ssl).toString())
                put("mail.smtp.ssl.enable", ssl.toString())
                put("mail.smtp.ssl.checkserveridentity", "true")
                put("mail.smtp.connectiontimeout", timeoutMs.toString())
                put("mail.smtp.timeout", timeoutMs.toString())
                put("mail.smtp.writetimeout", timeoutMs.toString())
                put("mail.mime.charset", UTF8)
            }
        return Session.getInstance(
            props,
            object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(c.username, c.password)
            },
        )
    }

    private inline fun runCatchingMail(block: () -> Unit): MailResult = try {
        block()
        MailResult.Ok
    } catch (e: AuthenticationFailedException) {
        MailResult.Failed(MailError.AUTH, "메일 계정 아이디나 앱 비밀번호가 틀렸습니다")
    } catch (e: SendFailedException) {
        if (!e.invalidAddresses.isNullOrEmpty()) {
            MailResult.Failed(MailError.INVALID_RECIPIENT, "메일 서버가 받는 사람 주소를 거부했습니다")
        } else {
            classify(e)
        }
    } catch (e: MessagingException) {
        classify(e)
    } catch (e: IOException) {
        // 첨부 파일을 읽지 못한 경우
        MailResult.Failed(MailError.UNKNOWN, "첨부 파일을 읽지 못했습니다")
    }

    private fun classify(e: MessagingException): MailResult = if (generateSequence<Throwable>(e) {
            it.cause
        }.any { it is IOException }
    ) {
        MailResult.Failed(MailError.NETWORK, "메일 서버에 연결하지 못했습니다. 네트워크를 확인해 주세요")
    } else {
        MailResult.Failed(MailError.UNKNOWN, "메일을 보내지 못했습니다")
    }

    private fun parseRecipients(to: List<String>): Array<InternetAddress>? {
        if (to.isEmpty()) return null
        return try {
            to.map { InternetAddress(it.trim(), true).also(InternetAddress::validate) }.toTypedArray()
        } catch (e: AddressException) {
            null
        }
    }

    private inline fun <T> Transport.use(block: (Transport) -> T): T = try {
        block(this)
    } finally {
        runCatching { close() }
    }

    companion object {
        init {
            // 파라미터(첨부 파일명)를 RFC 2231로 인코딩한다. encodefilename(=encoded-word)은 끈다.
            System.setProperty("mail.mime.encodeparameters", "true")
            System.setProperty("mail.mime.encodefilename", "false")
        }

        const val DEFAULT_TIMEOUT_MS = 15_000
        const val SSL_PORT = 465
        private const val UTF8 = "UTF-8"
        private const val DEFAULT_FROM_NAME = "H-TaxLog"
    }
}
