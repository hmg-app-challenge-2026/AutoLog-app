package kr.htaxlog.relay.receipt

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * 폰 카메라 페이지(web/camera)가 사진을 올리는 곳 (F-30, F-41). 로그인 없음, QR 1회 토큰만.
 * 카메라 페이지 자체(GET /r/{code})는 nginx가 정적 파일로 준다.
 */
@RestController
class ReceiptController(private val service: ReceiptService) {
    @PostMapping("/r/{vehicleCode}/receipt")
    fun upload(
        @PathVariable vehicleCode: String,
        @RequestParam("t", required = false) token: String?,
        @RequestPart("image") image: MultipartFile,
    ): ReceiptUploadResponse {
        val payload = service.handleUpload(
            vehicleCode,
            token,
            image.bytes,
            image.contentType ?: "application/octet-stream",
        )
        return ReceiptUploadResponse(payload.receiptId, payload.ocrStatus.name)
    }
}

/** 폰에는 결과 요약만 돌려준다. 추출값·썸네일 주소는 차 inbox로만 간다. */
data class ReceiptUploadResponse(val receiptId: String, val ocrStatus: String)
