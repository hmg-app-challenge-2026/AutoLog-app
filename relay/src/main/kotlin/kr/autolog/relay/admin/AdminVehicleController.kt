package kr.autolog.relay.admin

import kr.autolog.relay.common.ApiException
import kr.autolog.relay.common.RelayProperties
import kr.autolog.relay.common.Tokens
import kr.autolog.relay.receipt.ReceiptStorage
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class CreateVehicleRequest(val plateNo: String, val model: String? = null)

data class CreateVehicleResponse(val vehicleId: UUID, val vehicleCode: String)

data class ConnectCodeResponse(val code: String, val expiresAt: Instant)

/**
 * 운영 웹 차량 화면 API (F-50). 운영 웹 화면은 프론트 담당.
 * 인증은 [AdminAuth].
 */
@RestController
@RequestMapping("/admin/vehicles")
class AdminVehicleController(
    private val repo: AdminVehicleRepository,
    private val auth: AdminAuth,
    private val props: RelayProperties,
    private val clock: Clock,
    private val storage: ReceiptStorage,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    fun create(
        @RequestHeader("X-Admin-Token", required = false) token: String?,
        @RequestBody body: CreateVehicleRequest,
    ): CreateVehicleResponse {
        val org = authorize(token)
        val code = Tokens.newVehicleCode()
        return CreateVehicleResponse(repo.insertVehicle(org, body.plateNo.trim(), body.model, code), code)
    }

    @GetMapping
    fun list(@RequestHeader("X-Admin-Token", required = false) token: String?): List<AdminVehicle> =
        repo.list(authorize(token))

    /** 연결 코드 발급: 6자리, 24시간, 1회 (F-50 → 차의 F-01). */
    @PostMapping("/{vehicleId}/connect-code")
    fun connectCode(
        @RequestHeader("X-Admin-Token", required = false) token: String?,
        @PathVariable vehicleId: UUID,
    ): ConnectCodeResponse {
        requireVehicle(authorize(token), vehicleId)
        val code = Tokens.newConnectCode()
        val expiresAt = clock.instant().plus(Duration.ofHours(props.connectCodeTtlHours))
        repo.insertConnectCode(code, vehicleId, expiresAt)
        return ConnectCodeResponse(code, expiresAt)
    }

    /** 기기 토큰 회수. 차는 다음 요청부터 401 → 재등록 필요. */
    @DeleteMapping("/{vehicleId}/device")
    fun revoke(
        @RequestHeader("X-Admin-Token", required = false) token: String?,
        @PathVariable vehicleId: UUID,
    ): ResponseEntity<Unit> {
        requireVehicle(authorize(token), vehicleId)
        repo.revokeDevice(vehicleId)
        return ResponseEntity.noContent().build()
    }

    /**
     * 차량 삭제. 서버에 있는 그 차의 배차·영수증(파일 포함)·inbox·QR·연결 코드를 함께 지운다.
     * 차 안의 운행 기록(Room)은 그대로 남고, 차는 다음 동기화부터 401 → 연결 안 됨으로 바뀐다.
     */
    @DeleteMapping("/{vehicleId}")
    fun delete(
        @RequestHeader("X-Admin-Token", required = false) token: String?,
        @PathVariable vehicleId: UUID,
    ): ResponseEntity<Unit> {
        requireVehicle(authorize(token), vehicleId)
        val keys = repo.deleteVehicle(vehicleId)
        keys.forEach { key -> runCatching { storage.delete(key) }.onFailure { log.warn("영수증 파일 삭제 실패 {}", key) } }
        return ResponseEntity.noContent().build()
    }

    private fun authorize(token: String?): UUID = auth.authorize(token)

    private fun requireVehicle(org: UUID, vehicleId: UUID) {
        if (!repo.exists(org, vehicleId)) throw ApiException(HttpStatus.NOT_FOUND, "VEHICLE_NOT_FOUND", "차량이 없습니다")
    }
}
