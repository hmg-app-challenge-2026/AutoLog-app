package kr.htaxlog.vehicle.mail

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.SocketException
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * 테스트용 최소 SMTP 서버. TLS 없이 EHLO · AUTH LOGIN/PLAIN · MAIL · RCPT · DATA · QUIT만 처리한다.
 * 받은 메일 원문은 [received]에 쌓인다.
 */
class FakeSmtpServer(
    private val user: String,
    private val password: String,
    /** 이 주소로 오는 RCPT는 550으로 거부한다. */
    private val rejectRecipient: String? = null,
) : AutoCloseable {
    private val server = ServerSocket(0)
    val port: Int get() = server.localPort
    val received = CopyOnWriteArrayList<String>()

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket =
                    try {
                        server.accept()
                    } catch (e: SocketException) {
                        break
                    }
                socket.use { s ->
                    val r = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                    val w = PrintWriter(s.getOutputStream(), true)
                    session(r) { w.print("$it\r\n").also { w.flush() } }
                }
            }
        }
    }

    private fun session(r: BufferedReader, reply: (String) -> Unit) {
        reply("220 fake ESMTP")
        while (true) {
            val line = r.readLine() ?: return
            val cmd = line.substringBefore(' ').uppercase()
            when {
                cmd == "EHLO" || cmd == "HELO" -> {
                    reply("250-fake")
                    reply("250 AUTH LOGIN PLAIN")
                }
                line.uppercase().startsWith("AUTH PLAIN") -> {
                    val b64 = line.split(' ').getOrNull(2) ?: run {
                        reply("334 ")
                        r.readLine()
                    }
                    val parts = String(Base64.getDecoder().decode(b64)).split('\u0000')
                    reply(if (parts.getOrNull(1) == user && parts.getOrNull(2) == password) "235 ok" else "535 bad")
                }
                line.uppercase().startsWith("AUTH LOGIN") -> {
                    val u = line.split(' ').getOrNull(2) ?: run {
                        reply("334 VXNlcm5hbWU6")
                        r.readLine()
                    }
                    reply("334 UGFzc3dvcmQ6")
                    val p = r.readLine()
                    val ok = decode(u) == user && decode(p) == password
                    reply(if (ok) "235 ok" else "535 bad")
                }
                cmd == "RCPT" && rejectRecipient != null && line.contains(rejectRecipient) -> reply("550 no such user")
                cmd == "DATA" -> {
                    reply("354 go")
                    val body = StringBuilder()
                    while (true) {
                        val l = r.readLine() ?: return
                        if (l == ".") break
                        body.append(if (l.startsWith("..")) l.substring(1) else l).append("\r\n")
                    }
                    received += body.toString()
                    reply("250 queued")
                }
                cmd == "QUIT" -> {
                    reply("221 bye")
                    return
                }
                else -> reply("250 ok")
            }
        }
    }

    private fun decode(b64: String?) = b64?.let { String(Base64.getDecoder().decode(it.trim())) }

    override fun close() = server.close()
}
