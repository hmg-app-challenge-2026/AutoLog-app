package kr.autolog.shared

/**
 * 주소 표기 규칙 (open-issues I-05). 운행기록부처럼 광역자치단체를 짧게 쓴다: `서울특별시` → `서울`, `경기도` → `경기`.
 * relay(카카오 결과)와 차량(내비 주소 문자열)이 같은 규칙을 쓰도록 여기에 둔다. 순수 함수.
 */
object KoreanAddress {
    private val FULL_TO_SHORT =
        mapOf(
            "서울특별시" to "서울",
            "부산광역시" to "부산",
            "대구광역시" to "대구",
            "인천광역시" to "인천",
            "광주광역시" to "광주",
            "대전광역시" to "대전",
            "울산광역시" to "울산",
            "세종특별자치시" to "세종",
            "경기도" to "경기",
            "강원도" to "강원",
            "강원특별자치도" to "강원",
            "충청북도" to "충북",
            "충청남도" to "충남",
            "전라북도" to "전북",
            "전북특별자치도" to "전북",
            "전라남도" to "전남",
            "경상북도" to "경북",
            "경상남도" to "경남",
            "제주특별자치도" to "제주",
        )

    /** 첫 단어가 광역자치단체 정식 이름이면 줄인 이름으로 바꾸고, 공백을 하나로 맞춘다. 빈 문자열이면 null. */
    fun shortenRegion(address: String?): String? {
        val words = address?.trim()?.split(WHITESPACE)?.filter { it.isNotEmpty() }.orEmpty()
        if (words.isEmpty()) return null
        val first = FULL_TO_SHORT[words.first()] ?: words.first()
        return (listOf(first) + words.drop(1)).joinToString(" ")
    }

    /** 비어 있지 않은 조각만 공백으로 잇는다. 모두 비면 null. */
    fun join(vararg parts: String?): String? =
        parts.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.joinToString(" ").takeIf { it.isNotEmpty() }

    private val WHITESPACE = Regex("\\s+")
}
