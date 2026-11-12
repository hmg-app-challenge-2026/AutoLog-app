package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalInputLocked
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.motion.FadeThrough
import kr.autolog.vehicle.ui.design.motion.PopVisibility
import kr.autolog.vehicle.ui.design.motion.animateSelectColor

/** 모든 버튼의 공통 몸체. 잠금(LocalInputLocked)이면 스스로 비활성(디자인 언어 11장). */
@Composable
private fun ButtonBody(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    height: Dp,
    container: Color,
    content: Color,
    disabledContainer: Color,
    enabled: Boolean,
    loading: Boolean = false,
    labelStyle: TextStyle = AutoLogTheme.type.label,
) {
    val colors = AutoLogTheme.colors
    // 잠금이면 눌리지 않을 뿐 아니라 비활성 색으로 보인다(누를 수 없음을 미리 알림)
    val active = enabled && !LocalInputLocked.current && !loading
    val shape = RoundedCornerShape(Radius.lg)
    // 활성·비활성·로딩이 바뀌면 바탕·글자 색이 교차한다(디자인 언어 8.2 선택)
    val face by animateSelectColor(if (active || loading) container else disabledContainer)
    // 로딩 중에도 글자를 투명하게 남겨 버튼 폭이 흔들리지 않게 한다
    val textColor by animateSelectColor(
        if (loading) {
            Color.Transparent
        } else if (active) {
            content
        } else {
            colors.textTertiary
        },
    )
    Box(
        modifier =
        modifier
            .heightIn(min = height)
            .defaultMinSize(minWidth = Size.touchMin)
            .tappable(onClick, enabled = enabled && !loading)
            .clip(shape)
            .background(face)
            .padding(horizontal = Space.s6),
        contentAlignment = Alignment.Center,
    ) { ButtonLabel(text, textColor, content, loading, style = labelStyle) }
}

@Composable
private fun ButtonLabel(
    text: String,
    color: Color,
    spinner: Color,
    loading: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = AutoLogTheme.type.label,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        // 같은 버튼의 이름이 바뀌면([운행기록부 만들기] → [보내기 · 2명]) 그 자리에서 교체된다
        FadeThrough(text, contentAlignment = Alignment.Center, label = "buttonLabel") {
            Text(text = it, style = style, color = color, textAlign = TextAlign.Center)
        }
        PopVisibility(loading) {
            CircularProgressIndicator(color = spinner, strokeWidth = Space.s1, modifier = Modifier.size(Size.icon))
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Size.buttonLg,
    loading: Boolean = false,
    enabled: Boolean = true,
    labelStyle: TextStyle = AutoLogTheme.type.label,
) {
    val c = AutoLogTheme.colors
    ButtonBody(text, onClick, modifier, height, c.accentFill, c.onAccent, c.surface3, enabled, loading, labelStyle)
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Size.buttonMd,
    enabled: Boolean = true,
) {
    val c = AutoLogTheme.colors
    // surface3: 시트(surface2) 위에서도 버튼 면이 보이게 한 단계 위 서피스
    ButtonBody(text, onClick, modifier, height, c.surface3, c.textPrimary, c.surface3, enabled)
}

@Composable
fun TertiaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = AutoLogTheme.colors
    ButtonBody(text, onClick, modifier, Size.touchMin, Color.Transparent, c.accent, Color.Transparent, enabled)
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = AutoLogTheme.colors
    ButtonBody(text, onClick, modifier, Size.buttonMd, c.dangerFill, c.onAccent, c.surface3, enabled)
}

/**
 * 닫기(X). 보이는 원은 `iconLg`(48 dp, `surface3`)지만 터치 영역은 `touchMin`(76 dp) — 팔을 들어 누르는 차 화면 기준.
 * 고르기만 하는 팝업(전체 직원 등)의 오른쪽 위에 둔다. 결정이 필요한 팝업은 X 대신 하단 버튼으로 끝낸다(디자인 언어 11장).
 */
@Composable
fun CloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.action_close),
) = RoundIconButton(Icons.Rounded.Close, label, onClick, modifier)

/**
 * 뒤로(<) — 보조 화면(주유·운행 기록·운행기록부 …) 머리 줄 왼쪽(D-30). `RoundIconButton`과 같은 모양.
 * 팝업은 뒤로가 아니라 오른쪽 위 닫기([CloseButton])로 끝낸다.
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.action_back),
) = RoundIconButton(Icons.Rounded.ArrowBackIosNew, label, onClick, modifier)

/**
 * 아이콘 하나짜리 동그란 버튼(닫기 X, 달 이동 ◀ ▶). 보이는 원 `iconLg`(48 dp, `surface3`), 터치 영역 `touchMin`(76 dp).
 * [label]은 접근성 이름(화면에 보이지 않음). 비활성이면 아이콘이 `textTertiary`로 흐려지고 눌리지 않는다.
 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = AutoLogTheme.colors
    Box(
        modifier
            .size(Size.touchMin)
            .tappable(onClick, enabled = enabled)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(Size.iconLg).clip(CircleShape).background(c.surface3), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (enabled) c.textSecondary else c.textTertiary,
                modifier = Modifier.size(Size.iconSm),
            )
        }
    }
}
