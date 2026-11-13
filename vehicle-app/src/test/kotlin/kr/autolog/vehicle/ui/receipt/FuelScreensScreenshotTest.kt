package kr.autolog.vehicle.ui.receipt

import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.autolog.vehicle.data.repository.TripSession
import kr.autolog.vehicle.fake.FakeReceiptApi
import kr.autolog.vehicle.screenshot.captureAll
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FuelScreensScreenshotTest {
    @Test
    fun qr() = captureAll("fuel_qr") {
        ReceiptQrScreen(
            ReceiptQrUiState(
                loading = false,
                url = "https://autolog.example/r/demo",
                expiresAt = 600_000,
                now = 19_000,
            ),
            onAction = {},
        )
    }

    /** 직접 입력, 빈 칸(D-38): 칸 목록 + "결제 금액을 입력해 주세요" + 비활성 [추가하기]. 방금 끝난 운행이 기본 선택. */
    @Test
    fun manual() = captureAll("fuel_manual") {
        FuelEntryScreen(reduceManualEntry(TripSession.PendingReview("t-1")), onAction = {})
    }

    /** 직접 입력, 금액·주유량을 넣은 뒤 — 상태 줄 없음, [추가하기] 활성. */
    @Test
    fun manualFilled() = captureAll("fuel_manual_filled") {
        val state = reduceManualEntry(TripSession.PendingReview("t-1"))
        FuelEntryScreen(
            state.copy(values = mapOf(FuelField.AMOUNT to "68000", FuelField.LITERS to "40")),
            onAction = {},
        )
    }

    /** 영수증 요약(D-38): 금액 주인공 + 보조 두 줄(봐야 할 값만 주황) + 작은 사진 · 연결할 운행 · "주유 정보가 영수증과 일치하는지 확인해주세요" · [수정하기] [추가하기]. */
    @Test
    fun ocr() = captureAll("fuel_ocr") {
        FuelEntryScreen(reduceReceiptEntry(receipt, FakeReceiptApi.SAMPLE_CANDIDATES), onAction = {})
    }

    /** [수정하기] 뒤 칸 목록 — 봐야 할 칸(주유량·리터당 가격)이 먼저, 결제 금액은 크게, 확인됨 표시 없음. */
    @Test
    fun ocrDetails() = captureAll("fuel_ocr_details") {
        FuelEntryScreen(
            reduceReceiptEntry(receipt, FakeReceiptApi.SAMPLE_CANDIDATES).copy(detailsOpen = true),
            onAction = {},
        )
    }

    /** 영수증 값을 다 믿을 수 있고 후보가 하나 — 자동 선택, "금액 검산 통과", [추가하기] 1탭(스펙 7.5 자동). */
    @Test
    fun ocrSure() = captureAll("fuel_ocr_sure") {
        val sure = receipt.copy(confidence = receipt.confidence.mapValues { 0.99 })
        FuelEntryScreen(reduceReceiptEntry(sure, FakeReceiptApi.SAMPLE_CANDIDATES.take(1)), onAction = {})
    }

    /** 검산 실패 — 빨간 "…계산한 금액이 결제 금액과 달라요", [추가하기] 비활성. */
    @Test
    fun ocrArithFailed() = captureAll("fuel_ocr_arith_failed") {
        val unsure = FakeReceiptApi.SAMPLE_UNSURE.copy(thumbUrl = "")
        FuelEntryScreen(reduceReceiptEntry(unsure, FakeReceiptApi.SAMPLE_CANDIDATES), onAction = {})
    }

    /** 영수증을 못 읽음 — 처음부터 빈 칸 목록 + "영수증을 읽지 못했어요. 금액을 입력하면 저장할 수 있어요." */
    @Test
    fun ocrFailed() = captureAll("fuel_ocr_failed") {
        val failed = FakeReceiptApi.SAMPLE_FAILED.copy(thumbUrl = "")
        FuelEntryScreen(reduceReceiptEntry(failed, FakeReceiptApi.SAMPLE_CANDIDATES), onAction = {})
    }

    private val receipt = FakeReceiptApi.SAMPLE_RECEIPT.copy(thumbUrl = "")
}
