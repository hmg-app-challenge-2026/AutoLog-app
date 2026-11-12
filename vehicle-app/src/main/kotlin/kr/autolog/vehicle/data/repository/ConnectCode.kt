package kr.autolog.vehicle.data.repository

/**
 * 차량 연결 코드 규칙(F-01, F-40) — 운영 웹에서 발급한 6자리. 서버가 만드는 글자와 같아야 입력할 수 있다:
 * relay `Tokens.CODE_ALPHABET`("23456789ABCDEFGHJKLMNPQRSTUVWXYZ") · 앱 A `DeviceRepository.CODE`(`[2-9A-HJ-NP-Z]{6}`).
 * 헷갈리는 0·1·I·O는 쓰지 않는다. 백엔드 내부 패키지는 화면에서 import하지 않으므로 같은 값을 여기 둔다 —
 * 서버 규칙이 바뀌면 이 파일 하나만 고친다(`docs/integration/backend-link.md` G-01).
 */
object ConnectCode {
    const val LENGTH = 6
    const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    /** 입력할 수 있는 글자인가(소문자는 대문자로 본다). */
    fun accepts(key: Char): Boolean = key.uppercaseChar() in ALPHABET

    /**
     * 키보드로 친 글자를 코드로 — 소문자는 대문자로 바꾸고, 쓸 수 없는 글자(0·1·I·O·공백·기호·한글)는 버리고,
     * [LENGTH]자에서 자른다. 붙여 넣은 "7kq3-mz"도 "7KQ3MZ"가 된다.
     */
    fun normalize(input: String): String = input.uppercase().filter { it in ALPHABET }.take(LENGTH)

    fun isComplete(code: String): Boolean = code.length == LENGTH && code.all(::accepts)
}
