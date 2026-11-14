package kr.autolog.relay.receipt

import kr.autolog.relay.common.RelayProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class GeminiReceiptClientTest {
    private val props =
        RelayProperties(
            gemini = RelayProperties.Gemini(
                apiKey = "test-key",
                baseUrl = "https://gemini.test",
                maxAttempts = 2,
                timeoutSec = 0,
            ),
        )
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = GeminiReceiptClient(props, builder)

    private val okBody =
        """
        {"candidates":[{"content":{"parts":[{"text":
          "{\"merchant\":\"SK 판교주유소\",\"datetime\":\"2026-11-02T09:20:00+09:00\",\"fuelType\":\"휘발유\",\"liters\":40.0,\"unitPrice\":1700,\"discountWon\":null,\"amountWon\":68000,\"cardLast4\":\"1234\",\"confidence\":{\"amountWon\":0.98,\"liters\":0.93,\"bogus\":7}}"
        }]}}]}
        """.trimIndent()

    @Test
    fun `Gemini 응답 JSON에서 필드와 확신도를 읽는다`() {
        server
            .expect(requestTo("https://gemini.test/v1beta/models/gemini-3.5-flash-lite:generateContent"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("x-goog-api-key", "test-key"))
            .andRespond(withSuccess(okBody, MediaType.APPLICATION_JSON))

        val r = client.extract(byteArrayOf(1, 2, 3), "image/jpeg")

        assertEquals(68_000, r.fields.amountWon)
        assertEquals(40.0, r.fields.liters)
        assertEquals("1234", r.fields.cardLast4)
        assertEquals(mapOf("amountWon" to 0.98, "liters" to 0.93), r.confidence) // 0~1 밖 값은 버린다
        server.verify()
    }

    @Test
    fun `실패하면 설정한 횟수만큼 재시도한 뒤 예외`() {
        server
            .expect(
                ExpectedCount.times(2),
                requestTo("https://gemini.test/v1beta/models/gemini-3.5-flash-lite:generateContent"),
            )
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))

        assertThrows<IllegalStateException> { client.extract(byteArrayOf(1), "image/jpeg") }
        server.verify()
    }

    @Test
    fun `키가 없으면 호출하지 않고 실패`() {
        val noKey = GeminiReceiptClient(RelayProperties(), RestClient.builder())

        val e = assertThrows<IllegalStateException> { noKey.extract(byteArrayOf(1), "image/jpeg") }

        assertTrue(e.message!!.contains("GEMINI_API_KEY"))
    }
}
