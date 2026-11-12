package kr.autolog.vehicle.nav

import kr.autolog.vehicle.core.DeepLink

/**
 * 알림 탭 딥링크(스펙 6.10). 앱 A의 기록 알림이 `MainActivity`를 열 때 넣는 extra 키·값은 core [DeepLink]가 정한다(R-3, 앱 A·프론트 공용).
 * `ROUTE_REVIEW`("review") = 운행 결과(옛 목적 확인 — 이름은 앱 A와 맞춘 그대로). extra 값 → 화면.
 */
/** extra 값 → 화면. 모르는 경로이거나 필요한 값이 없으면 null(무시). Android Intent와 분리해 단위 테스트한다. */
fun deepLinkRoute(route: String?, tripId: String?): Route? = when (route) {
    DeepLink.ROUTE_REVIEW -> tripId?.takeIf { it.isNotBlank() }?.let { Route.TripResult(it) }
    else -> null
}
