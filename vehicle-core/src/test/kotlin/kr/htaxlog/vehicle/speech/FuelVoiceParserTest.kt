package kr.htaxlog.vehicle.speech

import kr.htaxlog.vehicle.core.VoiceFuel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelVoiceParserTest {
    private fun p(s: String) = FuelVoiceParser.parse(s)

    @Test
    fun `명세 예시 6만8천원 40리터`() {
        assertEquals(VoiceFuel(68_000, 40.0), p("6만8천원 40리터"))
    }

    @Test
    fun `한글로 말해도 읽는다`() {
        assertEquals(VoiceFuel(70_000, 40.0), p("휘발유 칠만 원 사십 리터"))
        assertEquals(VoiceFuel(68_000, 40.5), p("육만 팔천 원 사십 점 오 리터 넣었어"))
    }

    @Test
    fun `리터당·단가 뒤의 원은 단가다`() {
        assertEquals(VoiceFuel(84_450, 50.0, 1_689), p("리터당 1,689원 50리터 84,450원"))
        assertEquals(VoiceFuel(null, 30.0, 1_700), p("단가 천칠백원 30L"))
    }

    @Test
    fun `금액만 말해도 된다`() {
        assertEquals(VoiceFuel(50_000, null), p("오만원어치"))
    }

    @Test
    fun `조사 이가 숫자 앞에 붙어도 금액을 읽는다`() {
        assertEquals(VoiceFuel(60_000, null), p("금액이 6만원"))
    }

    @Test
    fun `상식 밖의 값과 숫자 없는 말은 버린다`() {
        assertNull(p("주유했어요"))
        assertNull(p("5원"))
    }
}
