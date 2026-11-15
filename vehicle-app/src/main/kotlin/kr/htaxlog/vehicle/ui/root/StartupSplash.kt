package kr.htaxlog.vehicle.ui.root

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kr.htaxlog.vehicle.R
import kr.htaxlog.vehicle.ui.design.LightColors
import kr.htaxlog.vehicle.ui.design.Size

private const val SPLASH_DURATION_MS = 800L

/** 앱은 바로 조립해 운행 상태를 처리하고, 시작 창의 로고를 첫 Compose 프레임까지 이어서 보여 준다. */
@Composable
fun StartupSplash(launchStartedAtMillis: Long, onFinished: () -> Unit, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay((launchStartedAtMillis + SPLASH_DURATION_MS - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
        visible = false
        onFinished()
    }

    Box(Modifier.fillMaxSize()) {
        content()
        if (visible) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().background(LightColors.surface1).pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                val logoSize = (minOf(maxWidth, maxHeight) * 0.64f).coerceAtMost(Size.splashLogoMax)
                Image(
                    painter = painterResource(R.drawable.htaxlog_splash),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(logoSize),
                )
            }
        }
    }
}
