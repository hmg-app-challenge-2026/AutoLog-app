package kr.autolog.vehicle.ui.receipt

import kr.autolog.vehicle.data.repository.ActiveTrip
import kr.autolog.vehicle.data.repository.HistoryItem
import kr.autolog.vehicle.data.repository.TripSession
import kr.autolog.vehicle.fake.FakeReceiptApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelEntryReducerTest {
    private val receipt = FakeReceiptApi.SAMPLE_RECEIPT
    private val oneCandidate = FakeReceiptApi.SAMPLE_CANDIDATES.take(1)

    @Test
    fun `직접 입력은 금액이 있어야 추가할 수 있다`() {
        assertFalse(FuelEntryUiState().canAdd)
        assertTrue(FuelEntryUiState(values = mapOf(FuelField.AMOUNT to "68000")).canAdd)
    }

    @Test
    fun `OCR 검산 실패는 수정 전 추가를 막고 값을 고치면 허용한다`() {
        val unsure = reduceReceiptEntry(FakeReceiptApi.SAMPLE_UNSURE, oneCandidate)
        assertFalse(unsure.canAdd)

        val corrected = unsure.copy(editing = FuelField.LITERS).deleteKey().deleteKey().deleteKey()
            .pressKey('4').pressKey('0')

        assertEquals("40", corrected[FuelField.LITERS])
        assertTrue(corrected.canAdd)
    }

    @Test
    fun `OCR 실패는 금액만 입력해도 추가할 수 있다`() {
        val state = reduceReceiptEntry(FakeReceiptApi.SAMPLE_FAILED, oneCandidate)
            .copy(editing = FuelField.AMOUNT).pressKey('5').pressKey('0')

        assertNull(state.arithOk)
        assertTrue(state.canAdd)
    }

    @Test
    fun `기록 중이면 지금 운행을 고른 채 직접 입력이 열린다`() {
        val state = reduceManualEntry(TripSession.Recording(ActiveTrip("t-now", 0, null), liveKm = 3.0))

        assertEquals(listOf(FuelTripOption("t-now", FuelTripLabel.Current)), state.trips)
        assertEquals("t-now", state.selectedTripId)
        assertEquals(listOf(FuelField.AMOUNT, FuelField.LITERS, FuelField.UNIT_PRICE), state.fields)
    }

    @Test
    fun `주유소에 주차해 결과가 저장된 뒤에도 30분 안에 끝난 운행은 방금 끝난 운행으로 고른 채 열린다`() {
        val last = HistoryItem("t-last", 0, endTime = 1_000_000, null, false, "본사", "주유소", 12.0, null, null)

        val soon = reduceManualEntry(TripSession.Idle, last, now = 1_000_000 + JUST_ENDED_WINDOW_MS)
        val late = reduceManualEntry(TripSession.Idle, last, now = 1_000_001 + JUST_ENDED_WINDOW_MS)

        assertEquals(listOf(FuelTripOption("t-last", FuelTripLabel.JustEnded)), soon.trips)
        assertEquals("t-last", soon.selectedTripId)
        assertTrue(late.trips.isEmpty())
    }

    @Test
    fun `운행이 없으면 연결 안 함으로 열린다`() {
        val state = reduceManualEntry(TripSession.Idle)

        assertTrue(state.trips.isEmpty())
        assertNull(state.selectedTripId)
    }

    @Test
    fun `모든 칸 확인됨 · 검산 통과 · 후보 하나면 그 후보를 고른 채로 연다`() {
        val sure = receipt.copy(confidence = receipt.confidence.mapValues { 0.99 })

        assertEquals("t-1", reduceReceiptEntry(sure, oneCandidate).selectedTripId)
        assertNull(reduceReceiptEntry(sure, FakeReceiptApi.SAMPLE_CANDIDATES).selectedTripId)
        assertNull(reduceReceiptEntry(receipt, oneCandidate).selectedTripId) // 리터 0.93 — 확인 필요
    }

    @Test
    fun `영수증 값은 칸으로 채워지고 저장할 때 그대로 돌아간다`() {
        val state = reduceReceiptEntry(receipt, oneCandidate)

        assertEquals("68000", state[FuelField.AMOUNT])
        assertEquals("40.0", state[FuelField.LITERS])
        assertEquals(receipt.fields, state.toReceiptFields())
    }

    @Test
    fun `숫자판은 숫자 칸에만 쓰이고 카드 칸은 숫자 네 자리만 남긴다`() {
        val base = reduceReceiptEntry(receipt, oneCandidate)

        assertEquals(base, base.copy(editing = FuelField.MERCHANT).pressKey('1').copy(editing = null))
        val card = base.copy(editing = FuelField.CARD_LAST4).typeText("98-76 54")
        assertEquals("9876", card[FuelField.CARD_LAST4])
    }

    @Test
    fun `사람이 고친 칸은 확인됨으로 본다`() {
        val state = reduceReceiptEntry(receipt, oneCandidate)
        assertEquals(0.93, state.confidence(FuelField.LITERS)!!, 0.0)

        val edited = state.copy(editing = FuelField.LITERS).pressKey('0')

        assertEquals(1.0, edited.confidence(FuelField.LITERS)!!, 0.0)
    }

    @Test
    fun `표시 값 — 원은 천 단위 쉼표, 일시는 짧게, 빈 칸은 없음`() {
        assertEquals("68,000", formatFuelValue(FuelField.AMOUNT, "68000"))
        assertEquals("40.0", formatFuelValue(FuelField.LITERS, "40.0"))
        assertEquals("11/2 09:20", formatFuelValue(FuelField.DATETIME, "2026-11-02T09:20:00+09:00"))
        assertNull(formatFuelValue(FuelField.MERCHANT, " "))
    }

    @Test
    fun `봐야 할 칸은 신뢰도가 확인 필요·다시 확인인 칸뿐 — 고치면 빠지고, 빈 칸은 경고가 아니다`() {
        val state = reduceReceiptEntry(receipt, oneCandidate)

        assertEquals(listOf(FuelField.LITERS, FuelField.UNIT_PRICE), state.needsCheck)
        assertEquals(listOf(FuelField.UNIT_PRICE), state.copy(edited = setOf(FuelField.LITERS)).needsCheck)
        assertTrue(reduceManualEntry(TripSession.Idle).needsCheck.isEmpty())
    }

    @Test
    fun `칸 목록은 처음 확인 필요였던 칸이 먼저 — 고친 뒤에도 자리를 지킨다`() {
        val state = reduceReceiptEntry(receipt, oneCandidate).copy(edited = setOf(FuelField.LITERS))

        assertEquals(
            listOf(
                FuelField.LITERS,
                FuelField.UNIT_PRICE,
                FuelField.MERCHANT,
                FuelField.DATETIME,
                FuelField.FUEL_TYPE,
                FuelField.AMOUNT,
                FuelField.DISCOUNT,
                FuelField.CARD_LAST4,
            ),
            state.listedFields,
        )
    }

    @Test
    fun `영수증은 요약으로 열리고 수정하기를 누르면 칸 목록 — 직접 입력과 금액을 못 읽은 영수증은 처음부터 칸 목록`() {
        val ocr = reduceReceiptEntry(receipt, oneCandidate)

        assertTrue(ocr.showsSummary)
        assertFalse(ocr.copy(detailsOpen = true).showsSummary)
        assertFalse(reduceManualEntry(TripSession.Idle).showsSummary)
        assertFalse(reduceReceiptEntry(FakeReceiptApi.SAMPLE_FAILED, oneCandidate).showsSummary)
    }

    @Test
    fun `상태 한 줄 — 검산 실패가 먼저, 금액이 없으면 입력 안내, 봐야 할 칸, 다 되면 검산 통과`() {
        assertEquals(FuelNotice.ArithFailed, reduceReceiptEntry(FakeReceiptApi.SAMPLE_UNSURE, oneCandidate).notice)
        assertEquals(FuelNotice.OcrFailed, reduceReceiptEntry(FakeReceiptApi.SAMPLE_FAILED, oneCandidate).notice)
        assertEquals(FuelNotice.AmountMissing, reduceManualEntry(TripSession.Idle).notice)
        assertEquals(
            FuelNotice.NeedsCheck(listOf(FuelField.LITERS, FuelField.UNIT_PRICE)),
            reduceReceiptEntry(receipt, oneCandidate).notice,
        )
        val sure = receipt.copy(confidence = receipt.confidence.mapValues { 0.99 })
        assertEquals(FuelNotice.ArithOk, reduceReceiptEntry(sure, oneCandidate).notice)
        val filled = reduceManualEntry(TripSession.Idle).copy(values = mapOf(FuelField.AMOUNT to "50000"))
        assertNull(filled.notice) // 직접 입력은 검산이 없다 — 금액만 있으면 할 일 없음
    }
}
