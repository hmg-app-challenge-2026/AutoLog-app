package kr.autolog.relay.receipt

import kr.autolog.relay.common.ApiException
import kr.autolog.relay.common.RelayProperties
import kr.autolog.relay.common.Tokens
import kr.autolog.relay.device.DeviceVehicle
import kr.autolog.shared.QrTokenResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 영수증 QR 1회 토큰 (F-30). 10분, 업로드 1번에 소모된다.
 * DB에는 토큰 해시만 저장한다. QR 주소: `{publicBaseUrl}/r/{vehicleCode}?t={token}`
 */
@Service
class QrTokenService(
    private val repo: QrTokenRepository,
    private val props: RelayProperties,
    private val clock: Clock,
) {
    fun issue(vehicle: DeviceVehicle): QrTokenResponse {
        val token = Tokens.newToken()
        val expiresAt = clock.instant().plus(Duration.ofMinutes(props.qrTokenTtlMinutes))
        repo.insert(Tokens.sha256Hex(token), vehicle.vehicleId, expiresAt)
        val url = "${props.publicBaseUrl.trimEnd('/')}/r/${vehicle.vehicleCode}?t=$token"
        return QrTokenResponse(
            url,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(expiresAt.atOffset(ZoneOffset.ofHours(9))),
        )
    }

    /** 업로드 시 토큰을 소모하고 차량 id를 돌려준다. 무효면 401. */
    fun consume(vehicleCode: String, token: String?): UUID {
        if (token.isNullOrBlank()) throw invalid()
        return repo.consume(vehicleCode, Tokens.sha256Hex(token), clock.instant()) ?: throw invalid()
    }

    /** 토큰이 가리키는 차량(만료·사용 여부 무관). 모르는 토큰이면 401. */
    fun vehicleOf(vehicleCode: String, token: String?): UUID {
        if (token.isNullOrBlank()) throw invalid()
        return repo.vehicleOf(vehicleCode, Tokens.sha256Hex(token)) ?: throw invalid()
    }

    /** 아직 쓸 수 있는지만 본다. 못 쓰면 401. */
    fun requireValid(vehicleCode: String, token: String?) {
        if (!isValid(vehicleCode, token)) throw invalid()
    }

    fun isValid(vehicleCode: String, token: String?): Boolean =
        !token.isNullOrBlank() && repo.isValid(vehicleCode, Tokens.sha256Hex(token), clock.instant())

    private fun invalid() =
        ApiException(HttpStatus.UNAUTHORIZED, "QR_TOKEN_INVALID", "QR이 만료됐거나 이미 사용됐습니다. 차 화면에서 QR을 다시 띄워 주세요")
}
