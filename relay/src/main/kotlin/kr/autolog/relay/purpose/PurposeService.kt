package kr.autolog.relay.purpose

import kr.autolog.relay.admin.ReferenceRepository
import kr.autolog.relay.common.ApiException
import kr.autolog.relay.device.DeviceRegisteredListener
import kr.autolog.relay.inbox.InboxService
import kr.autolog.shared.PurposeDto
import kr.autolog.shared.PurposeRuleDto
import kr.autolog.shared.PurposesPayload
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class PurposeRequest(val label: String? = null, val category: String? = null)

/** [originPlaceId] 비우면 출발지 어디서든. [purpose]는 기본 목적 이름 또는 회사 목적의 name. */
data class PurposeRuleRequest(
    val originPlaceId: UUID? = null,
    val destPlaceId: UUID? = null,
    val purpose: String? = null,
)

/** 관리자 웹에 보이는 목적. [name]은 규칙에 쓰는 값(차의 PurposeDetail.name). [builtIn]이면 지울 수 없다. */
data class PurposeView(val id: Long?, val name: String, val label: String, val category: String, val builtIn: Boolean)

data class PurposeRuleView(val ruleId: UUID, val originPlaceId: UUID?, val destPlaceId: UUID, val purpose: String)

/**
 * 회사 목적과 출발→도착 목적 규칙 (F-08). 바뀔 때마다 회사의 모든 차에 PURPOSES(전체)를 보낸다. 새 차가 등록되면 그 차에도.
 * 목적의 차 쪽 이름은 `custom:<구분>:<이름>`(차 PurposeDetail.custom과 같은 규칙)이라 이름만으로 구분·이름이 살아 있다.
 */
@Service
class PurposeService(
    private val repo: PurposeRepository,
    private val refs: ReferenceRepository,
    private val inbox: InboxService,
) : DeviceRegisteredListener {
    fun purposes(org: UUID): List<PurposeView> =
        BUILT_IN.map { (name, label, category) -> PurposeView(null, name, label, category, true) } +
            repo.purposes(org).map {
                PurposeView(it.id, customName(it.category, it.label), it.label, it.category, false)
            }

    @Transactional
    fun addPurpose(org: UUID, req: PurposeRequest): PurposeView {
        val label = req.label?.trim().orEmpty()
        if (label.isEmpty() || label.length > MAX_LABEL) throw bad("목적 이름을 1~${MAX_LABEL}자로 입력해 주세요")
        if (label.contains(':')) throw bad("목적 이름에 ':'는 쓸 수 없습니다")
        val category = req.category?.takeIf { it in CATEGORIES } ?: throw bad("구분을 골라 주세요")
        if (BUILT_IN.any { it.second == label } || !repo.insertPurpose(org, label, category)) {
            throw ApiException(HttpStatus.CONFLICT, "PURPOSE_DUPLICATE", "이미 있는 목적입니다")
        }
        publish(org)
        return purposes(org).last { it.label == label }
    }

    @Transactional
    fun deletePurpose(org: UUID, id: Long) {
        val row = repo.purpose(org, id) ?: throw notFound("PURPOSE_NOT_FOUND", "목적이 없습니다")
        repo.deleteRulesUsing(org, customName(row.category, row.label))
        repo.deletePurpose(org, id)
        publish(org)
    }

    fun rules(org: UUID): List<PurposeRuleView> =
        repo.rules(org).map { PurposeRuleView(it.ruleId, it.originPlaceId, it.destPlaceId, it.purpose) }

    @Transactional
    fun addRule(org: UUID, req: PurposeRuleRequest): PurposeRuleView {
        val dest = req.destPlaceId ?: throw bad("도착 장소를 골라 주세요")
        val places = repo.placeIds(org)
        if (dest !in places || (req.originPlaceId != null && req.originPlaceId !in places)) {
            throw bad("이 회사에 없는 장소입니다")
        }
        if (req.originPlaceId == dest) throw bad("출발과 도착이 같은 장소입니다")
        val purpose = req.purpose?.takeIf { p -> purposes(org).any { it.name == p } } ?: throw bad("목적을 골라 주세요")
        val row = PurposeRuleRow(UUID.randomUUID(), req.originPlaceId, dest, purpose)
        if (!repo.insertRule(org, row)) {
            throw ApiException(HttpStatus.CONFLICT, "PURPOSE_RULE_DUPLICATE", "같은 출발·도착 규칙이 이미 있습니다")
        }
        publish(org)
        return PurposeRuleView(row.ruleId, row.originPlaceId, row.destPlaceId, row.purpose)
    }

    @Transactional
    fun deleteRule(org: UUID, id: UUID) {
        if (!repo.deleteRule(org, id)) throw notFound("PURPOSE_RULE_NOT_FOUND", "규칙이 없습니다")
        publish(org)
    }

    override fun onRegistered(vehicleId: UUID, orgId: UUID) {
        val p = payload(orgId)
        if (p.purposes.isNotEmpty() || p.rules.isNotEmpty()) inbox.deliver(vehicleId, p)
    }

    /** 장소가 지워지면 그 장소를 쓰던 규칙도 DB에서 사라진다(FK) — 장소 삭제 뒤에도 불러 차에 알린다. */
    fun publish(org: UUID) {
        val p = payload(org)
        refs.vehicleIds(org).forEach { inbox.deliver(it, p) }
    }

    private fun payload(org: UUID) = PurposesPayload(
        purposes = repo.purposes(org).map { PurposeDto(it.label, it.category) },
        rules = repo.rules(org).map {
            PurposeRuleDto(it.ruleId.toString(), it.originPlaceId?.toString(), it.destPlaceId.toString(), it.purpose)
        },
    )

    private fun bad(message: String) = ApiException(HttpStatus.BAD_REQUEST, "PURPOSE_INVALID", message)

    private fun notFound(code: String, message: String) = ApiException(HttpStatus.NOT_FOUND, code, message)

    companion object {
        const val MAX_LABEL = 30
        val CATEGORIES = setOf("BUSINESS", "COMMUTE", "NON_BUSINESS")

        /** 차 PurposeDetail의 기본 목적(미확정 제외). 이름·한글·구분. */
        val BUILT_IN: List<Triple<String, String, String>> = listOf(
            Triple("CLIENT_VISIT", "거래처 방문", "BUSINESS"),
            Triple("FIELD_WORK", "외근", "BUSINESS"),
            Triple("BUSINESS_TRIP", "출장", "BUSINESS"),
            Triple("MEETING", "회의", "BUSINESS"),
            Triple("BRANCH_MOVE", "지점 이동", "BUSINESS"),
            Triple("COMMUTE_IN", "출근", "COMMUTE"),
            Triple("COMMUTE_OUT", "퇴근", "COMMUTE"),
            Triple("PERSONAL", "개인 이동", "NON_BUSINESS"),
        )

        fun customName(category: String, label: String) = "custom:$category:$label"
    }
}
