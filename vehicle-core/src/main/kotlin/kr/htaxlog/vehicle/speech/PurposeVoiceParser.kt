package kr.htaxlog.vehicle.speech

import kr.htaxlog.shared.PlaceKind
import kr.htaxlog.vehicle.core.PlaceInfo
import kr.htaxlog.vehicle.core.PurposeDetail
import kr.htaxlog.vehicle.core.VoicePurpose

/**
 * 목적 음성 → 세부 목적 (F-09, 기술명세 2.7). 단순 규칙: 키워드와 등록 장소 이름을 위에서부터 맞춘다.
 * 원문은 항상 note로 남긴다(TripEntity.purposeNote). 결과는 추천이고 사용자가 화면에서 확정한다.
 * 수용 기준: "판교 A고객사 미팅" → 거래처 방문 + note.
 */
object PurposeVoiceParser {
    private val RULES: List<Pair<Regex, PurposeDetail>> =
        listOf(
            Regex("출근") to PurposeDetail.COMMUTE_IN,
            Regex("퇴근") to PurposeDetail.COMMUTE_OUT,
            Regex("개인|사적|사적인|병원|장보") to PurposeDetail.PERSONAL,
            Regex("출장") to PurposeDetail.BUSINESS_TRIP,
        )
    private val AFTER_PLACE: List<Pair<Regex, PurposeDetail>> =
        listOf(
            Regex("거래처|고객|방문|납품|영업|상담|계약") to PurposeDetail.CLIENT_VISIT,
            Regex("지점") to PurposeDetail.BRANCH_MOVE,
            Regex("회의|미팅|세미나|교육") to PurposeDetail.MEETING,
            Regex("외근|현장|점검|설치|수리|출동|배송|업무") to PurposeDetail.FIELD_WORK,
        )
    private const val MIN_LABEL_LEN = 2

    fun parse(text: String, places: List<PlaceInfo> = emptyList()): VoicePurpose? {
        val note = text.trim().ifEmpty { return null }
        val t = normalize(note)
        RULES.firstOrNull { (re, _) -> re.containsMatchIn(t) }?.let { return VoicePurpose(it.second, note) }
        matchPlace(t, places)?.let { p -> fromPlace(p)?.let { return VoicePurpose(it, note) } }
        return AFTER_PLACE.firstOrNull { (re, _) -> re.containsMatchIn(t) }?.let { VoicePurpose(it.second, note) }
    }

    /** 말에 등록 장소 이름이 들어 있으면 그 장소(가장 긴 이름 우선). 자택은 보지 않는다. */
    internal fun matchPlace(normalizedText: String, places: List<PlaceInfo>): PlaceInfo? = places
        .filter { it.kind != PlaceKind.HOME }
        .map { it to normalize(it.label) }
        .filter { (_, label) -> label.length >= MIN_LABEL_LEN && normalizedText.contains(label) }
        .maxByOrNull { (_, label) -> label.length }
        ?.first

    private fun fromPlace(p: PlaceInfo): PurposeDetail? = PurposeDetail.fromName(p.defaultPurpose)
        ?: when (p.kind) {
            PlaceKind.CLIENT -> PurposeDetail.CLIENT_VISIT
            PlaceKind.BRANCH -> PurposeDetail.BRANCH_MOVE
            else -> null
        }

    private fun normalize(s: String) = s.lowercase().filterNot { it.isWhitespace() }
}
