package kr.autolog.vehicle.ui.design

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * 화면 오른쪽 위에 앱 공통 요소(운전자 배지)가 차지하는 폭. 창 inset처럼 다룬다 —
 * 화면의 맨 위 줄은 `padding(end = LocalTopEndReserved.current)`로 이 자리를 비운다.
 * 배지를 그리는 쪽(root의 BadgeScaffold)이 실제로 잰 폭을 넣고, 배지가 없으면 0이다.
 */
val LocalTopEndReserved = compositionLocalOf { 0.dp }
