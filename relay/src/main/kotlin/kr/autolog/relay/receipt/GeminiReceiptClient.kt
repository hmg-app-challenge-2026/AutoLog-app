package kr.autolog.relay.receipt

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kr.autolog.relay.common.RelayProperties
import kr.autolog.shared.ReceiptFields
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.Base64

/** OCR 결과. confidence 키는 ReceiptFields 필드 이름과 같다. */
data class OcrResult(val fields: ReceiptFields, val confidence: Map<String, Double>)

/** 영수증 이미지 → 구조화 필드. 테스트에서 가짜로 바꿀 수 있게 인터페이스로 둔다. */
fun interface ReceiptOcr {
    /** 실패하면 예외. 재시도는 구현이 한다. */
    fun extract(image: ByteArray, mimeType: String): OcrResult
}

/**
 * Gemini API(REST)로 영수증 필드를 뽑는다 (F-41, 기술명세 4.5).
 * 키는 서버 환경변수(GEMINI_API_KEY)에만 둔다. responseSchema로 JSON 모양을 강제한다.
 */
@Component
class GeminiReceiptClient(private val props: RelayProperties, restClientBuilder: RestClient.Builder) : ReceiptOcr {
    private val log = LoggerFactory.getLogger(javaClass)
    private val rest = restClientBuilder.baseUrl(props.gemini.baseUrl)
        .apply {
            if (props.gemini.timeoutSec > 0) {
                it.requestFactory(
                    SimpleClientHttpRequestFactory().apply {
                        setConnectTimeout(CONNECT_TIMEOUT_MS)
                        setReadTimeout(props.gemini.timeoutSec * 1_000)
                    },
                )
            }
        }.build()
    private val json = Json { ignoreUnknownKeys = true }

    override fun extract(image: ByteArray, mimeType: String): OcrResult {
        check(props.gemini.apiKey.isNotBlank()) { "GEMINI_API_KEY가 설정되지 않았습니다" }
        var last: Exception? = null
        val startedAt = System.nanoTime()
        repeat(props.gemini.maxAttempts) { attempt ->
            val elapsedSec = (System.nanoTime() - startedAt) / 1_000_000_000
            if (attempt > 0 && elapsedSec + props.gemini.timeoutSec > props.gemini.budgetSec) {
                log.warn("Gemini OCR 시간 예산 {}초를 넘겨 재시도를 멈춘다", props.gemini.budgetSec)
                throw IllegalStateException("Gemini OCR 시간 초과", last)
            }
            try {
                return parse(call(image, mimeType))
            } catch (e: RestClientException) {
                last = e
            } catch (e: IllegalArgumentException) {
                last = e // 응답 JSON 형식 오류
            }
            log.warn("Gemini OCR 실패 {}/{}: {}", attempt + 1, props.gemini.maxAttempts, last?.message)
            if (attempt < props.gemini.maxAttempts - 1) Thread.sleep(BACKOFF_MS * (attempt + 1))
        }
        throw IllegalStateException("Gemini OCR ${props.gemini.maxAttempts}회 실패", last)
    }

    private fun call(image: ByteArray, mimeType: String): String = rest
        .post()
        .uri("/v1beta/models/{model}:generateContent", props.gemini.model)
        .header("x-goog-api-key", props.gemini.apiKey)
        .contentType(MediaType.APPLICATION_JSON)
        .body(requestBody(image, mimeType).toString())
        .retrieve()
        .body(String::class.java) ?: throw IllegalArgumentException("Gemini 응답이 비었습니다")

    internal fun parse(responseBody: String): OcrResult {
        val text =
            json
                .parseToJsonElement(responseBody)
                .jsonObject["candidates"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("content")
                ?.jsonObject
                ?.get("parts")
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("text")
                ?.jsonPrimitive
                ?.content ?: throw IllegalArgumentException("Gemini 응답에 text가 없습니다")
        val r = json.decodeFromString(GeminiReceipt.serializer(), text)
        return OcrResult(
            ReceiptFields(
                r.merchant,
                r.datetime,
                r.fuelType,
                r.liters,
                r.unitPrice,
                r.discountWon,
                r.amountWon,
                r.cardLast4,
            ),
            r.confidence.filterValues { it in 0.0..1.0 },
        )
    }

    private fun requestBody(image: ByteArray, mimeType: String): JsonObject = buildJsonObject {
        putJsonArray("contents") {
            add(
                buildJsonObject {
                    putJsonArray("parts") {
                        add(buildJsonObject { put("text", PROMPT) })
                        add(
                            buildJsonObject {
                                putJsonObject("inline_data") {
                                    put("mime_type", mimeType)
                                    put("data", Base64.getEncoder().encodeToString(image))
                                }
                            },
                        )
                    }
                },
            )
        }
        putJsonObject("generationConfig") {
            put("temperature", 0)
            put("responseMimeType", "application/json")
            put("responseSchema", RESPONSE_SCHEMA)
        }
    }

    @Serializable
    private data class GeminiReceipt(
        val merchant: String? = null,
        val datetime: String? = null,
        val fuelType: String? = null,
        val liters: Double? = null,
        val unitPrice: Int? = null,
        val discountWon: Int? = null,
        val amountWon: Int? = null,
        val cardLast4: String? = null,
        val confidence: Map<String, Double> = emptyMap(),
    )

    companion object {
        private const val BACKOFF_MS = 1_000L
        private const val CONNECT_TIMEOUT_MS = 5_000

        /** 기술명세 4.5 프롬프트. */
        const val PROMPT =
            "주유 영수증. 상호, 거래일시(ISO 8601 +09:00), 유종, 수량(L), 단가, 할인, 결제금액, 카드 끝 4자리를 JSON으로. " +
                "없으면 null. 각 필드 확신도 0~1을 confidence에 필드 이름을 키로 넣어라. 카드번호는 끝 4자리 외 출력 금지. " +
                "필드: merchant, datetime, fuelType, liters, unitPrice, discountWon, amountWon, cardLast4"

        private val FIELD_TYPES =
            mapOf(
                "merchant" to "STRING",
                "datetime" to "STRING",
                "fuelType" to "STRING",
                "liters" to "NUMBER",
                "unitPrice" to "INTEGER",
                "discountWon" to "INTEGER",
                "amountWon" to "INTEGER",
                "cardLast4" to "STRING",
            )

        private val RESPONSE_SCHEMA: JsonObject =
            buildJsonObject {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    FIELD_TYPES.forEach { (name, type) ->
                        putJsonObject(name) {
                            put("type", type)
                            put("nullable", true)
                        }
                    }
                    putJsonObject("confidence") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            FIELD_TYPES.keys.forEach { name -> putJsonObject(name) { put("type", "NUMBER") } }
                        }
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("confidence")) })
            }
    }
}
