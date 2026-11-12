package kr.autolog.vehicle.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.autolog.vehicle.core.AutoLogComponents
import kr.autolog.vehicle.data.repository.Outcome
import kr.autolog.vehicle.data.repository.ServerLink
import kr.autolog.vehicle.data.repository.TripSession
import kr.autolog.vehicle.data.upload.UnavailableUploadApi
import kr.autolog.vehicle.data.usage.StaticCarUxSource
import kr.autolog.vehicle.fake.FakeContext
import kr.autolog.vehicle.fake.FakeDeviceApi
import kr.autolog.vehicle.fake.FakeLocalStore
import kr.autolog.vehicle.fake.FakeMailApi
import kr.autolog.vehicle.fake.FakePurposeEngine
import kr.autolog.vehicle.fake.FakeReceiptApi
import kr.autolog.vehicle.fake.FakeReportApi
import kr.autolog.vehicle.fake.FakeSettingsApi
import kr.autolog.vehicle.fake.FakeTripSession
import kr.autolog.vehicle.fake.FakeVoiceApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

/**
 * 실제 앱 그래프가 `AutoLog.create`의 구성 요소(계약)를 화면 Repository에 제대로 잇는지 — 구성 요소 자리에 백엔드 `fake/`를 넣어
 * 실구현과 같은 계약 모양으로 확인한다(실구현은 SDK·Room이 필요해 JVM 테스트로는 못 돌린다).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealAppGraphTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    private val local = FakeLocalStore()
    private val components = AutoLogComponents(
        session = FakeTripSession(scope),
        local = local,
        report = FakeReportApi(local),
        device = FakeDeviceApi(registered = true),
        context = FakeContext(),
        purpose = FakePurposeEngine(),
        voice = FakeVoiceApi(),
        receipts = FakeReceiptApi(),
        mail = FakeMailApi(),
        settings = FakeSettingsApi(),
    )
    private val graph = RealAppGraph(components, StaticCarUxSource(), scope)

    @Test
    fun `세션·운행 기록·서버 연결은 구성 요소에서 그대로 온다`() = runTest {
        assertEquals(TripSession.Idle, graph.session.state.first())
        val month = YearMonth.now(ZoneId.of("Asia/Seoul"))
        assertEquals(local.trips(month).first().size, graph.trips.history(month).first().size)
        assertTrue(graph.settings.link.value is ServerLink.Linked)
    }

    @Test
    fun `사내 서버 업로드는 계약이 없어 꺼져 있고 지금 전송은 이유와 함께 실패한다 — 가짜 숫자를 보여 주지 않는다`() = runTest {
        val upload = graph.settings.upload.value
        assertFalse(upload.enabled)
        assertEquals(0, upload.pendingCount)
        assertEquals(Outcome.Failed(UnavailableUploadApi.REASON), graph.settings.uploadNow())
    }

    @Test
    fun `데모 패널이 없다`() {
        assertEquals(null, graph.demoOverlay)
    }
}
