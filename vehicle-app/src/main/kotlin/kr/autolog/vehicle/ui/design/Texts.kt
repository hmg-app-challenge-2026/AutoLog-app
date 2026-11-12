package kr.autolog.vehicle.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kr.autolog.vehicle.R

/** "5초" / "2분 41초" */
@Composable
fun durationText(sec: Int): String = when {
    sec < 60 -> stringResource(R.string.duration_sec, sec)
    else -> stringResource(R.string.duration_min_sec, sec / 60, sec % 60)
}

/** 시계 모양 남은 시간 "9:41"(QR 유효 시간처럼 분 단위로 오래 남는 값). */
@Composable
fun clockText(sec: Int): String = stringResource(R.string.clock_min_sec, sec / 60, sec % 60)
