package rs.russian.portal.announcement.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.generated.model.AnnouncementDto
import rs.russian.portal.announcement.service.AnnouncementService
import rs.russian.portal.shared.security.Authorized
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_SSO
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import rs.russian.portal.user.domain.enums.UserGroup.MAIN_VOLUNTEER

data class PersonalAnnouncementRequest(
    val title: String,
    val body: String,
    val username: String,
)

@RestController
@RequestMapping("/announcements")
class AnnouncementPersonalController(
    private val announcementService: AnnouncementService,
) {

    @Authorized(allowed = [ADMIN, ADMIN_VOLUNTEER, ADMIN_SSO, MAIN_VOLUNTEER])
    @PostMapping("/personal")
    fun createPersonal(@RequestBody request: PersonalAnnouncementRequest): ResponseEntity<AnnouncementDto> =
        ResponseEntity.ok(announcementService.createForUser(request.username, request.title, request.body))
}
