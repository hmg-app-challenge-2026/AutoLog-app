package kr.autolog.vehicle.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kr.autolog.shared.PlaceKind
import kr.autolog.shared.PurposeDto
import kr.autolog.shared.PurposeRuleDto
import kr.autolog.shared.PurposesPayload
import kr.autolog.vehicle.core.Origin
import kr.autolog.vehicle.core.PlaceAddResult
import kr.autolog.vehicle.core.PlaceCandidate
import kr.autolog.vehicle.core.PlaceInfo
import kr.autolog.vehicle.core.PlaceSearchResult
import kr.autolog.vehicle.core.PurposeCategory
import kr.autolog.vehicle.core.PurposeInfo
import kr.autolog.vehicle.core.PurposeRuleInfo
import kr.autolog.vehicle.core.RecipientInfo
import kr.autolog.vehicle.core.SettingsApi
import kr.autolog.vehicle.core.SettingsResult
import kr.autolog.vehicle.core.SyncResult
import kr.autolog.vehicle.core.UserInfo
import kr.autolog.vehicle.domain.PurposeBook

/** [SettingsApi] 가짜. 서버 배포 수신자 1명 + 직접 입력 1명, 직원 3명, 회사 목적 1개·규칙 1개로 시작한다. */
class FakeSettingsApi(var nextSync: SyncResult = SyncResult.Ok(received = 2)) : SettingsApi {
    private val recipientState =
        MutableStateFlow(
            listOf(
                RecipientInfo("accounting@example.com", "회계팀", Origin.SERVER),
                RecipientInfo("me@example.com", "나", Origin.LOCAL),
            ),
        )
    private val userState =
        MutableStateFlow(
            listOf(
                UserInfo("u-1", "김영업", "영업1팀"),
                UserInfo("u-2", "이현장", "기술지원팀"),
                UserInfo("u-3", "박관리", "총무팀"),
            ),
        )
    private val autoState = MutableStateFlow(false)

    override fun recipients(): Flow<List<RecipientInfo>> = recipientState

    override suspend fun addRecipient(email: String, name: String?): SettingsResult {
        if (!email.contains('@')) return SettingsResult.Invalid("이메일 형식이 올바르지 않습니다")
        recipientState.value =
            recipientState.value.filterNot { it.email == email } + RecipientInfo(email, name, Origin.LOCAL)
        return SettingsResult.Ok
    }

    override suspend fun removeRecipient(email: String): SettingsResult {
        val r = recipientState.value.firstOrNull { it.email == email } ?: return SettingsResult.Invalid("없는 수신자입니다")
        recipientState.value = recipientState.value - r
        return SettingsResult.Ok
    }

    override fun users(): Flow<List<UserInfo>> = userState

    override suspend fun addUser(name: String, dept: String?, email: String?): SettingsResult {
        if (name.isBlank()) return SettingsResult.Invalid("이름을 입력해 주세요")
        userState.value =
            userState.value + UserInfo("local-${userState.value.size + 1}", name.trim(), dept, email, Origin.LOCAL)
        return SettingsResult.Ok
    }

    override val autoMonthlySend: StateFlow<Boolean> = autoState.asStateFlow()

    override suspend fun setAutoMonthlySend(enabled: Boolean) {
        autoState.value = enabled
    }

    override suspend fun syncNow(): SyncResult = nextSync

    private val fakePlaces = listOf(
        PlaceInfo("p-office", "본사", PlaceKind.OFFICE, 37.5665, 126.978),
        PlaceInfo("p-client", "한빛상사", PlaceKind.CLIENT, 37.4979, 127.0276),
        PlaceInfo("p-depot", "물류센터", PlaceKind.CLIENT, 37.3947, 127.1112),
    )
    private val book = PurposeBook().apply {
        applyServer(
            PurposesPayload(
                purposes = listOf(PurposeDto("납품", PurposeCategory.BUSINESS.name)),
                rules = listOf(PurposeRuleDto("r-1", "p-office", "p-depot", "custom:BUSINESS:납품")),
            ),
        )
    }

    override fun purposes(): Flow<List<PurposeInfo>> = book.data.map { book.purposes(it) }

    override suspend fun addPurpose(label: String, category: PurposeCategory): SettingsResult =
        book.addPurpose(label, category).fold({ SettingsResult.Ok }, { SettingsResult.Invalid(it.message ?: "") })

    override suspend fun removePurpose(name: String): SettingsResult =
        book.removePurpose(name).fold({ SettingsResult.Ok }, { SettingsResult.Invalid(it.message ?: "") })

    override fun purposeRules(): Flow<List<PurposeRuleInfo>> = book.data.map { d ->
        val byId = fakePlaces.associateBy { it.placeId }
        book.rules(d).map {
            PurposeRuleInfo(
                it.ruleId,
                it.originPlaceId?.let { id -> byId[id]?.label },
                it.originPlaceId != null && byId[it.originPlaceId] == null,
                byId[it.destPlaceId]?.label,
                it.purpose,
                it.origin,
            )
        }
    }

    private val addedPlaces = mutableListOf<PlaceInfo>()

    override suspend fun places(): List<PlaceInfo> = fakePlaces + addedPlaces

    override suspend fun searchPlaces(query: String): PlaceSearchResult = PlaceSearchResult.Found(
        listOf(
            PlaceCandidate("$query 본점", "서울 중구 세종대로 110", 37.5663, 126.9779),
            PlaceCandidate("$query 2호점", "경기 성남시 분당구 판교역로 166", 37.3952, 127.1110),
        ),
    )

    override suspend fun currentPlace(): PlaceSearchResult =
        PlaceSearchResult.Found(listOf(PlaceCandidate(null, "서울 강남구 테헤란로 152", 37.5000, 127.0364)))

    override suspend fun addPlace(label: String, lat: Double, lng: Double): PlaceAddResult {
        if (label.isBlank()) return PlaceAddResult.Invalid("장소 이름을 입력해 주세요")
        val id = "p-added-${addedPlaces.size + 1}"
        addedPlaces += PlaceInfo(id, label.trim(), PlaceKind.OTHER, lat, lng, origin = Origin.LOCAL)
        return PlaceAddResult.Added(id)
    }

    override suspend fun addPurposeRule(
        originPlaceId: String?,
        destPlaceId: String,
        purposeName: String,
    ): SettingsResult = book.addRule(originPlaceId, destPlaceId, purposeName).fold({ SettingsResult.Ok }, {
        SettingsResult.Invalid(
            it.message ?: "",
        )
    })

    override suspend fun removePurposeRule(ruleId: String): SettingsResult =
        book.removeRule(ruleId).fold({ SettingsResult.Ok }, { SettingsResult.Invalid(it.message ?: "") })
}
