package kr.autolog.vehicle.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kr.autolog.vehicle.core.ConnectionState
import kr.autolog.vehicle.core.QrResult
import kr.autolog.vehicle.core.RegisterResult
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeviceRepositoryTest {
    private val server = MockWebServer()
    private val store = MemoryDeviceStore()
    private lateinit var repo: DeviceRepository
    private lateinit var url: String

    @Before
    fun setUp() {
        server.start()
        url = server.url("/").toString()
        repo = DeviceRepository(store, RelayHttp(io = Dispatchers.Unconfined))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `연결 코드로 등록하면 토큰을 저장하고 번호판으로 차량을 표시한다`() = runTest {
        server.enqueue(
            json(
                200,
                """{"device_token":"tok","vehicle_id":"veh-1","org_id":"org-1","plate_no":"12가3456","model":"아이오닉 6"}""",
            ),
        )
        var synced = false
        repo.onRegistered = { synced = true }

        val r = repo.register("$url/", "k7mq2x")

        assertEquals(RegisterResult.Ok, r)
        val req = server.takeRequest()
        assertEquals("/v1/devices", req.path)
        assertEquals("""{"code":"K7MQ2X"}""", req.body.readUtf8())
        assertEquals("tok", store.record!!.token)
        assertEquals("12가3456 · 아이오닉 6", (repo.connection.value as ConnectionState.Registered).vehicleLabel)
        assertEquals("veh-1", repo.vehicleId)
        assertTrue(synced)
    }

    @Test
    fun `코드 형식이 틀리면 서버에 묻지 않는다`() = runTest {
        val r = repo.register(url, "12345O")

        assertTrue(r is RegisterResult.Invalid)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `서버가 코드를 거부하면 이유를 보여 준다`() = runTest {
        server.enqueue(json(400, """{"code":"CONNECT_CODE_INVALID","message":"..."}"""))

        val r = repo.register(url, "ABC234")

        assertEquals(RegisterResult.Invalid("연결 코드가 맞지 않거나 만료됐어요"), r)
        assertEquals(ConnectionState.NotRegistered, repo.connection.value)
    }

    @Test
    fun `서버에 닿지 못하면 네트워크를 확인하라고 한다`() = runTest {
        server.shutdown()

        val r = repo.register(url, "ABC234") as RegisterResult.Invalid

        assertTrue(r.reason.startsWith("서버에 연결하지 못했어요"))
    }

    @Test
    fun `서버 주소는 http로 시작해야 한다`() = runTest {
        assertTrue(repo.register("autolog.acme.co.kr", "ABC234") is RegisterResult.Invalid)
    }

    @Test
    fun `등록 뒤 호출에는 Bearer 토큰을 붙이고 QR 만료 시각을 읽는다`() = runTest {
        store.save(DeviceRecord(url.trimEnd('/'), "tok", "veh-1", "org-1"))
        server.enqueue(json(200, """{"url":"https://r.example/r/abcd?t=x","expires_at":"2026-11-09T10:10:00+09:00"}"""))

        val qr = repo.issueQr() as QrResult.Issued

        assertEquals("Bearer tok", server.takeRequest().getHeader("Authorization"))
        assertEquals(java.time.Instant.parse("2026-11-09T01:10:00Z").toEpochMilli(), qr.expiresAt)
    }

    @Test
    fun `서버가 꺼지면 연결 불가로 바뀌고 토큰이 회수되면 등록이 풀린다`() = runTest {
        store.save(DeviceRecord(url.trimEnd('/'), "tok", "veh-1", "org-1", plateNo = "12가3456"))
        server.enqueue(json(401, """{"code":"DEVICE_UNAUTHORIZED","message":"회수됨"}"""))

        repo.call { u, t -> inbox(u, t) }

        assertEquals(ConnectionState.NotRegistered, repo.connection.value)
        assertNull(store.record)
    }

    @Test
    fun `네트워크 실패는 Unreachable이고 로컬 기록은 그대로다`() = runTest {
        store.save(DeviceRecord(url.trimEnd('/'), "tok", "veh-1", "org-1", plateNo = "12가3456"))
        server.shutdown()

        repo.call { u, t -> inbox(u, t) }

        assertTrue(repo.connection.value is ConnectionState.Unreachable)
        assertEquals("tok", store.record!!.token)
    }

    @Test
    fun `토큰은 로그 문자열에 나오지 않는다`() {
        assertTrue("tok-secret" !in DeviceRecord("https://a", "tok-secret", "v", "o").toString())
    }

    @Test
    fun `주소 변환은 기기 토큰으로 위도·경도를 묻고 응답을 읽는다`() = runTest {
        store.save(DeviceRecord(url.trimEnd('/'), "tok", "veh-1", "org-1"))
        server.enqueue(json(200, """{"address":"서울 서대문구 연희동 연희로 11-1","place_name":"연희빌딩"}"""))

        val r = repo.call { u, t -> reverseGeocode(u, t, 37.5665, 126.978) }

        val req = server.takeRequest()
        assertEquals("/v1/geo/reverse?lat=37.5665&lng=126.978", req.path)
        assertEquals("Bearer tok", req.getHeader("Authorization"))
        assertEquals("연희빌딩", (r as RelayResult.Ok).value.placeName)
    }

    @Test
    fun `주소 변환이 꺼진 서버(503)는 오류 코드를 돌려주고 연결 상태는 그대로다`() = runTest {
        store.save(DeviceRecord(url.trimEnd('/'), "tok", "veh-1", "org-1"))
        server.enqueue(json(503, """{"code":"GEOCODER_DISABLED","message":"off"}"""))

        val r = repo.call { u, t -> reverseGeocode(u, t, 37.0, 127.0) }

        assertEquals("GEOCODER_DISABLED", (r as RelayResult.Failure).code)
        assertTrue(store.record != null)
    }

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setBody(body)
        .setHeader("Content-Type", "application/json")
}

class MemoryDeviceStore : DeviceStore {
    var record: DeviceRecord? = null

    override fun load() = record

    override fun save(record: DeviceRecord) {
        this.record = record
    }

    override fun clear() {
        record = null
    }
}
