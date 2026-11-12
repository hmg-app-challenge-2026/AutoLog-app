package kr.autolog.vehicle.overlay

import kr.autolog.vehicle.core.LiveTrip
import kr.autolog.vehicle.core.SessionState
import kr.autolog.vehicle.core.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 운행 기록 알약(스펙 6.11): 무엇을 보이고 언제 띄우는가. */
class RecordingPillContentTest {
    private val trip = LiveTrip("t-1", startTime = 0L, 18_420.5, "판교", "u-1")
    private val recording = SessionState.Recording(trip, liveKm = 12.34, lastSavedAt = 0L)

    @Test
    fun `기록 중이면 거리와 지난 분을 보인다`() {
        assertEquals(RecordingPillContent("12.3", 15), recordingPillContent(recording, nowMs = 15 * 60_000L + 59_000L))
    }

    @Test
    fun `계기판을 못 읽으면 거리는 비운다 — 추정하지 않는다`() {
        assertNull(recordingPillContent(recording.copy(liveKm = null), 0L)!!.km)
    }

    @Test
    fun `종료 대기·대기 중이면 알약이 없다`() {
        val stop = SessionState.StopCandidate(trip, 1.0, StopReason.GEAR_P, since = 0L, deadlineAt = 5_000L)
        assertNull(recordingPillContent(stop, 0L))
        assertNull(recordingPillContent(SessionState.Idle, 0L))
    }

    @Test
    fun `AutoLog 화면이 보이는 동안은 띄우지 않고, 다른 앱이 앞에 있을 때만 띄운다`() {
        val content = recordingPillContent(recording, 0L)

        assertFalse(shouldShowRecordingPill(content, appVisible = true, allowed = true))
        assertTrue(shouldShowRecordingPill(content, appVisible = false, allowed = true))
    }

    @Test
    fun `권한이 없거나 기록 중이 아니면 띄우지 않는다`() {
        assertFalse(shouldShowRecordingPill(recordingPillContent(recording, 0L), appVisible = false, allowed = false))
        assertFalse(shouldShowRecordingPill(null, appVisible = false, allowed = true))
    }
}
