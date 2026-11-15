package kr.htaxlog.relay.common

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** 토큰 생성·해시. 기기 토큰 원문은 DB에 저장하지 않고 sha256 해시만 저장한다. */
object Tokens {
    private val random = SecureRandom()

    /** 헷갈리는 0/O, 1/I 를 뺀 문자. */
    private const val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    /** 기기 토큰·QR 토큰. URL에 넣을 수 있는 32바이트 랜덤. */
    fun newToken(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** 연결 코드 6자리 (F-50). */
    fun newConnectCode(): String = randomCode(6)

    /** QR 주소에 들어가는 차량 공개 코드 8자리. */
    fun newVehicleCode(): String = randomCode(8).lowercase()

    fun sha256Hex(value: String): String = sha256Hex(value.toByteArray())

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun randomCode(length: Int): String =
        (1..length).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
}
