package kr.htaxlog.vehicle.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.htaxlog.vehicle.core.ConnectionState
import kr.htaxlog.vehicle.core.DeviceApi
import kr.htaxlog.vehicle.core.QrResult
import kr.htaxlog.vehicle.core.RegisterResult
import java.time.Clock
import java.time.OffsetDateTime

/**
 * 서버 연결 (F-01, F-40) — [DeviceApi] 구현. 기기 토큰을 보관하고 relay 호출에 붙인다.
 * 서버가 없거나 꺼져 있어도 로컬 기능(운행·엑셀·메일)은 막지 않는다. 실패는 상태로만 알린다.
 */
class DeviceRepository(
    private val store: DeviceStore,
    private val http: RelayHttp,
    private val clock: Clock = Clock.systemUTC(),
    private val log: (String) -> Unit = {},
) : DeviceApi {
    private val _connection = MutableStateFlow(stateOf(store.load(), reachable = true))
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    /** 등록에 성공한 직후 부른다(F-01 ⑤ "즉시 inbox 1회 수신"). 조립 지점이 넣는다. */
    @Volatile var onRegistered: suspend () -> Unit = {}

    /** 등록된 차량 id. 운행 기록의 vehicleId. 미등록이면 null. */
    val vehicleId: String? get() = store.load()?.vehicleId

    /** 운행기록부 파일명·메일 제목에 쓰는 번호판. 모르면 null. */
    val plateNo: String? get() = store.load()?.plateNo

    /** 운행기록부 별지 서식 ①차종. 모르면 null. */
    val model: String? get() = store.load()?.model

    override suspend fun register(serverUrl: String, code: String): RegisterResult {
        val url = normalizeUrl(serverUrl) ?: return RegisterResult.Invalid("서버 주소는 https:// 로 시작해야 해요")
        val c = code.trim().uppercase()
        if (!CODE.matches(c)) return RegisterResult.Invalid("연결 코드 6자리를 확인해 주세요")
        return when (val r = http.register(url, c)) {
            is RelayResult.Ok -> {
                val rec =
                    DeviceRecord(
                        url,
                        r.value.deviceToken,
                        r.value.vehicleId,
                        r.value.orgId,
                        r.value.plateNo,
                        r.value.model,
                    )
                store.save(rec)
                _connection.value = stateOf(rec, reachable = true)
                log("registered vehicle=${rec.vehicleId} plate=${rec.plateNo}")
                runCatching { onRegistered() }.onFailure { log("first sync failed: ${it.message}") }
                RegisterResult.Ok
            }
            is RelayResult.Failure -> RegisterResult.Invalid(
                when {
                    r.status == null -> "서버에 연결하지 못했어요. 주소와 네트워크를 확인해 주세요"
                    r.code == "CONNECT_CODE_INVALID" -> "연결 코드가 맞지 않거나 만료됐어요"
                    else -> r.message
                },
            )
        }
    }

    /**
     * 등록된 서버로 호출한다. 결과에 따라 연결 상태를 갱신한다:
     * 네트워크 실패 → Unreachable, 401(토큰 회수) → 등록 해제(NotRegistered), 성공 → Registered.
     */
    suspend fun <T> call(block: suspend RelayHttp.(url: String, token: String) -> RelayResult<T>): RelayResult<T> {
        val rec = store.load() ?: return RelayResult.Failure(null, NOT_REGISTERED, "서버에 연결되지 않았어요")
        val r = http.block(rec.serverUrl, rec.token)
        when {
            r is RelayResult.Failure && r.status == UNAUTHORIZED -> {
                log("device token revoked")
                store.clear()
                _connection.value = ConnectionState.NotRegistered
            }
            r is RelayResult.Failure && r.status == null -> _connection.value = stateOf(store.load(), reachable = false)
            r is RelayResult.Ok -> _connection.value = stateOf(store.load(), reachable = true)
            else -> Unit
        }
        return r
    }

    /** inbox 수신 성공을 기록한다(설정 화면 "마지막 동기화"). */
    fun markSynced() {
        val rec = store.load() ?: return
        store.save(rec.copy(lastSyncAt = clock.millis()))
        _connection.value = stateOf(store.load(), reachable = true)
    }

    /** 영수증 QR용 1회 토큰 URL (F-30). 토큰이 1회용이라 캐시하지 않는다. */
    suspend fun issueQr(): QrResult = when (val r = call { url, token -> qrToken(url, token) }) {
        is RelayResult.Ok -> QrResult.Issued(
            r.value.url,
            runCatching { OffsetDateTime.parse(r.value.expiresAt).toInstant().toEpochMilli() }
                .getOrDefault(clock.millis() + QR_FALLBACK_TTL_MS),
        )
        is RelayResult.Failure -> QrResult.Failed(if (r.code == NOT_REGISTERED) r.message else "서버에 연결하지 못했어요")
    }

    private fun stateOf(rec: DeviceRecord?, reachable: Boolean): ConnectionState = when {
        rec == null -> ConnectionState.NotRegistered
        reachable -> ConnectionState.Registered(labelOf(rec), rec.serverUrl, rec.lastSyncAt)
        else -> ConnectionState.Unreachable(labelOf(rec), rec.serverUrl)
    }

    companion object {
        /** relay `Tokens.newConnectCode()`와 같은 문자 집합(0·1·I·O 제외). */
        val CODE = Regex("[2-9A-HJ-NP-Z]{6}")
        const val NOT_REGISTERED = "NOT_REGISTERED"
        private const val UNAUTHORIZED = 401
        private const val QR_FALLBACK_TTL_MS = 10 * 60_000L

        /** "12가3456 · 아이오닉 6". 번호판을 모르면 차량 id 앞부분. */
        fun labelOf(rec: DeviceRecord): String =
            listOfNotNull(rec.plateNo ?: "차량 ${rec.vehicleId.take(8)}", rec.model).joinToString(" · ")

        /** 끝의 `/`를 떼고, http(s)만 받는다. */
        fun normalizeUrl(raw: String): String? {
            val u = raw.trim().trimEnd('/')
            return u.takeIf { (it.startsWith("https://") || it.startsWith("http://")) && it.length > "https://".length }
        }
    }
}
