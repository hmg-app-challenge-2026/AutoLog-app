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
    val ops: Ops = Ops(),
    val connectCodeTtlHours: Long = 24,
    val qrTokenTtlMinutes: Long = 10,
    val inboxRetentionDays: Long = 30,
    val receiptRetentionDays: Long = 30,
    val gemini: Gemini = Gemini(),
    val storage: Storage = Storage(),
    val geocoder: GeocoderProps = GeocoderProps(),
    val mail: Mail = Mail(),
) {
    /** 회사 메일 앱 비밀번호 암호화 키(아무 길이의 비밀 문자열). 비어 있으면 메일 계정 저장을 거절한다. */
    data class Mail(val secretKey: String = "")

    /** 카카오 로컬 API. 주소 → 좌표(F-52 운영 웹)와 좌표 → 주소(F-06·F-07 운행, geo/). 키가 비어 있으면 둘 다 끈다. */
    data class GeocoderProps(val kakaoKey: String = "", val baseUrl: String = "https://dapi.kakao.com")

    /** 회사 관리자 로그인 세션 유지 시간, 서버 전체 회사 가입 상한(시간당). */
    data class Admin(val sessionHours: Long = 12, val signupsPerHour: Int = 30)

    /** 운영자 토큰(회사·관리자 계정 만들기, /ops). 비어 있으면 /ops 전부 403. */
    data class Ops(val token: String = "")

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
