package kr.autolog.vehicle.nav

/** 백스택의 모든 화면은 주차(FULL) 전용이다(스펙 6.10). 정차·주행 중엔 Home만 남아 읽기 전용으로 보인다(D-31). */
sealed interface Route {
    data object ProfilePicker : Route

    data object Home : Route

    /**
     * 운행 결과 카드(스펙 7.3, D-27). 운행이 끝나면 자동으로·알림 탭으로 열 때는 [TripResultMode.AFTER_TRIP],
     * 운행 기록 조회에서 지난 운행을 열 때는 [TripResultMode.REVIEW](카운트다운·자동 저장 없음, D-29).
     */
    data class TripResult(val tripId: String, val mode: TripResultMode = TripResultMode.AFTER_TRIP) : Route

    /** 주유 기록 추가의 기본 경로: 폰으로 영수증을 올릴 QR(스펙 7.4, D-20). */
    data object ReceiptQr : Route

    /**
     * 주유 기록 입력 창 하나(스펙 7.5, D-20). [receiptId]가 있으면 영수증 OCR 값이 채워진 채로,
     * null이면 빈 칸으로(QR 화면의 [직접 입력하기]) 열린다. 저장은 [추가하기] 한 번.
     */
    data class FuelEntry(val receiptId: String? = null) : Route

    data object Report : Route

    data object History : Route

    data object Settings : Route
}

/** 운행 결과 카드를 어떤 상황에서 열었는지(D-29). */
enum class TripResultMode {
    /** 운행이 막 끝남 — 5초 뒤 지금 선택으로 저장하고 돌아간다(종료 대기면 앱 A 종료 시각). */
    AFTER_TRIP,

    /**
     * 운행 기록 조회에서 지난 운행을 사후 확인 — 정차·주차 중 천천히 보는 것이라 카운트다운도 자동 저장도 없다.
     * [저장]을 눌러야만 저장되고 목록으로 돌아간다. 목적 미입력 운행은 수정 모드로 열린다.
     */
    REVIEW,
}
