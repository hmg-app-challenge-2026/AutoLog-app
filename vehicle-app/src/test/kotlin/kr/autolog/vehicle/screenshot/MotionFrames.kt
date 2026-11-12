package kr.autolog.vehicle.screenshot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.io.FileOutputStream

/** 모션 프레임 시각(ms) — 전환 시작부터. 스프링 smooth(응답 ≈ 0.35 s)가 끝나는 600 ms까지. */
val MotionFrameTimes = listOf(0L, 16L, 50L, 100L, 150L, 200L, 250L, 300L, 400L, 600L)

/**
 * WSL에서는 에뮬레이터를 못 돌리므로 움직임을 **이어 붙인 정지 프레임**으로 본다(모션 계획 6장).
 * [start]로 전환을 일으킨 뒤 [MotionFrameTimes]마다 화면을 찍어 가로로 늘어놓고, 각 칸 위에 시각을 적어
 * `src/test/screenshots/motion/<name>.png`로 저장한다(커밋하지 않음 — 리뷰어가 순서·방향·끝 모양을 본다).
 * 호출 전 `compose.mainClock.autoAdvance = false`. 시트는 `recordRoborazziDemoDebug`로 돌릴 때만 생긴다.
 */
fun ComposeContentTestRule.recordMotionFrames(name: String, scale: Int = 4, start: () -> Unit) {
    // 상태를 바꾼 뒤 바로 알린다 — 시계를 멈춘 테스트에서는 그래야 다음 프레임이 바뀐 값을 그린다
    runOnUiThread {
        start()
        Snapshot.sendApplyNotifications()
    }
    mainClock.advanceTimeByFrame() // 스택 변화가 구성에 반영된 첫 프레임 = 0 ms
    var at = 0L
    // captureToImage는 시계를 멈춘 상태에서 다시 그리기를 기다리다 멈춘다 — Roborazzi로 프레임마다 파일을 찍고 다시 읽는다
    val tmp = File("build/motion-frames/$name").apply { mkdirs() }
    val frames = MotionFrameTimes.map { t ->
        mainClock.advanceTimeBy(t - at)
        at = t
        val f = File(tmp, "$t.png")
        onRoot().captureRoboImage(f.path)
        t to BitmapFactory.decodeFile(f.path)
    }
    // Roborazzi는 기록 모드(recordRoborazzi*)에서만 파일을 쓴다 — 보통 테스트 실행에서는 시계만 돌리고 시트는 만들지 않는다
    if (frames.any { it.second == null }) return
    val w = frames.first().second!!.width / scale
    val h = frames.first().second!!.height / scale
    val label = h / 10
    val sheet = Bitmap.createBitmap(w * frames.size, h + label, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(sheet)
    canvas.drawColor(Color.WHITE)
    val paint = Paint().apply {
        color = Color.BLACK
        textSize = label * 0.7f
        isAntiAlias = true
    }
    frames.forEachIndexed { i, (t, bmp) ->
        canvas.drawText("${t}ms", i * w + 6f, label * 0.8f, paint)
        canvas.drawBitmap(bmp!!, null, Rect(i * w, label, (i + 1) * w, label + h), null)
    }
    val file = File("$SCREENSHOT_DIR/motion/$name.png")
    file.parentFile?.mkdirs()
    FileOutputStream(file).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
