package kr.htaxlog.vehicle

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import kr.htaxlog.vehicle.core.DeepLink
import kr.htaxlog.vehicle.data.usage.AppReturnController
import kr.htaxlog.vehicle.di.AppGraph
import kr.htaxlog.vehicle.di.runtimePermissions
import kr.htaxlog.vehicle.nav.deepLinkRoute
import kr.htaxlog.vehicle.session.EXTRA_SCREEN_ON_BOOT
import kr.htaxlog.vehicle.ui.root.HTaxLogRoot
import kr.htaxlog.vehicle.ui.root.StartupSplash

/**
 * 앱의 유일한 Activity. 실제 앱은 그래프(= 백엔드 `HTaxLog.create`)를 만들기 전에 런타임 권한을 묻는다 — 권한이 있어야 좌표·알림이
 * 붙은 채로 조립된다(백엔드 `HTaxLog` 주석). 거절해도 그 기능만 빠지고 앱은 뜬다. 데모는 물을 권한이 없어 바로 뜬다.
 */
class MainActivity : ComponentActivity() {
    private val launchStartedAtMillis = SystemClock.elapsedRealtime()
    private val graph: AppGraph get() = (application as HTaxLogApp).graph

    /** 그래프를 만들고 화면을 띄웠는가. 그 전에 온 알림 Intent는 [pendingIntent]에 두었다가 띄울 때 적용한다. */
    private var started = false
    private var pendingIntent: Intent? = null
    private val appReturnController by lazy { AppReturnController(graph.appReturn) { moveTaskToBack(true) } }

    private val askPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 재생성(창 크기 변경 등)에는 같은 알림 Intent를 다시 적용하지 않는다
        if (savedInstanceState == null) pendingIntent = intent
        val missing = runtimePermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) start() else askPermissions.launch(missing.toTypedArray())
    }

    private fun start() {
        if (started) return
        started = true
        pendingIntent?.let(::openDeepLink)
        pendingIntent = null
        val appGraph = graph
        val screenOnBoot = intent.getBooleanExtra(EXTRA_SCREEN_ON_BOOT, false)
        setContent {
            StartupSplash(
                launchStartedAtMillis = launchStartedAtMillis,
                onFinished = { window.setBackgroundDrawableResource(R.color.window_bg) },
            ) {
                HTaxLogRoot(
                    graph = appGraph,
                    onRequestBackground = appReturnController::onMotionConfirmed,
                    deferBackgroundUntilMotionKnown = screenOnBoot,
                )
            }
        }
        // 이 화면이 보이는 동안은 알약을 숨기고 앱 A 알림을 조용히(스펙 6.11·D-37), 창이 놓인 앱 영역 가운데를 알려 준다
        appGraph.setAppVisible(true)
        window.decorView.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> reportAppArea(view) }
        window.decorView.post { reportAppArea(window.decorView) }
        appGraph.overlay.askPermissionOnce()
        // 런타임 권한 창에서 돌아온 뒤 start()가 호출되면 첫 onResume이 이미 지났을 수 있다.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            appReturnController.onResume(appGraph.usage.motion.value)
        }
    }

    // 그래프를 만들기 전(권한을 묻는 중)에는 건드리지 않는다 — 꺼내는 순간 백엔드가 조립된다
    override fun onStart() {
        super.onStart()
        if (started) graph.setAppVisible(true)
    }

    override fun onResume() {
        super.onResume()
        if (started) appReturnController.onResume(graph.usage.motion.value)
    }

    override fun onStop() {
        super.onStop()
        if (started) graph.setAppVisible(false)
    }

    private fun reportAppArea(view: View) {
        if (!view.isAttachedToWindow || view.width == 0) return
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        graph.overlay.setAppAreaCenter(location[0] + view.width / 2)
    }

    /** 앱이 떠 있을 때 알림을 누르면 여기로 온다(스펙 6.10). 아직 권한을 묻는 중이면 띄울 때 적용한다. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (started) openDeepLink(intent) else pendingIntent = intent
    }

    private fun openDeepLink(intent: Intent) {
        val route =
            deepLinkRoute(intent.getStringExtra(DeepLink.KEY_ROUTE), intent.getStringExtra(DeepLink.KEY_TRIP_ID))
        route?.let(graph.navigator::handleDeepLink)
    }
}
