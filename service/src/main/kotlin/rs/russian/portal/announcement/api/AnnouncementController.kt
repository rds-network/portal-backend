package rs.russian.portal.announcement.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import rs.russian.generated.api.AnnouncementsApi
import rs.russian.generated.model.AnnouncementCreateRequest
import rs.russian.generated.model.AnnouncementDto
import rs.russian.generated.model.UnreadAnnouncementsCountDto
import rs.russian.portal.announcement.service.AnnouncementService
import rs.russian.portal.shared.security.Authorized
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_SSO
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import rs.russian.portal.user.domain.enums.UserGroup.MAIN_VOLUNTEER
import java.util.UUID

@RestController
class AnnouncementController(
    private val announcementService: AnnouncementService,
) : AnnouncementsApi {

    override fun getAnnouncements(): ResponseEntity<List<AnnouncementDto>> =
        ResponseEntity.ok(announcementService.getForCurrentUser())

    override fun getUnreadAnnouncementsCount(): ResponseEntity<UnreadAnnouncementsCountDto> =
        ResponseEntity.ok(announcementService.getUnreadCount())

    override fun markAnnouncementRead(id: UUID): ResponseEntity<Unit> {
        announcementService.markRead(id)
        return ResponseEntity.ok().build()
    }

    @Authorized(allowed = [ADMIN, ADMIN_VOLUNTEER, ADMIN_SSO, MAIN_VOLUNTEER])
    override fun createAnnouncement(request: AnnouncementCreateRequest): ResponseEntity<AnnouncementDto> =
        ResponseEntity.ok(announcementService.create(request))
}
