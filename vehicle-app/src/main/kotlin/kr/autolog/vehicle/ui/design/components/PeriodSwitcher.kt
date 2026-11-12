package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.motion.DirectionalContent
import java.time.YearMonth

/**
 * 디자인 언어 11장 PeriodSwitcher — 달 이동 **‹ 2026년 11월 ›**(운행 기록 조회·운행기록부 내보내기가 같이 쓴다).
 * 화살표와 달이 **한 캡슐** 안에 있다: `surface1` 면 + `outline` 테두리, 끝이 둥근 알약(높이 76 dp). 화살표는 테두리 없는
 * 굵은 꺾쇠(`accent`) — 머리 줄 왼쪽의 동그란 뒤로 버튼(`BackButton`, 회색 원 + 얇은 <)과 모양·색이 달라 헷갈리지 않는다(2026-11-10).
 * 가운데 글자는 창 크기 역할 `periodTitle`. [canGoNext]가 false면 ›가 흐려지고 안 눌린다(미래 달 없음).
 */
@Composable
fun PeriodSwitcher(
    month: YearMonth,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AutoLogTheme.colors
    val pill = RoundedCornerShape(percent = 50)
    Row(
        modifier
            .heightIn(min = Size.touchMin)
            .clip(pill)
            .background(c.surface1)
            .border(Size.hairline, c.outline, pill),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepArrow(
            Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            stringResource(R.string.month_prev),
            onPrev,
            enabled = true,
        )
        // 누른 쪽으로 옛 달이 밀려나고 새 달이 반대편에서 들어온다(디자인 언어 8.2 방향 교체)
        DirectionalContent(month, direction = { from, to -> to.compareTo(from) }, label = "month") {
            Text(
                stringResource(R.string.period_month, it.year, it.monthValue),
                modifier = Modifier.padding(horizontal = Space.s2),
                style = AutoLogTheme.windowType.periodTitle,
                color = c.textPrimary,
            )
        }
        StepArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.month_next), onNext, canGoNext)
    }
}

/** 캡슐 안 화살표 하나: 테두리·바탕 없는 굵은 꺾쇠(`icon` 36 dp, `accent`), 터치 76 dp. 비활성은 `textTertiary`. */
@Composable
private fun StepArrow(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean) {
    Box(
        Modifier.size(Size.touchMin).tappable(onClick, enabled = enabled),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) AutoLogTheme.colors.accent else AutoLogTheme.colors.textTertiary,
            modifier = Modifier.size(Size.icon),
        )
    }
}
