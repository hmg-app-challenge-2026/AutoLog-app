package kr.autolog.vehicle.mail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kr.autolog.shared.MailAccountDto
import kr.autolog.vehicle.core.MailError
import kr.autolog.vehicle.core.MailResult
import kr.autolog.vehicle.core.ReportFile
import kr.autolog.vehicle.core.SmtpConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.net.ServerSocket
import java.time.YearMonth
import java.util.Properties
import javax.mail.Session
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class SmtpMailerTest {
    private val server =
        FakeSmtpServer(user = "me@example.com", password = "app-pass", rejectRecipient = "ghost@example.com")
    private val config = SmtpConfig("127.0.0.1", server.port, "me@example.com", "app-pass", startTls = false)
    private val xlsx = File.createTempFile("report", ".xlsx").apply { writeBytes(byteArrayOf(0x50, 0x4B, 3, 4)) }
    private val report = ReportFile(YearMonth.of(2026, 11), xlsx, "12가3456", 42, 1234.56, 3)
    private val store = MemStore()
    private val service = MailService(store, SmtpMailer(timeoutMs = 3_000), Dispatchers.Unconfined, log = {})

    @After
    fun tearDown() {
        server.close()
        xlsx.delete()
    }

    private fun parse(raw: String) =
        MimeMessage(Session.getInstance(Properties()), ByteArrayInputStream(raw.toByteArray()))

    @Test
    fun `운행기록부를 제목·요약 본문·엑셀 첨부로 보낸다`() = runTest {
        store.save(config)

        val result = service.send(report, listOf("boss@example.com"))

        assertEquals(MailResult.Ok, result)
        val msg = parse(server.received.single())
        assertEquals("[AutoLog] 2026년 11월 운행기록부 · 12가3456", msg.subject)
        assertEquals("boss@example.com", msg.allRecipients.single().toString())
        val parts = msg.content as MimeMultipart
        val body = parts.getBodyPart(0).content as String
        assertTrue(body.contains("운행 42건"))
        assertTrue(body.contains("1,234.6 km"))
        assertTrue(body.contains("검토 필요 3건"))
        val attachment = parts.getBodyPart(1)
        assertEquals("운행기록부_2026-11_12가3456.xlsx", attachment.fileName)
        // Gmail이 풀 수 있는 RFC 2231 형식이어야 한다("=?UTF-8?B?" 형식이면 첨부 이름이 깨져 보임)
        val raw = server.received.single()
        assertTrue(raw.contains("filename*=UTF-8''"))
        assertFalse(raw.contains("filename=\"=?UTF-8?"))
        assertTrue(attachment.contentType.startsWith(MailComposer.XLSX_MIME))
    }

    @Test
    fun `앱 비밀번호가 틀리면 AUTH`() = runTest {
        store.save(config.copy(password = "wrong"))

        val result = service.send(report, listOf("boss@example.com"))

        assertEquals(MailError.AUTH, (result as MailResult.Failed).error)
        assertTrue(server.received.isEmpty())
    }

    @Test
    fun `서버에 연결할 수 없으면 NETWORK`() = runTest {
        val closedPort = ServerSocket(0).use { it.localPort }
        store.save(config.copy(port = closedPort))

        val result = service.send(report, listOf("boss@example.com"))

        assertEquals(MailError.NETWORK, (result as MailResult.Failed).error)
    }

    @Test
    fun `주소 형식이 틀리면 접속하지 않고 INVALID_RECIPIENT`() = runTest {
        store.save(config)

        val result = service.send(report, listOf("not-an-email"))

        assertEquals(MailError.INVALID_RECIPIENT, (result as MailResult.Failed).error)
        assertTrue(server.received.isEmpty())
    }

    @Test
    fun `서버가 받는 사람을 거부하면 INVALID_RECIPIENT`() = runTest {
        store.save(config)

        val result = service.send(report, listOf("ghost@example.com"))

        assertEquals(MailError.INVALID_RECIPIENT, (result as MailResult.Failed).error)
    }

    @Test
    fun `메일 설정 전에는 NOT_CONFIGURED`() = runTest {
        val result = service.send(report, listOf("boss@example.com"))

        assertEquals(MailError.NOT_CONFIGURED, (result as MailResult.Failed).error)
        assertFalse(service.smtpConfigured())
    }

    @Test
    fun `설정은 로그인에 성공해야 저장한다`() = runTest {
        val bad = service.saveSmtp(config.copy(password = "wrong"))
        assertEquals(MailError.AUTH, (bad as MailResult.Failed).error)
        assertNull(store.load())

        val ok = service.saveSmtp(config)

        assertEquals(MailResult.Ok, ok)
        assertTrue(service.smtpConfigured())
    }

    @Test
    fun `관리자 웹에서 온 계정이 차의 메일 계정을 덮어쓰고 그 계정으로 보낸다`() = runTest {
        store.save(config.copy(username = "old@example.com", password = "other"))

        service.applyServerAccount(
            MailAccountDto("127.0.0.1", server.port, "me@example.com", "app-pass", startTls = false),
        )
        val sent = service.send(report, listOf("boss@example.com"))

        assertEquals(MailResult.Ok, sent)
        assertEquals("me@example.com", service.account.value)
    }

    @Test
    fun `관리자 웹에서 계정을 지우면 차의 메일 계정도 지운다`() {
        store.save(config)

        service.applyServerAccount(null)

        assertNull(store.load())
        assertNull(service.account.value)
    }

    @Test
    fun `비밀번호가 빠진 계정 편지는 반영하지 않는다`() {
        val applied = service.applyServerAccount(MailAccountDto("smtp.example.com", username = "a@example.com"))

        assertFalse(applied)
        assertNull(store.load())
    }
}

private class MemStore : SmtpCredentialStore {
    private var config: SmtpConfig? = null

    override fun load() = config

    override fun save(config: SmtpConfig) {
        this.config = config
    }

    override fun clear() {
        config = null
    }
}
