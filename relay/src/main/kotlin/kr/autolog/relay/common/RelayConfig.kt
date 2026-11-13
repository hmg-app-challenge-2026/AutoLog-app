package kr.autolog.relay.common

import kr.autolog.shared.AutoLogJson
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.KotlinSerializationJsonHttpMessageConverter
import java.time.Clock

@ConfigurationProperties("autolog")
data class RelayProperties(
    val publicBaseUrl: String = "http://localhost",
    val admin: Admin = Admin(),
    val connectCodeTtlHours: Long = 24,
    val qrTokenTtlMinutes: Long = 10,
    val inboxRetentionDays: Long = 30,
    val receiptRetentionDays: Long = 30,
    val gemini: Gemini = Gemini(),
    val storage: Storage = Storage(),
    val geocoder: GeocoderProps = GeocoderProps(),
) {
    /** 카카오 로컬 API. 주소 → 좌표(F-52 운영 웹)와 좌표 → 주소(F-06·F-07 운행, geo/). 키가 비어 있으면 둘 다 끈다. */
    data class GeocoderProps(val kakaoKey: String = "", val baseUrl: String = "https://dapi.kakao.com")

    data class Admin(val token: String = "", val orgName: String = "데모 조직")

    data class Gemini(
        val apiKey: String = "",
        val model: String = "gemini-2.5-flash",
        val baseUrl: String = "https://generativelanguage.googleapis.com",
        val maxAttempts: Int = 3,
    )

    data class Storage(
        val endpoint: String = "http://localhost:9000",
        val publicEndpoint: String = "http://localhost:9000",
        val accessKey: String = "minio",
        val secretKey: String = "minio12345",
        val bucket: String = "receipts",
        val signedUrlDays: Int = 7,
    )
}

@Configuration
@EnableConfigurationProperties(RelayProperties::class)
class RelayConfig {
    /** 시각은 주입받는다. 테스트에서 고정 시계로 바꾸기 위해서다. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    /** shared 의 @Serializable DTO는 차량 앱과 같은 AutoLogJson 설정으로 직렬화한다. */
    @Bean
    fun kotlinSerializationConverter() = KotlinSerializationJsonHttpMessageConverter(AutoLogJson)
}
