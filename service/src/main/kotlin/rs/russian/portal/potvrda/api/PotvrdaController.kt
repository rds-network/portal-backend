package rs.russian.portal.potvrda.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rs.russian.generated.model.PotvrdaIssueCreateRequest
import rs.russian.generated.model.PotvrdaIssueDto
import rs.russian.generated.model.PotvrdaIssuePageResponse
import rs.russian.generated.model.PotvrdaNextNumberDto
import rs.russian.portal.potvrda.service.PotvrdaIssueService
import rs.russian.portal.shared.security.Authorized
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_SSO
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import rs.russian.portal.user.domain.enums.UserGroup.MAIN_VOLUNTEER

@RestController
@RequestMapping("/potvrda")
class PotvrdaController(
    private val potvrdaIssueService: PotvrdaIssueService,
) {

    @Authorized(allowed = [ADMIN, ADMIN_SSO, ADMIN_VOLUNTEER, MAIN_VOLUNTEER])
    @GetMapping("/next-number")
    fun nextNumber(@RequestParam(required = false) year: Int?): ResponseEntity<PotvrdaNextNumberDto> =
        ResponseEntity.ok(potvrdaIssueService.nextNumber(year))

    @Authorized(allowed = [ADMIN, ADMIN_SSO, ADMIN_VOLUNTEER, MAIN_VOLUNTEER])
    @GetMapping("/issues")
    fun list(
        @RequestParam(required = false, defaultValue = "0") pageNumber: Int,
        @RequestParam(required = false, defaultValue = "50") pageSize: Int,
        @RequestParam(required = false) search: String?,
    ): ResponseEntity<PotvrdaIssuePageResponse> =
        ResponseEntity.ok(potvrdaIssueService.list(pageNumber, pageSize, search))

    @Authorized(allowed = [ADMIN, ADMIN_SSO, ADMIN_VOLUNTEER, MAIN_VOLUNTEER])
    @PostMapping("/issues")
    fun create(@RequestBody request: PotvrdaIssueCreateRequest): ResponseEntity<PotvrdaIssueDto> =
        ResponseEntity.ok(potvrdaIssueService.create(request))
}
