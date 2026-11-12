package kr.autolog.vehicle.ui.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.autolog.vehicle.di.LocalAppGraph
import kr.autolog.vehicle.ui.design.LocalTopEndReserved
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.components.DriverBadge
import kr.autolog.vehicle.ui.design.components.LocalModalCount
import kr.autolog.vehicle.ui.design.screenMargin

/** 실제 앱: 현재 운전자로 [BadgeScaffold]를 그린다. [visible]=false면 배지를 감춘다(기록 중 Home). */
@Composable
fun DriverBadgeScaffold(visible: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val graph = LocalAppGraph.current
    val driver by graph.profile.currentDriver().collectAsStateWithLifecycle(initialValue = null)
    BadgeScaffold(driver?.userId, driver?.name, onClick, visible, content)
}

/**
 * 화면 + 오른쪽 위 운전자 배지(넷플릭스식, 디자인 언어 11장). 모달(ModalCard)이 떠 있으면 배지를 숨긴다.
 * 스크린샷 테스트도 이 함수로 실제와 같은 자리에 그린다. [visible]=false면 배지도, 위 줄이 비워 둘 폭도 없다.
 */
@Composable
fun BadgeScaffold(
    userId: String?,
    name: String?,
    onClick: () -> Unit,
    visible: Boolean = true,
    content: @Composable () -> Unit,
) {
    val modal = remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // 배지는 화면 바깥 여백과 같은 자리 — 화면 콘텐츠의 오른쪽 위 모서리와 맞물린다
    val margin = screenMargin()
    // 배지 폭을 재서 화면 위 줄이 비워 둘 폭으로 알린다 — 이름 길이가 달라도 겹치지 않는다.
    // 화면 콘텐츠는 screenMargin()만큼 안쪽에서 끝나고 배지는 그보다 s3 바깥까지 나가므로, 콘텐츠 기준 예약 폭 = 배지 폭 - s3 + 간격
    var badgePx by remember { mutableIntStateOf(0) }
    val reserved = if (!visible || badgePx == 0) 0.dp else with(density) { badgePx.toDp() } - Space.s3 + TOP_END_GAP
    CompositionLocalProvider(LocalModalCount provides modal, LocalTopEndReserved provides reserved) {
        Box(Modifier.fillMaxSize()) {
            content()
            if (visible && modal.intValue == 0) {
                // 위: 화면 위쪽 줄(높이 76 dp)과 같은 줄. 오른쪽: 배지 안쪽 여백(s3)만큼 빼서 글자가 콘텐츠 여백선에 맞게
                Box(
                    Modifier.fillMaxSize().padding(top = margin, end = margin - Space.s3),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    DriverBadge(userId, name, onClick, Modifier.onSizeChanged { badgePx = it.width })
                }
            }
        }
    }
}

/** 위 줄 요소와 배지 사이 최소 간격. */
private val TOP_END_GAP = Space.s4
