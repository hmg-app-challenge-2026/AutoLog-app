package kr.autolog.vehicle.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.LocalWindowLayout
import kr.autolog.vehicle.ui.design.Space
import kr.autolog.vehicle.ui.design.WindowLayout
import kr.autolog.vehicle.ui.design.components.HeroMetric
import kr.autolog.vehicle.ui.design.components.MetricSize
import kr.autolog.vehicle.ui.design.components.RouteFlow
import kr.autolog.vehicle.ui.design.components.TripListRow
import kr.autolog.vehicle.ui.trips.TripRowUi
import kr.autolog.vehicle.ui.trips.tripMetaText
import kr.autolog.vehicle.ui.trips.tripRouteText

/**
 * Home 가운데 두 섹션(D-25). 상태마다 "사용자가 지금 알고 싶은 것"을 왼쪽(값)·오른쪽(맥락) 순서로 돌려준다.
 * - 대기: "이번 달 운행 정보"(누적 km + 보조 사실) · "최근 운행 기록"(최신 3건 목록, 누르면 운행 기록 조회).
 * - 기록 중: "주행 거리" · "운행 경로"(출발지 → 흐르는 화살표 → 목적지).
 * - 복구 중: 없음(팝업이 모든 정보를 말한다).
 * 소제목 문구는 정보 종류마다 하나인 `section_*`. 정차·주행 중에도 같은 섹션을 그대로 보인다(D-31). 거리 숫자는 화면 폭에 따라 140/112/80 sp로 조정한다.
 */
@Composable
internal fun homeSections(state: HomeUiState, onAction: (HomeAction) -> Unit): List<HomeSection> = when (state.phase) {
    HomePhase.READY ->
        listOf(
            HomeSection(stringResource(R.string.section_month_trip)) { MonthSummary(state.month) },
            HomeSection(stringResource(R.string.section_recent_trips)) { RecentTrips(state.recentTrips, onAction) },
        )
    HomePhase.RECORDING_PARKED ->
        listOf(
            HomeSection(stringResource(R.string.section_distance)) { TripDistance(state.tripKm) },
            HomeSection(stringResource(R.string.section_route)) { TripRoute(state) },
        )
    HomePhase.NEEDS_RECOVERY -> emptyList()
}

@Composable
private fun TripRoute(state: HomeUiState) {
    val c = AutoLogTheme.colors
    // 분할 화면 한 칸은 소제목 바로 밑에 붙어 보여 소제목과 출발지 사이를 24 dp 더 띄운다(넓은 화면은 내용이 세로 가운데라 이미 떨어져 있다)
    if (LocalWindowLayout.current == WindowLayout.NARROW) Spacer(Modifier.height(Space.s5))
    RouteFlow(
        origin = state.origin,
        destination = state.destination ?: stringResource(R.string.route_no_destination),
        destinationColor = if (state.destination != null) c.textPrimary else c.textSecondary,
        textStyle = AutoLogTheme.windowType.homeRoute,
        markerScale = routeMarkerScale(),
    )
}

/**
 * 출발지·목적지를 키운 Home에서 ○ · 흐르는 갈매기 · ●도 함께 키운다(2026-11-10 사용자 지시 — 글자보다 한 단계 더, 방향이 한눈에).
 * 넓은 화면 2배(갈매기 48×72 dp) · 분할 화면 1.6배 · 세로 창 그대로.
 */
@Composable
private fun routeMarkerScale(): Float = when (LocalWindowLayout.current) {
    WindowLayout.WIDE -> ROUTE_MARKER_SCALE_WIDE
    WindowLayout.NARROW -> ROUTE_MARKER_SCALE_NARROW
    WindowLayout.TALL -> 1f
}

private const val ROUTE_MARKER_SCALE_WIDE = 2f
private const val ROUTE_MARKER_SCALE_NARROW = 1.6f

/** 큰 숫자 크기 — 넓은 화면 140 sp, 세로 화면 112 sp, 좁은 분할 화면 80 sp. */
@Composable
private fun metricSize(): MetricSize = when (LocalWindowLayout.current) {
    WindowLayout.WIDE -> MetricSize.HERO
    // 분할 화면 한 칸·세로 창은 세로 한 칸에 두 섹션을 쌓으므로 한 단계 작게(112 sp)
    WindowLayout.NARROW, WindowLayout.TALL -> MetricSize.MEDIUM
}

@Composable
private fun MonthSummary(month: MonthUi?) {
    val c = AutoLogTheme.colors
    HeroMetric(
        value = month?.totalKm,
        unit = stringResource(R.string.unit_km),
        label = null,
        unavailableText = stringResource(R.string.home_month_loading),
        size = metricSize(),
    )
    month?.let {
        Spacer(Modifier.height(Space.s2))
        Text(
            stringResource(R.string.home_month_meta, it.tripCount, it.businessKm),
            style = AutoLogTheme.type.caption,
            color = c.textSecondary,
        )
    }
}

@Composable
private fun TripDistance(tripKm: String?) {
    if (tripKm == null) {
        // 추정값은 그리지 않는다. 앱 안 알림이 없으므로(D-26) 이유는 이 칸 안에서 한 줄로
        Text(
            stringResource(R.string.trip_km_unknown),
            style = AutoLogTheme.type.label,
            color = AutoLogTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(Space.s2))
        Text(
            stringResource(R.string.trip_km_unknown_reason),
            style = AutoLogTheme.type.caption,
            color = AutoLogTheme.colors.textSecondary,
        )
        return
    }
    HeroMetric(
        value = tripKm,
        unit = stringResource(R.string.unit_km),
        label = null, // 소제목 "주행 거리"가 이미 말한다
        unavailableText = stringResource(R.string.distance_unavailable),
        size = metricSize(),
    )
}

/**
 * 최근 운행 최대 3건, 위에서 아래로. 목적이 없는 운행은 아래 줄 끝이 "목적 미입력"(재촉하지 않는 회색 — 사실만).
 * 어느 줄을 눌러도 운행 기록 조회로 간다 — 목적을 고치는 곳은 거기 하나다(D-26).
 */
@Composable
private fun RecentTrips(trips: List<TripRowUi>, onAction: (HomeAction) -> Unit) {
    if (trips.isEmpty()) {
        Text(
            stringResource(R.string.recent_empty),
            style = AutoLogTheme.type.caption,
            color = AutoLogTheme.colors.textTertiary,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        trips.forEach { trip ->
            TripListRow(
                title = tripRouteText(trip),
                subtitle = tripMetaText(trip),
                onClick = { onAction(HomeAction.OpenRecords) },
            )
        }
    }
}
