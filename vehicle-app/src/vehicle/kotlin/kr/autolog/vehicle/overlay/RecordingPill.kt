package kr.autolog.vehicle.overlay

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kr.autolog.vehicle.R
import kr.autolog.vehicle.common.AppClock
import kr.autolog.vehicle.core.TripSessionApi
import kr.autolog.vehicle.ui.design.LightColors
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.autoLogType

/**
 * [RecordingOverlay] 실구현 — `TYPE_APPLICATION_OVERLAY` 창 하나(스펙 6.11). 기록 상태·거리는 앱 A 세션을 그대로 따르고,
 * 1초마다 경과 시간을 다시 그린다. 권한이 없거나 창을 못 띄워도 운행 기록과 앱 A의 운행 알림은 그대로 동작한다.
 * 모양은 라이트 토큰으로 고정(흰 면 · 초록 점 · 진한 글자) — 내비 지도 위에서 늘 같은 대비로 읽히게.
 * 실차 권한 확인 사항: `docs/integration/recording-overlay-permission.md`.
 */
class RecordingPill(private val app: Application, private val session: TripSessionApi, private val clock: AppClock) :
    RecordingOverlay {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var window: WindowManager? = null
    private var pill: View? = null
    private var label: TextView? = null
    private var windowBlocked = false
    private var appVisible = false
    private var appAreaCenterX: Int? = null

    override val supported = true

    init {
        scope.launch {
            while (isActive) {
                render()
                delay(TICK_MS)
            }
        }
    }

    override fun isAllowed(): Boolean = Settings.canDrawOverlays(app)

    override fun openSettings(): Boolean {
        if (isAllowed()) return true
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            // Android 11+는 package: URI를 무시하고 전체 앱 목록을 연다
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) data = Uri.parse("package:${app.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            app.startActivity(intent)
            true
        } catch (error: ActivityNotFoundException) {
            Log.w(TAG, "overlay permission screen unavailable; recording notification remains active", error)
            false
        } catch (error: SecurityException) {
            Log.w(TAG, "overlay permission screen denied; recording notification remains active", error)
            false
        }
    }

    override fun askPermissionOnce() {
        if (isAllowed() || prefs.getBoolean(KEY_ASKED, false)) return
        if (openSettings()) prefs.edit().putBoolean(KEY_ASKED, true).apply()
    }

    /**
     * 보이면 바로 숨기고, 안 보이게 되면 다음 틱(1초 안)에 띄운다 — 창 크기 변경으로 Activity가 다시 만들어질 때
     * stop → start 사이 몇 ms 동안 알약이 깜빡이지 않게.
     */
    override fun setAppVisible(visible: Boolean) {
        appVisible = visible
        if (visible) remove()
    }

    override fun setAppAreaCenter(centerXPx: Int) {
        if (appAreaCenterX == centerXPx) return
        appAreaCenterX = centerXPx
        val overlay = pill ?: return
        val params = overlay.layoutParams as? WindowManager.LayoutParams ?: return
        params.x = horizontalOffset(centerXPx)
        window?.updateViewLayout(overlay, params)
    }

    private fun render() {
        val content = recordingPillContent(session.state.value, clock.nowMs())
        if (!shouldShowRecordingPill(content, appVisible, isAllowed()) || content == null) {
            remove()
            return
        }
        if (windowBlocked || !ensureWindow()) return
        val text = app.getString(R.string.recording_pill_text, kmText(content), elapsedText(content.elapsedMin))
        label?.text = text
        pill?.contentDescription = app.getString(R.string.recording_pill_a11y, text)
    }

    /** 창이 없으면 만든다. 시스템이 막으면(권한 취소 직후 등) 이번 실행 동안은 다시 시도하지 않는다. */
    private fun ensureWindow(): Boolean {
        if (pill != null) return true
        val wm = app.getSystemService(WindowManager::class.java)
        val (view, textView) = createView()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            alpha = PILL_ALPHA
            x = horizontalOffset(appAreaCenterX)
            y = px(Space.s9)
        }
        return try {
            wm.addView(view, params)
            window = wm
            pill = view
            label = textView
            true
        } catch (error: RuntimeException) {
            windowBlocked = true
            Log.w(TAG, "overlay unavailable; recording notification remains active", error)
            false
        }
    }

    private fun remove() {
        pill?.let { view -> window?.removeView(view) }
        pill = null
        label = null
        window = null
    }

    private fun createView(): Pair<View, TextView> {
        val c = LightColors
        val row = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(Space.s5), px(Space.s4), px(Space.s5), px(Space.s4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = px(Radius.xl).toFloat() // 알약: 높이의 절반 이상
                setColor(c.surface1.toArgb())
                setStroke(px(Size.hairline), c.outline.toArgb())
            }
            elevation = px(Space.s2).toFloat()
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        row.addView(
            View(app).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c.success.toArgb())
                }
            },
            LinearLayout.LayoutParams(px(Size.statusDot), px(Size.statusDot)).apply { marginEnd = px(Space.s4) },
        )
        val text = TextView(app).apply {
            setTextColor(c.textPrimary.toArgb())
            textSize = autoLogType().caption.fontSize.value
            // 앱과 같은 Asta Sans(가변 글꼴) — 굵기는 wght 축으로(디자인 언어 6.1)
            typeface = ResourcesCompat.getFont(app, R.font.asta_sans)
            fontVariationSettings = "'wght' $PILL_WEIGHT"
            maxLines = 1
        }
        row.addView(text)
        return row to text
    }

    private fun kmText(content: RecordingPillContent): String =
        content.km?.let { app.getString(R.string.trip_km, it) } ?: app.getString(R.string.trip_km_unknown)

    private fun elapsedText(min: Long): String = if (min < MIN_PER_HOUR) {
        app.getString(R.string.trip_minutes, min.toInt())
    } else {
        app.getString(R.string.trip_hours_minutes, (min / MIN_PER_HOUR).toInt(), (min % MIN_PER_HOUR).toInt())
    }

    private fun px(dp: Dp): Int = (dp.value * app.resources.displayMetrics.density + 0.5f).toInt()

    /** 화면 가운데 기준 가로 이동. AutoLog 창 위치를 아직 모르면 Pleos 기본 배치(오른쪽 2/3 지점 — 앱 영역 가운데)로. */
    private fun horizontalOffset(centerX: Int?): Int {
        val screenWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            app.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds.width()
        } else {
            app.resources.displayMetrics.widthPixels
        }
        return (centerX ?: (screenWidth * 2 / 3)) - screenWidth / 2
    }

    private companion object {
        const val TAG = "AutoLog/pill"
        const val PREFS = "recording_pill"
        const val KEY_ASKED = "permission_requested"
        const val TICK_MS = 1_000L
        const val PILL_ALPHA = 0.8f
        const val PILL_WEIGHT = 700
        const val MIN_PER_HOUR = 60L
    }
}
