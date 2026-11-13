package kr.autolog.vehicle.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kr.autolog.shared.AutoLogJson
import kr.autolog.shared.DeviceRegisterRequest
import kr.autolog.shared.DeviceRegisterResponse
import kr.autolog.shared.InboxItem
import kr.autolog.shared.QrTokenResponse
import kr.autolog.shared.ReverseGeocodeResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 중계 서버 호출 결과. 예외 대신 값으로 돌려준다(CONVENTIONS 7.5). */
sealed interface RelayResult<out T> {
    data class Ok<T>(val value: T) : RelayResult<T>

    /** [status] null = 네트워크 실패(서버에 닿지 못함). [code]는 서버 에러 코드(예: CONNECT_CODE_INVALID). */
    data class Failure(val status: Int?, val code: String?, val message: String) : RelayResult<Nothing>
}

/**
 * 중계 서버(relay) 차량 API (기술명세 4.3). 서버 주소는 회사마다 달라서 호출마다 받는다(런타임 설정, R-4).
 * 차 → 서버로는 운행·비용을 보내지 않는다. inbox를 가져오고(ack), 기기 등록·QR 토큰만 요청한다.
 */
class RelayHttp(
    private val client: OkHttpClient = defaultClient(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun register(baseUrl: String, code: String): RelayResult<DeviceRegisterResponse> = call(
        Request.Builder().url("$baseUrl/v1/devices")
            .post(
                AutoLogJson.encodeToString(
                    DeviceRegisterRequest.serializer(),
                    DeviceRegisterRequest(code),
                ).toJsonBody(),
            ),
        DeviceRegisterResponse.serializer(),
    )

    suspend fun inbox(baseUrl: String, token: String): RelayResult<List<InboxItem>> =
        call(Request.Builder().url("$baseUrl/v1/inbox").get().bearer(token), ListSerializer(InboxItem.serializer()))

    suspend fun ack(baseUrl: String, token: String, inboxId: Long): RelayResult<Unit> =
        call(Request.Builder().url("$baseUrl/v1/inbox/$inboxId/ack").post(EMPTY).bearer(token), null)

    suspend fun qrToken(baseUrl: String, token: String): RelayResult<QrTokenResponse> =
        call(Request.Builder().url("$baseUrl/v1/qr-tokens").post(EMPTY).bearer(token), QrTokenResponse.serializer())

    /**
     * 운행 좌표 → 주소 (F-06, F-07). 차는 짧게만 기다리고 내비 주소로 넘어가야 해서 이 호출만 3초 타임아웃.
     * 정밀 좌표는 URL에만 실리고 로그에는 남기지 않는다(CONVENTIONS 7.6).
     */
    suspend fun reverseGeocode(
        baseUrl: String,
        token: String,
        lat: Double,
        lng: Double,
    ): RelayResult<ReverseGeocodeResponse> = call(
        Request.Builder().url("$baseUrl/v1/geo/reverse?lat=$lat&lng=$lng").get().bearer(token),
        ReverseGeocodeResponse.serializer(),
        quickClient,
    )

    private val quickClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(QUICK_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(QUICK_TIMEOUT_SEC, TimeUnit.SECONDS)
            .callTimeout(QUICK_TIMEOUT_SEC + 1, TimeUnit.SECONDS)
            .build()
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> call(
        builder: Request.Builder,
        serializer: KSerializer<T>?,
        http: OkHttpClient = client,
    ): RelayResult<T> = withContext(io) {
        try {
            http.newCall(builder.build()).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (res.isSuccessful) {
                    RelayResult.Ok(serializer?.let { AutoLogJson.decodeFromString(it, body) } ?: Unit as T)
                } else {
                    errorOf(res.code, body)
                }
            }
        } catch (e: IOException) {
            RelayResult.Failure(null, null, e.message ?: "network error")
        } catch (e: IllegalArgumentException) { // 잘못된 URL·응답 JSON
            RelayResult.Failure(null, "BAD_RESPONSE", e.message ?: "bad response")
        }
    }

    private fun errorOf(status: Int, body: String): RelayResult.Failure {
        val json = runCatching { AutoLogJson.parseToJsonElement(body).jsonObject }.getOrNull()
        return RelayResult.Failure(
            status = status,
            code = json?.get("code")?.jsonPrimitive?.content,
            message = json?.get("message")?.jsonPrimitive?.content ?: "HTTP $status",
        )
    }

    private fun Request.Builder.bearer(token: String) = header("Authorization", "Bearer $token")

    companion object {
        private val JSON = "application/json".toMediaType()
        private const val QUICK_TIMEOUT_SEC = 3L
        private val EMPTY = ByteArray(0).toRequestBody(null)

        private fun String.toJsonBody() = toRequestBody(JSON)

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
