package kr.autolog.vehicle.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.WindowLayoutHost
import kr.autolog.vehicle.ui.root.BadgeScaffold
import org.robolectric.RuntimeEnvironment

/**
 * 스크린샷 창 크기 — **실제 Pleos 화면 둘만**(2026-11-10 사용자 지시: 근거 없는 비율은 지운다). 둘 다 밀도 1.0(160 dpi)이라
 * 픽셀이 Pleos Connect v2.1.0 에뮬레이터 화면과 같다.
 * - [WIDE] 넓은 화면 = 앱 하나가 앱 영역 전체: 2560×1268 dp(디스플레이 2560×1440 px에서 상태 표시줄 76 px · 하단 바 96 px를 뺀 것,
 *   에뮬레이터 캡처로 잼 — V-01). 레이아웃 판정 WIDE.
 * - [NARROW] 좁은 화면 = 앱 둘의 1:1 분할 한 칸: 1280×1268 dp. Pleos Window Policy "앱 2개 실행 시 1:1(기본값)"에 앱 영역 폭을 반으로 나눈 값 —
 *   창 사이 경계 몇 dp는 아직 재지 않았다(V-01, 에뮬레이터에서 재면 바꾼다). 레이아웃 판정 NARROW(가로세로 ≈ 1.0).
 */
enum class ShotSize(val qualifier: String) {
    WIDE("w2560dp-h1268dp"),
    NARROW("w1280dp-h1268dp"),
}

/** 실제 앱처럼 오른쪽 위 운전자 배지를 얹을 때 — Home "대기 중"뿐(D-30). */
data class ShotDriver(val userId: String?, val name: String?)

/** 스크린샷 기록 위치(로컬 검토용, 커밋하지 않음 — CONVENTIONS 4장). */
const val SCREENSHOT_DIR = "src/test/screenshots"

/** 스크린샷 공통 운전자(배차된 김영업). */
val SampleDriver = ShotDriver("u-1", "김영업")

/**
 * 레이아웃 × 테마마다 한 장씩. 파일: `src/test/screenshots/<size>/<name>_<theme>.png` — 창 크기(wide·narrow)마다 폴더를 나눠
 * 같은 크기끼리 넘겨 보며 비교한다(2026-11-09 사용자 결정). [driver]가 있으면 실제 앱과 같은 [BadgeScaffold]로 감싼다.
 */
fun captureAll(
    name: String,
    sizes: List<ShotSize> = ShotSize.entries,
    driver: ShotDriver? = null,
    content: @Composable () -> Unit,
) {
    for (size in sizes) {
        for (dark in listOf(true, false)) {
            RuntimeEnvironment.setQualifiers("${size.qualifier}-${if (dark) "night" else "notnight"}-mdpi")
            val theme = if (dark) "dark" else "light"
            captureRoboImage("$SCREENSHOT_DIR/${size.name.lowercase()}/${name}_$theme.png") {
                // 반복 상태 모션(경로 화살표 등)은 멈춘 그림으로 — 무한 애니메이션은 캡처를 끝나지 않게 한다
                AutoLogTheme(darkTheme = dark, ambientMotion = false) {
                    Box(Modifier.fillMaxSize().background(AutoLogTheme.colors.bg)) {
                        WindowLayoutHost {
                            if (driver == null) {
                                content()
                            } else {
                                BadgeScaffold(driver.userId, driver.name, onClick = {}, content = content)
                            }
                        }
                    }
                }
            }
        }
    }
}
