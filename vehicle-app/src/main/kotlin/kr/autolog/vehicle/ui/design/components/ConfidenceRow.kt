package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Radius
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space

/** OCR 신뢰도 3단계(스펙 7.5). 값이 없으면 MISSING. */
enum class ConfidenceLevel { OK, CHECK, RECHECK, MISSING }

/** 이 신뢰도 이상이면 "확인됨"(초록). 영수증 자동 조건(스펙 7.5 — 모든 칸 초록)도 같은 값을 쓴다. */
const val CONFIDENCE_OK = 0.95

/** 이 신뢰도 이상이면 "확인 필요"(노랑), 미만이면 "다시 확인"(빨강). */
const val CONFIDENCE_CHECK = 0.75

fun confidenceLevelOf(value: String?, confidence: Double?): ConfidenceLevel = when {
    value == null -> ConfidenceLevel.MISSING
    confidence == null || confidence >= CONFIDENCE_OK -> ConfidenceLevel.OK
    confidence >= CONFIDENCE_CHECK -> ConfidenceLevel.CHECK
    else -> ConfidenceLevel.RECHECK
}

/** 디자인 언어 11장 ConfidenceRow. 색만으로 전하지 않는다 — 아이콘 + 글자 + (노랑·빨강) 테두리. 탭하면 편집. */
@Composable
fun ConfidenceRow(
    label: String,
    value: String?,
    confidence: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AutoLogTheme.colors
    val t = AutoLogTheme.type
    val level = confidenceLevelOf(value, confidence)
    val (tone, icon, text) =
        when (level) {
            ConfidenceLevel.OK -> Triple(c.success, Icons.Rounded.CheckCircle, R.string.confidence_ok)
            ConfidenceLevel.CHECK -> Triple(c.warning, Icons.Rounded.ErrorOutline, R.string.confidence_check)
            ConfidenceLevel.RECHECK -> Triple(c.danger, Icons.Rounded.ReportProblem, R.string.confidence_recheck)
            ConfidenceLevel.MISSING -> Triple(c.danger, Icons.Rounded.ReportProblem, R.string.confidence_missing)
        }
    val shape = RoundedCornerShape(Radius.sm)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Size.touchMin)
            .tappable(onClick)
            .clip(shape)
            .background(c.surface1)
            .then(if (level == ConfidenceLevel.OK) Modifier else Modifier.border(Size.ring, tone, shape))
            .padding(horizontal = Space.s5, vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = t.caption, color = c.textSecondary)
            Text(value ?: "—", style = t.title, color = c.textPrimary, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(Size.icon))
            Text(stringResource(text), style = t.caption, color = tone)
        }
    }
}
