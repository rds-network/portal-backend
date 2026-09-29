package rs.russian.portal.application.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.generated.model.ApplicationDto
import rs.russian.portal.application.mapper.ApplicationMapper
import rs.russian.portal.application.service.ApplicationService
import rs.russian.portal.shared.security.Authorized
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import rs.russian.portal.user.domain.enums.UserGroup.INTERVIEWER

@RestController
@RequestMapping("/application")
class ApplicationLookupController(
    private val applicationService: ApplicationService,
    private val applicationMapper: ApplicationMapper,
) {

    @Authorized(allowed = [ADMIN_VOLUNTEER, INTERVIEWER])
    @GetMapping("/for-user/{username}")
    fun getApplicationForUser(@PathVariable username: String): ResponseEntity<ApplicationDto> {
        val application = applicationService.findLatestForUsername(username)
            ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(applicationMapper.toDto(application))
    }
}
