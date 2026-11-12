package kr.autolog.vehicle.ui.receipt

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.autolog.vehicle.data.repository.TripSession
import kr.autolog.vehicle.fake.FakeReceiptApi
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.WindowLayoutHost
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 주유 입력 창(D-38) — 넓은 창(두 칸) 기준. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w1024dp-h640dp")
class FuelEntryScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: FuelEntryUiState, actions: MutableList<FuelEntryAction> = mutableListOf()) =
        compose.setContent {
            AutoLogTheme { WindowLayoutHost { FuelEntryScreen(state, onAction = { actions += it }) } }
        }

    private val receipt = FakeReceiptApi.SAMPLE_RECEIPT.copy(thumbUrl = "")

    @Test
    fun `영수증 요약 — 금액이 주인공, 연결할 운행이 첫 화면에, 봐야 할 칸 안내, 확인됨 표시는 없다`() {
        show(reduceReceiptEntry(receipt, FakeReceiptApi.SAMPLE_CANDIDATES))

        compose.onNodeWithText("68,000 원").assertIsDisplayed()
        compose.onNodeWithText("연결할 운행").assertIsDisplayed()
        compose.onNodeWithText("09:12 본사 → 판교 거래처 · 주유 정차").assertIsDisplayed()
        compose.onNodeWithText("주유량·리터당 가격 확인이 필요해요").assertIsDisplayed()
        compose.onNodeWithText("확인됨").assertDoesNotExist()
    }

    @Test
    fun `수정하기를 누르면 칸 목록을 펼친다`() {
        val actions = mutableListOf<FuelEntryAction>()
        show(reduceReceiptEntry(receipt, FakeReceiptApi.SAMPLE_CANDIDATES), actions)

        compose.onNodeWithText("수정하기").performClick()

        assertEquals(listOf(FuelEntryAction.OpenDetails), actions)
    }

    @Test
    fun `칸 목록에서 칸을 누르면 그 칸을 고친다 — 봐야 할 칸엔 확인 필요`() {
        val actions = mutableListOf<FuelEntryAction>()
        show(reduceReceiptEntry(receipt, FakeReceiptApi.SAMPLE_CANDIDATES).copy(detailsOpen = true), actions)

        compose.onNodeWithText("수정하기").assertDoesNotExist()
        compose.onNodeWithText("주유량").performClick()

        assertEquals(listOf<FuelEntryAction>(FuelEntryAction.Edit(FuelField.LITERS)), actions)
    }

    @Test
    fun `직접 입력 — 빈 칸은 눌러서 입력, 금액이 없으면 안내와 비활성 추가하기`() {
        show(reduceManualEntry(TripSession.Idle))

        compose.onNodeWithText("결제 금액을 입력해 주세요").assertIsDisplayed()
        compose.onNodeWithText("추가하기").assertIsNotEnabled()
        compose.onNodeWithText("수정하기").assertDoesNotExist()
    }
}
