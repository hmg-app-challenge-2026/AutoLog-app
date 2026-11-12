package kr.autolog.vehicle.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 앱 어디에도 Asta Sans가 아닌 글씨체가 나오지 않게 지킨다(디자인 언어 6.1).
 * ① 우리 글자 토큰 ② Material 3 슬롯 15개 ③ 소스의 모든 Text 호출이 토큰 style을 쓰는지 ④ 글꼴을 따로 덮어쓰는 코드가 없는지.
 */
class FontCoverageTest {
    private val type = autoLogType()

    @Test
    fun `우리 글자 토큰은 모두 Asta Sans다`() {
        listOf(
            type.hero, type.heroSmall, type.heroCompact, type.display, type.headline,
            type.title, type.body, type.label, type.caption, type.section,
        ).forEach { assertEquals(AstaSans, it.fontFamily) }
    }

    @Test
    fun `Material 3 글자 슬롯 15개도 모두 Asta Sans다`() {
        val m = autoLogMaterialTypography(type)
        listOf(
            m.displayLarge, m.displayMedium, m.displaySmall, m.headlineLarge, m.headlineMedium, m.headlineSmall,
            m.titleLarge, m.titleMedium, m.titleSmall, m.bodyLarge, m.bodyMedium, m.bodySmall,
            m.labelLarge, m.labelMedium, m.labelSmall,
        ).forEach { assertEquals(AstaSans, it.fontFamily) }
    }

    @Test
    fun `모든 Text 호출은 style을 지정하고 글꼴을 따로 덮어쓰지 않는다`() {
        val sources =
            listOf("src/main", "src/demo", "src/vehicle")
                .map(::File)
                .filter { it.exists() }
                .flatMap { dir -> dir.walkTopDown().filter { it.extension == "kt" }.toList() }
        assertTrue("소스를 찾지 못했다 — 작업 디렉터리 확인", sources.isNotEmpty())
        val problems = mutableListOf<String>()
        val call = Regex("""(?<![\w.])(Text|BasicTextField|BasicText)\(""")
        for (f in sources) {
            val s = f.readText()
            for (m in call.findAll(s)) {
                if (s.substring(maxOf(0, m.range.first - 4), m.range.first) == "fun ") continue
                val args = argsOf(s, m.range.last + 1)
                if (Regex("""^\s*\w+\s*:""").containsMatchIn(args)) continue // 선언(파라미터 목록)
                if ("style" !in args) problems += "${f.path}:${s.lineAt(m.range.first)} style 없음"
            }
            if (f.name != "Type.kt") {
                Regex("""fontFamily\s*=|FontFamily\.|Typeface""").findAll(s).forEach {
                    problems += "${f.path}:${s.lineAt(it.range.first)} 글꼴 직접 지정"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun argsOf(s: String, start: Int): String {
        var depth = 1
        var i = start
        while (depth > 0 && i < s.length) {
            when (s[i]) {
                '(' -> depth++
                ')' -> depth--
            }
            i++
        }
        return s.substring(start, i - 1)
    }

    private fun String.lineAt(index: Int) = substring(0, index).count { it == '\n' } + 1
}
