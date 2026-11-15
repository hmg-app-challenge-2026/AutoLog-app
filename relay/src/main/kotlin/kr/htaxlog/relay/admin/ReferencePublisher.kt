package kr.htaxlog.relay.admin

import kr.htaxlog.relay.device.DeviceRegisteredListener
import kr.htaxlog.relay.inbox.InboxService
import kr.htaxlog.shared.PlaceDto
import kr.htaxlog.shared.PlacesPayload
import kr.htaxlog.shared.RecipientDto
import kr.htaxlog.shared.RecipientsPayload
import kr.htaxlog.shared.ScheduleDto
import kr.htaxlog.shared.SchedulesPayload
import kr.htaxlog.shared.UserDto
import kr.htaxlog.shared.UsersPayload
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 기준 정보 배포 (F-43). 바뀔 때마다 **전체 목록**을 차 inbox에 넣는다(InboxService가 안 가져간 이전 편지는 지운다).
 * - 직원·장소·수신자: 조직의 모든 차
 * - 배차: 해당 차만, 어제부터 30일 뒤까지
 * 새 기기가 등록되면 그 차에 네 가지를 모두 보낸다.
 */
@Component
class ReferencePublisher(
    private val repo: ReferenceRepository,
    private val inbox: InboxService,
    private val clock: Clock,
) : DeviceRegisteredListener {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publishUsers(org: UUID) = toAll(
        org,
        UsersPayload(
            repo.users(org).map {
                UserDto(it.userId.toString(), it.name, it.dept, it.email)
            },
        ),
    )

    fun publishPlaces(org: UUID) = toAll(org, PlacesPayload(repo.places(org).map { it.toDto() }))

    fun publishRecipients(org: UUID) = toAll(
        org,
        RecipientsPayload(
            repo.recipients(org).map {
                RecipientDto(it.email, it.name)
            },
        ),
    )

    fun publishSchedules(org: UUID, vehicleIds: Collection<UUID>) {
        val now = clock.instant()
        vehicleIds.distinct().forEach { v ->
            val list = repo.schedules(org, v, now.minus(PAST), now.plus(FUTURE)).map { it.toDto() }
            inbox.deliver(v, SchedulesPayload(list))
        }
    }

    override fun onRegistered(vehicleId: UUID, orgId: UUID) {
        val now = clock.instant()
        inbox.deliver(
            vehicleId,
            UsersPayload(
                repo.users(orgId).map {
                    UserDto(it.userId.toString(), it.name, it.dept, it.email)
                },
            ),
        )
        inbox.deliver(vehicleId, PlacesPayload(repo.places(orgId).map { it.toDto() }))
        inbox.deliver(vehicleId, RecipientsPayload(repo.recipients(orgId).map { RecipientDto(it.email, it.name) }))
        inbox.deliver(
            vehicleId,
            SchedulesPayload(
                repo.schedules(orgId, vehicleId, now.minus(PAST), now.plus(FUTURE)).map {
                    it.toDto()
                },
            ),
        )
        log.info("새 기기에 기준 정보 배포 vehicle={}", vehicleId)
    }

    private fun toAll(org: UUID, payload: kr.htaxlog.shared.InboxPayload) {
        val vehicles = repo.vehicleIds(org)
        vehicles.forEach { inbox.deliver(it, payload) }
        log.info("기준 정보 배포 {} → 차량 {}대", InboxService.kindOf(payload), vehicles.size)
    }

    companion object {
        val PAST: Duration = Duration.ofDays(1)
        val FUTURE: Duration = Duration.ofDays(30)
        private val KST: ZoneOffset = ZoneOffset.ofHours(9)

        fun iso(t: Instant): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(t.atOffset(KST))

        fun PlaceRow.toDto() = PlaceDto(placeId.toString(), label, kind, lat, lng, radiusM, defaultPurpose)

        fun ScheduleRow.toDto() = ScheduleDto(
            scheduleId.toString(),
            vehicleId.toString(),
            userId?.toString(),
            iso(startsAt),
            iso(endsAt),
            destPlaceId?.toString(),
            purposeCategory,
            purposeDetail,
        )
    }
}
