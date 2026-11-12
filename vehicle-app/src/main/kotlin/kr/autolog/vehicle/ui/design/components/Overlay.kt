package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalLeaving
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.verticalScrollFade

/** 지금 떠 있는 모달 수. 운전자 배지 등 화면 위 요소는 0이 아니면 숨는다(모달이 화면 전체를 덮어야 하므로). */
val LocalModalCount = compositionLocalOf<MutableIntState?> { null }

/**
 * 디자인 언어 11장 ModalCard — 앱의 **유일한 팝업 모양**. 화면 가운데 둥근 카드(`radius.xl`) + scrim.
 * 아래에 붙는 시트는 쓰지 않는다(2026-11-09 사용자 결정: 옛 안드로이드 앱처럼 보였다).
 * - 면: [kr.autolog.vehicle.ui.design.AutoLogColors.surfaceRaised] + 머리카락 테두리. 그림자는 쓰지 않는다(평면 — 테슬라식,
 *   scrim과 테두리만으로 떠 있는 면이 읽힌다. 그림자는 렌더러마다 다르게 그려졌다).
 * - 열림·닫힘: [ModalHost]가 정한 진행도([LocalModalPresence])로 scrim 투명도, 카드 투명도 + 배율(열림 0.92→1, 닫힘 1→0.96).
 *   닫히는 동안에는 카드 안을 누를 수 없다. **팝업은 모두 [ModalHost]로 감싼다** — 밖에서 그리면 움직이지 않는다.
 * - 키보드가 올라오면 카드가 그 위로 올라가고(imePadding), 내용이 넘치면 카드 안에서 스크롤한다(가장자리 흐림).
 * - [footer]는 스크롤 밖 카드 하단에 고정된다 — 결정 버튼은 여기에 둔다(주 버튼은 스크롤해야 보이면 안 된다, 디자인 언어 7장).
 * - **닫기·취소는 모두 [onClose] → 카드 오른쪽 위 X([CloseButton])**. 하단 [취소]·[닫기] 버튼은 두지 않는다.
 *   닫을 수 없는 팝업(반드시 골라야 하는 결정)은 onClose를 주지 않는다. X가 닫기 이상의 뜻이면(놓친 운행의 "무시")
 *   [closeLabel]로 접근성 이름을 그 뜻으로 바꾼다.
 * - [title]을 주면 X와 같은 머리 줄 가운데에 놓인다(iOS 시트 배치). X가 없는 팝업은 내용 첫 줄에 [ModalTitle]을 둬도 된다.
 * - 바깥 탭은 뒤 화면으로 새지 않게 삼킨다(클릭 의미론 없음 — 정차 중 "눌리는 것" 검사에 걸리지 않는다). 닫기는 카드 안 버튼으로만.
 * 단계가 FULL에서 내려가면 Navigator가 Home만 남기고, Home은 정차·주행 중 팝업을 띄우지 않는다(D-31).
 */
@Composable
fun ModalCard(
    modifier: Modifier = Modifier,
    maxWidth: Dp = Size.dialogMaxWidth,
    title: String? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    closeLabel: String = stringResource(R.string.action_close),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AutoLogTheme.colors
    val motion = AutoLogTheme.motion
    val modal = LocalModalCount.current
    DisposableEffect(modal) {
        modal?.let { it.intValue += 1 }
        onDispose { modal?.let { it.intValue -= 1 } }
    }
    val presence = LocalModalPresence.current
    val p = presence.progress
    val fromScale = if (presence.closing) motion.modalExitScale else motion.modalEnterScale
    val shape = RoundedCornerShape(Radius.xl)
    // 닫히는 동안은 카드 안 버튼도 누를 수 없다(사라지는 팝업을 두 번 누르지 않게) — 모양은 그대로
    CompositionLocalProvider(LocalLeaving provides presence.closing) {
        Box(modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = p.coerceIn(0f, 1f) }.background(c.scrim))
            Box(Modifier.fillMaxSize().imePadding().padding(Space.s6), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = p.coerceIn(0f, 1f)
                            scaleX = fromScale + (1f - fromScale) * p
                            scaleY = scaleX
                        }.clip(shape)
                        .background(c.surfaceRaised)
                        .border(Size.hairline, c.outline, shape),
                ) {
                    // 머리 줄(스크롤 밖 고정): 제목은 가운데, X는 오른쪽 — 스크롤해도 항상 닿고 내용 위에 겹치지 않는다.
                    // 제목 좌우를 X 폭만큼 똑같이 비워 제목이 카드 정중앙에 남는다
                    val header = title != null || onClose != null
                    if (header) {
                        Box(
                            Modifier.fillMaxWidth().padding(
                                top = Space.s3,
                                start = Space.s3,
                                end = Space.s3,
                            ).heightIn(min = Size.touchMin),
                            contentAlignment = Alignment.Center,
                        ) {
                            title?.let { ModalTitle(it, Modifier.padding(horizontal = Size.touchMin)) }
                            onClose?.let { CloseButton(it, Modifier.align(Alignment.CenterEnd), label = closeLabel) }
                        }
                    }
                    val scroll = rememberScrollState()
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScrollFade(scroll, Space.s7)
                            .verticalScroll(scroll)
                            .padding(
                                start = Space.s6,
                                end = Space.s6,
                                bottom = Space.s6,
                                top = if (header) Space.s2 else Space.s6,
                            ),
                        content = content,
                    )
                    if (footer != null) {
                        Column(
                            Modifier.fillMaxWidth().padding(start = Space.s6, end = Space.s6, bottom = Space.s6),
                            content = footer,
                        )
                    }
                }
            }
        }
    }
}

/** 팝업 제목(`headline`). **모든 팝업의 제목·설명은 가운데 정렬**(D-21) — 정렬을 이 함수 한 곳에 묶는다. */
@Composable
fun ModalTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.fillMaxWidth(),
        style = AutoLogTheme.windowType.modalTitle,
        color = AutoLogTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
    )
}

/** 팝업 설명·요약 문구. 기본은 `body` + `textSecondary`, 강조가 필요하면 [style]·[color]만 바꾼다. 가운데 정렬. */
@Composable
fun ModalText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AutoLogTheme.type.body,
    color: Color = AutoLogTheme.colors.textSecondary,
) {
    Text(text, modifier.fillMaxWidth(), style = style, color = color, textAlign = TextAlign.Center)
}
