package kr.htaxlog.relay.admin

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/*
 * 운영 웹 기준 정보 API (F-51~54, 기술명세 4.3). 화면은 프론트 담당.
 * 인증은 X-Admin-Token([AdminAuth]). 바꾸면 차 inbox로 배포된다(F-43).
 * CSV 업로드: POST /admin/{users|places|schedules|recipients}/import
 *   - multipart/form-data 의 file 파트, 또는 본문 그대로(text/csv). UTF-8·CP949 모두 읽는다.
 *   - 한 줄이라도 틀리면 아무것도 저장하지 않고 400 IMPORT_INVALID + 줄별 오류.
 */

private const val TOKEN = "X-Admin-Token"

@RestController
@RequestMapping("/admin/users")
class AdminUserController(private val auth: AdminAuth, private val service: AdminReferenceService) {
    @GetMapping
    fun list(@RequestHeader(TOKEN, required = false) t: String?) = service.users(auth.authorize(t))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestHeader(TOKEN, required = false) t: String?, @RequestBody body: UserRequest) =
        service.createUser(auth.authorize(t), body)

    @PatchMapping("/{id}")
    fun update(
        @RequestHeader(TOKEN, required = false) t: String?,
        @PathVariable id: UUID,
        @RequestBody body: UserRequest,
    ) = service.updateUser(auth.authorize(t), id, body)

    @DeleteMapping("/{id}")
    fun delete(@RequestHeader(TOKEN, required = false) t: String?, @PathVariable id: UUID): ResponseEntity<Unit> {
        service.deleteUser(auth.authorize(t), id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importFile(@RequestHeader(TOKEN, required = false) t: String?, @RequestPart("file") file: MultipartFile) =
        service.importUsers(auth.authorize(t), file.bytes)

    @PostMapping("/import", consumes = [CSV])
    fun importBody(@RequestHeader(TOKEN, required = false) t: String?, @RequestBody csv: ByteArray) =
        service.importUsers(auth.authorize(t), csv)
}

@RestController
@RequestMapping("/admin/places")
class AdminPlaceController(private val auth: AdminAuth, private val service: AdminReferenceService) {
    @GetMapping
    fun list(@RequestHeader(TOKEN, required = false) t: String?) = service.places(auth.authorize(t))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestHeader(TOKEN, required = false) t: String?, @RequestBody body: PlaceRequest) =
        service.createPlace(auth.authorize(t), body)

    @PatchMapping("/{id}")
    fun update(
        @RequestHeader(TOKEN, required = false) t: String?,
        @PathVariable id: UUID,
        @RequestBody body: PlaceRequest,
    ) = service.updatePlace(auth.authorize(t), id, body)

    @DeleteMapping("/{id}")
    fun delete(@RequestHeader(TOKEN, required = false) t: String?, @PathVariable id: UUID): ResponseEntity<Unit> {
        service.deletePlace(auth.authorize(t), id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importFile(@RequestHeader(TOKEN, required = false) t: String?, @RequestPart("file") file: MultipartFile) =
        service.importPlaces(auth.authorize(t), file.bytes)

    @PostMapping("/import", consumes = [CSV])
    fun importBody(@RequestHeader(TOKEN, required = false) t: String?, @RequestBody csv: ByteArray) =
        service.importPlaces(auth.authorize(t), csv)
}

@RestController
@RequestMapping("/admin/schedules")
class AdminScheduleController(private val auth: AdminAuth, private val service: AdminReferenceService) {
    /** 주간 캘린더: ?vehicleId=&from=2026-11-09T00:00:00+09:00&to=… */
    @GetMapping
    fun list(
        @RequestHeader(TOKEN, required = false) t: String?,
        @RequestParam(required = false) vehicleId: UUID?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
    ) = service.schedules(auth.authorize(t), vehicleId, from, to)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestHeader(TOKEN, required = false) t: String?, @RequestBody body: ScheduleRequest) =
        service.createSchedule(auth.authorize(t), body)

    @PatchMapping("/{id}")
    fun update(
        @RequestHeader(TOKEN, required = false) t: String?,
        @PathVariable id: UUID,
        @RequestBody body: ScheduleRequest,
    ) = service.updateSchedule(auth.authorize(t), id, body)

    @DeleteMapping("/{id}")
    fun delete(@RequestHeader(TOKEN, required = false) t: String?, @PathVariable id: UUID): ResponseEntity<Unit> {
        service.deleteSchedule(auth.authorize(t), id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importFile(@RequestHeader(TOKEN, required = false) t: String?, @RequestPart("file") file: MultipartFile) =
        service.importSchedules(auth.authorize(t), file.bytes)

    @PostMapping("/import", consumes = [CSV])
    fun importBody(@RequestHeader(TOKEN, required = false) t: String?, @RequestBody csv: ByteArray) =
        service.importSchedules(auth.authorize(t), csv)
}

@RestController
@RequestMapping("/admin/recipients")
class AdminRecipientController(private val auth: AdminAuth, private val service: AdminReferenceService) {
    @GetMapping
    fun list(@RequestHeader(TOKEN, required = false) t: String?) = service.recipients(auth.authorize(t))

    @PostMapping
    fun add(
        @RequestHeader(TOKEN, required = false) t: String?,
        @RequestBody body: RecipientRequest,
    ): ResponseEntity<Unit> {
        service.addRecipient(auth.authorize(t), body)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @DeleteMapping("/{id}")
    fun delete(@RequestHeader(TOKEN, required = false) t: String?, @PathVariable id: Long): ResponseEntity<Unit> {
        service.deleteRecipient(auth.authorize(t), id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importFile(@RequestHeader(TOKEN, required = false) t: String?, @RequestPart("file") file: MultipartFile) =
        service.importRecipients(auth.authorize(t), file.bytes)

    @PostMapping("/import", consumes = [CSV])
    fun importBody(@RequestHeader(TOKEN, required = false) t: String?, @RequestBody csv: ByteArray) =
        service.importRecipients(auth.authorize(t), csv)
}

data class ImportErrorBody(val code: String, val message: String, val errors: List<ImportError>)

@RestControllerAdvice
class ImportErrorHandler {
    @ExceptionHandler(ImportFailed::class)
    fun handle(e: ImportFailed): ResponseEntity<ImportErrorBody> = ResponseEntity
        .badRequest()
        .body(ImportErrorBody("IMPORT_INVALID", "CSV ${e.errors.size}줄에 오류가 있어 저장하지 않았습니다", e.errors))
}

private const val CSV = "text/csv"
