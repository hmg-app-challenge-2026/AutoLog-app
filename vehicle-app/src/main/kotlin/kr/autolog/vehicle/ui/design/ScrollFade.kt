package kr.autolog.vehicle.ui.design

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * 디자인 언어 9장: 스크롤 영역에 더 볼 내용이 있으면 그 가장자리를 [length]만큼 흐리게 한다.
 * 잘린 글자가 "깨진 화면"이 아니라 "아래에 더 있다"로 읽히게 하는 단서. 끝까지 스크롤하면 그쪽 흐림은 사라진다.
 * 스크롤 컨테이너 바깥(뷰포트 크기의 부모)에 붙인다.
 */
fun Modifier.verticalScrollFade(state: ScrollState, length: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = length.toPx().coerceAtMost(size.height / 2)
        if (state.canScrollBackward) {
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black), endY = edge),
                blendMode = BlendMode.DstIn,
            )
        }
        if (state.canScrollForward) {
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Black, Color.Transparent),
                    startY = size.height - edge,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }
