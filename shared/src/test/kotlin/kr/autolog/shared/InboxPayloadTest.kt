package kr.autolog.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class InboxPayloadTest {
    @Test
    fun `payload round-trips through shared Json`() {
        val original: InboxPayload = UsersPayload(listOf(UserDto("u1", "김운전", "영업", null)))
        val text = AutoLogJson.encodeToString(InboxPayload.serializer(), original)
        assertEquals(original, AutoLogJson.decodeFromString(InboxPayload.serializer(), text))
    }

    @Test
    fun `옛 서버의 기기 등록 응답에 번호판이 없어도 읽힌다`() {
        val old = """{"device_token":"t","vehicle_id":"v","org_id":"o"}"""

        val r = AutoLogJson.decodeFromString(DeviceRegisterResponse.serializer(), old)

        assertEquals(null, r.plateNo)
        assertEquals(
            "12가3456",
            AutoLogJson.decodeFromString(
                DeviceRegisterResponse.serializer(),
                AutoLogJson.encodeToString(DeviceRegisterResponse.serializer(), r.copy(plateNo = "12가3456")),
            ).plateNo,
        )
    }

    @Test
    fun `회사 메일 계정 편지는 계정이 있든 지웠든 왕복한다`() {
        val set: InboxPayload =
            MailAccountPayload(MailAccountDto("smtp.example.com", 465, "a@example.com", "pw", "회사", false))
        val cleared: InboxPayload = MailAccountPayload(null)

        listOf(set, cleared).forEach {
            val text = AutoLogJson.encodeToString(InboxPayload.serializer(), it)
            assertEquals(it, AutoLogJson.decodeFromString(InboxPayload.serializer(), text))
        }
    }

    @Test
    fun `메일 계정 문자열에는 비밀번호가 찍히지 않는다`() {
        val dto = MailAccountDto("smtp.example.com", username = "a@example.com", password = "secret-pw")

        assertEquals(false, dto.toString().contains("secret-pw"))
    }
}
