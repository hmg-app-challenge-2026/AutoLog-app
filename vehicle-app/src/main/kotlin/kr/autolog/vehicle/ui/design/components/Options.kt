package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.motion.PopVisibility
import kr.autolog.vehicle.ui.design.motion.animateSelectColor
import kr.autolog.vehicle.ui.design.motion.animateSelectDp

/**
 * 디자인 언어 11장 ChoiceRow — 여러 개 중 **하나**를 고르는 선택형 행(운행 결과의 운행 목적, 주유 연결 운행).
 * 같은 폭의 행을 쌓아 한 묶음으로 읽히게 한다. 행 = `surface1` 면(`radius.md`) + 왼쪽 원 28 dp + 글자, 행 전체가 터치 대상(76 dp).
 * 선택: `accent` 테두리(3 dp) + 원 가운데 `accent` 점. 고르지 않은 행: `outline` 1 dp 테두리 — 면이 바탕에 묻히지 않게.
 * [onClick]이 null이면 **보기 전용**(누를 수 없을 뿐, 글자는 같은 색 — 목록이 또렷이 읽히게).
 */
@Composable
fun ChoiceRow(text: String, selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = AutoLogTheme.colors
    val ring by animateSelectColor(if (selected) c.accent else c.textTertiary)
    OptionRow(text, null, selected, onClick, Role.RadioButton, modifier) {
        Box(Modifier.size(Size.iconSm).border(Size.ring, ring, CircleShape), contentAlignment = Alignment.Center) {
            // 고르면 가운데 점이 "톡" 나타난다(디자인 언어 8.2 선택)
            PopVisibility(selected) { Box(Modifier.size(Size.statusDot).clip(CircleShape).background(c.accent)) }
        }
    }
}

/**
 * 디자인 언어 11장 CheckRow — 여러 개를 **함께** 고르는 체크 행(운행기록부 받는 사람). [ChoiceRow]와 같은 면·테두리·크기,
 * 표시만 네모 체크: 고름 = `accentFill` 네모 + 흰 ✓, 안 고름 = `textTertiary` 테두리 네모. [detail]은 이름 아래 보조 줄(메일 주소 등).
 */
@Composable
fun CheckRow(
    text: String,
    checked: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val c = AutoLogTheme.colors
    val box = RoundedCornerShape(Radius.sm)
    val fill by animateSelectColor(if (checked) c.accentFill else Color.Transparent)
    val edge by animateSelectColor(if (checked) c.accentFill else c.textTertiary)
    OptionRow(text, detail, checked, onClick, Role.Checkbox, modifier) {
        Box(
            Modifier.size(Size.iconSm).clip(box).background(fill).border(Size.ring, edge, box),
            contentAlignment = Alignment.Center,
        ) {
            PopVisibility(checked) { Icon(Icons.Rounded.Check, contentDescription = null, tint = c.onAccent) }
        }
    }
}

/** [ChoiceRow]·[CheckRow] 공용 틀: 면 · 테두리 · 터치 영역 · 왼쪽 표시 + 글자(+ 보조 줄). */
@Composable
private fun OptionRow(
    text: String,
    detail: String?,
    selected: Boolean,
    onClick: (() -> Unit)?,
    role: Role,
    modifier: Modifier,
    mark: @Composable () -> Unit,
) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    val shape = RoundedCornerShape(Radius.md)
    val edgeWidth by animateSelectDp(if (selected) Size.ring else Size.hairline)
    val edgeColor by animateSelectColor(if (selected) c.accent else c.outline)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = Size.touchMin)
            .semantics { this.selected = selected }
            .tappable(onClick, role = role),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .background(c.surface1)
                .border(edgeWidth, edgeColor, shape),
        )
        Row(
            Modifier.padding(horizontal = Space.s5),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            mark()
            if (detail == null) {
                Text(text, style = t.body, color = c.textPrimary)
            } else {
                Column(Modifier.padding(vertical = Space.s3)) {
                    Text(text, style = t.body, color = c.textPrimary)
                    Text(detail, style = t.caption, color = c.textSecondary)
                }
            }
        }
    }
}
