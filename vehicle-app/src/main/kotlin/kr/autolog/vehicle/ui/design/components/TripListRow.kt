package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.motion.ExpandVisibility

/**
 * 디자인 언어 11장 TripListRow — 운행 한 건 목록 줄(Home "최근 운행 기록", 이력). `surface1`, `radius.md`, 최소 높이 76 dp.
 * 위 [title](경로, `label`, 한 줄 말줄임) · 아래 [subtitle](날짜·거리·목적, `caption`, `textSecondary`).
 * 줄 전체가 터치 대상, 끝에 ›. 상태를 색으로 재촉하지 않는다 — 목적이 없으면 문장("목적 미입력")으로만 말한다(D-26).
 * [extra]는 아래 줄 밑에 붙는 내용(운행 기록 조회의 "운전자 지정" 후보처럼 그 줄에서 바로 끝낼 일). 그 안의 버튼은 줄 탭과 따로 눌린다.
 */
@Composable
fun TripListRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Size.touchMin)
            .tappable(onClick)
            .clip(RoundedCornerShape(Radius.md))
            .background(c.surface1)
            .padding(horizontal = Space.s5, vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
            Text(title, style = t.label, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = t.caption,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 그 줄에서 끝낼 일이 끝나면(운전자 지정) 접히며 사라진다
            var lastExtra by remember { mutableStateOf(extra) }
            if (extra != null) lastExtra = extra
            ExpandVisibility(extra != null) { Column { lastExtra?.invoke(this) } }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textTertiary)
    }
}
