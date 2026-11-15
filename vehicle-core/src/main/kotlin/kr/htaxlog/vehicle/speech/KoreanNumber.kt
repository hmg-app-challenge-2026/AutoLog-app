package kr.htaxlog.vehicle.speech

/**
 * STT가 돌려주는 숫자 표현을 값으로 바꾼다. 순수 함수.
 * "68000", "68,000", "6만8천", "육만 팔천", "칠만", "사십점오", "40.5", "만" 등.
 */
object KoreanNumber {
    private val DIGITS = mapOf(
        '영' to 0, '공' to 0, '일' to 1, '이' to 2, '삼' to 3, '사' to 4, '오' to 5, '육' to 6, '칠' to 7,
        '팔' to 8, '구' to 9,
    )
    private val SMALL_UNITS = mapOf('십' to 10L, '백' to 100L, '천' to 1_000L)
    private const val MAN = 10_000L

    fun isNumeralChar(c: Char): Boolean =
        c.isAsciiDigit() || c in DIGITS || c in SMALL_UNITS || c == '만' || c == '점' || c == '.' || c == ','

    fun parse(raw: String): Double? {
        val s = raw.filterNot { it == ',' || it.isWhitespace() }
        if (s.isEmpty()) return null
        val sep = s.indexOfFirst { it == '점' || it == '.' }
        val intPart = if (sep >= 0) s.substring(0, sep) else s
        val fracPart = if (sep >= 0) s.substring(sep + 1) else ""
        val whole = if (intPart.isEmpty()) 0L else parseInteger(intPart) ?: return null
        if (fracPart.isEmpty()) return whole.toDouble()
        val frac = fracPart.map { c -> if (c.isAsciiDigit()) c.digitToInt() else DIGITS[c] ?: return null }
        return "$whole.${frac.joinToString("")}".toDouble()
    }

    private fun parseInteger(s: String): Long? {
        var total = 0L
        var section = 0L
        var num = 0L
        var sawDigit = false
        for (c in s) {
            when {
                c.isAsciiDigit() -> {
                    num = num * 10 + c.digitToInt()
                    sawDigit = true
                }
                c in DIGITS -> {
                    num = DIGITS.getValue(c).toLong()
                    sawDigit = true
                }
                c in SMALL_UNITS -> {
                    section += (if (num == 0L) 1 else num) * SMALL_UNITS.getValue(c)
                    num = 0
                    sawDigit = true
                }
                c == '만' -> {
                    val chunk = section + num
                    total += (if (chunk == 0L) 1 else chunk) * MAN
                    section = 0
                    num = 0
                    sawDigit = true
                }
                else -> return null
            }
        }
        return if (sawDigit) total + section + num else null
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'
}
