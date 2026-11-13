package kr.autolog.relay

import kotlinx.serialization.builtins.ListSerializer
import kr.autolog.relay.receipt.OcrResult
import kr.autolog.relay.receipt.ReceiptOcr
import kr.autolog.relay.support.TEST_OPS_TOKEN
import kr.autolog.relay.support.adminSession
import kr.autolog.shared.AutoLogJson
import kr.autolog.shared.DeviceRegisterResponse
import kr.autolog.shared.InboxItem
import kr.autolog.shared.InboxKind
import kr.autolog.shared.QrTokenResponse
import kr.autolog.shared.ReceiptFields
import kr.autolog.shared.ReceiptPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.util.LinkedMultiValueMap
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URI
import javax.imageio.ImageIO

/** 폰 업로드 → MinIO 저장 → (가짜) OCR → receipts 기록 → 차 inbox → 썸네일 서명 URL 다운로드. Docker 없으면 건너뜀. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["autolog.ops.token=$TEST_OPS_TOKEN"],
)
@Testcontainers(disabledWithoutDocker = true)
class ReceiptUploadIntegrationTest {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @Container
        @JvmStatic
        val minio =
            MinIOContainer(DockerImageName.parse("chainguard/minio:latest").asCompatibleSubstituteFor("minio/minio"))

        @DynamicPropertySource
        @JvmStatic
        fun storage(r: DynamicPropertyRegistry) {
            r.add("autolog.storage.endpoint") { minio.s3URL }
            r.add("autolog.storage.public-endpoint") { minio.s3URL }
            r.add("autolog.storage.access-key") { minio.userName }
            r.add("autolog.storage.secret-key") { minio.password }
        }
    }

    @TestConfiguration
    class FakeOcr {
        @Bean
        @Primary
        fun ocr() = ReceiptOcr { _, _ ->
            OcrResult(
                ReceiptFields("SK 판교주유소", null, "휘발유", 40.0, 1700, 0, 68_000, "1234"),
                mapOf(
                    "amountWon" to 0.98,
                ),
            )
        }
    }

    @Autowired lateinit var http: TestRestTemplate

    private val admin by lazy {
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Admin-Token", http.adminSession("영수증 테스트 회사", "receipt-admin@test.example"))
        }
    }

    private fun bearer(token: String) = HttpEntity<Void>(HttpHeaders().apply { setBearerAuth(token) })

    private fun jpeg(): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(2400, 1200, BufferedImage.TYPE_INT_RGB), "jpg", it)
    }.toByteArray()

    @Test
    fun `폰에서 올린 영수증이 차 inbox에 OCR 결과와 썸네일 주소로 도착한다`() {
        val vehicleId = http.postForEntity(
            "/admin/vehicles",
            HttpEntity("""{"plateNo":"12가3456"}""", admin),
            Map::class.java,
        ).body!!["vehicleId"]
        val code = http.postForEntity(
            "/admin/vehicles/$vehicleId/connect-code",
            HttpEntity("", admin),
            Map::class.java,
        ).body!!["code"]
        val reg = http.postForEntity(
            "/v1/devices",
            HttpEntity(
                """{"code":"$code"}""",
                HttpHeaders().apply {
                    contentType =
                        MediaType.APPLICATION_JSON
                },
            ),
            String::class.java,
        )
        val device = AutoLogJson.decodeFromString(DeviceRegisterResponse.serializer(), reg.body!!)
        val qr = AutoLogJson.decodeFromString(
            QrTokenResponse.serializer(),
            http.exchange("/v1/qr-tokens", HttpMethod.POST, bearer(device.deviceToken), String::class.java).body!!,
        )
        val path = URI(qr.url).let { "${it.path}/receipt?${it.query}" }

        // 폰: multipart 업로드
        val image = jpeg()
        val form = LinkedMultiValueMap<String, Any>().apply {
            add(
                "image",
                HttpEntity(
                    object : ByteArrayResource(image) {
                        override fun getFilename() = "r.jpg"
                    },
                    HttpHeaders().apply {
                        contentType =
                            MediaType.IMAGE_JPEG
                    },
                ),
            )
        }
        val upload = http.postForEntity(
            path,
            HttpEntity(
                form,
                HttpHeaders().apply {
                    contentType =
                        MediaType.MULTIPART_FORM_DATA
                },
            ),
            Map::class.java,
        )
        assertEquals(HttpStatus.OK, upload.statusCode)
        assertEquals("OK", upload.body!!["ocrStatus"])

        // 같은 QR로 다시 올리면 401 (1회용)
        assertEquals(
            HttpStatus.UNAUTHORIZED,
            http.postForEntity(
                path,
                HttpEntity(
                    form,
                    HttpHeaders().apply {
                        contentType =
                            MediaType.MULTIPART_FORM_DATA
                    },
                ),
                String::class.java,
            ).statusCode,
        )

        // 차: inbox에서 RECEIPT 수신 → 썸네일 다운로드(긴 변 1600px)
        val items = AutoLogJson.decodeFromString(
            ListSerializer(InboxItem.serializer()),
            http.exchange("/v1/inbox", HttpMethod.GET, bearer(device.deviceToken), String::class.java).body!!,
        )
        val receipt = items.single { it.kind == InboxKind.RECEIPT }.payload as ReceiptPayload
        assertEquals(68_000, receipt.fields.amountWon)
        assertEquals(true, receipt.arithOk)
        val thumb = ImageIO.read(URI(receipt.thumbUrl).toURL())
        assertEquals(1600, thumb.width)
    }

    @Test
    fun `15MB보다 큰 사진은 413과 RECEIPT_TOO_LARGE`() {
        val big = ByteArray(15 * 1024 * 1024 + 512 * 1024)
        val form = LinkedMultiValueMap<String, Any>().apply {
            add(
                "image",
                HttpEntity(
                    object : ByteArrayResource(big) {
                        override fun getFilename() = "big.jpg"
                    },
                    HttpHeaders().apply { contentType = MediaType.IMAGE_JPEG },
                ),
            )
        }

        val res = http.postForEntity(
            "/r/anycar/receipt?t=x",
            HttpEntity(form, HttpHeaders().apply { contentType = MediaType.MULTIPART_FORM_DATA }),
            Map::class.java,
        )

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, res.statusCode)
        assertEquals("RECEIPT_TOO_LARGE", res.body!!["code"])
    }
}
