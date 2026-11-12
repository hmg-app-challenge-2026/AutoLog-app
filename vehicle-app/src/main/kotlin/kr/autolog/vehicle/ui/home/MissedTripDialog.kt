package kr.autolog.vehicle.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.components.ModalCard
import kr.autolog.vehicle.ui.design.components.ModalText
import kr.autolog.vehicle.ui.design.components.PrimaryButton

/**
 * 기능명세 F-10 놓친 운행 — 앱이 꺼진 동안 계기판 거리가 늘었을 때 대기 상태 Home에서 **한 번** 묻는다(D-26).
 * 앱 안 알림이 없으므로 띠 대신 팝업이다. [추가하기] → 거리 = 차이, 목적은 비어 있다(운행 기록 조회에서 입력).
 * 오른쪽 위 X = 무시(접근성 이름도 "무시").
 */
@Composable
fun MissedTripDialog(km: String, onAdd: () -> Unit, onIgnore: () -> Unit) {
    ModalCard(
        title = stringResource(R.string.missed_title),
        onClose = onIgnore,
        closeLabel = stringResource(R.string.missed_ignore),
        footer = {
            PrimaryButton(stringResource(R.string.missed_add), onClick = onAdd, modifier = Modifier.fillMaxWidth())
        },
    ) {
        ModalText(stringResource(R.string.missed_body, km), style = AutoLogTheme.type.title)
    }
}
