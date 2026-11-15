package kr.htaxlog.relay.common

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException

/** API 오류. 응답은 `{ "code": "...", "message": "..." }` (CONVENTIONS 8장). */
class ApiException(val status: HttpStatus, val code: String, override val message: String) : RuntimeException(message)

data class ErrorBody(val code: String, val message: String)

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ApiException::class)
    fun handle(e: ApiException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(e.status).body(ErrorBody(e.code, e.message))

    /** multipart 한도(application.yml) 초과. Spring 기본 500 대신 413 + 공통 오류 형식. */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun tooLarge(e: MaxUploadSizeExceededException): ResponseEntity<ErrorBody> = ResponseEntity
        .status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(ErrorBody("RECEIPT_TOO_LARGE", "사진이 너무 큽니다 (최대 15MB)"))
}
