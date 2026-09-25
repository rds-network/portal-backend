package rs.russian.portal.program.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.program.service.ProgramCuratorService
import rs.russian.portal.shared.security.Authorized
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_SSO
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import rs.russian.portal.user.domain.enums.UserGroup.MAIN_VOLUNTEER

@RestController
@RequestMapping("/program-curators")
class ProgramCuratorController(
    private val programCuratorService: ProgramCuratorService,
) {

    @GetMapping
    fun list(): ResponseEntity<List<ProgramCuratorDto>> =
        ResponseEntity.ok(programCuratorService.list())

    @GetMapping("/me")
    fun me(): ResponseEntity<Map<String, Boolean>> =
        ResponseEntity.ok(mapOf("curator" to programCuratorService.isCurrentCurator()))

    @Authorized(allowed = [ADMIN, ADMIN_VOLUNTEER, ADMIN_SSO, MAIN_VOLUNTEER])
    @PostMapping
    fun assign(@RequestBody request: ProgramCuratorWriteRequest): ResponseEntity<ProgramCuratorDto> =
        ResponseEntity.ok(programCuratorService.assign(request))

    @Authorized(allowed = [ADMIN, ADMIN_VOLUNTEER, ADMIN_SSO, MAIN_VOLUNTEER])
    @DeleteMapping("/{programCode}/{username}")
    fun remove(
        @PathVariable programCode: String,
        @PathVariable username: String,
    ): ResponseEntity<Void> {
        programCuratorService.remove(programCode, username)
        return ResponseEntity.noContent().build()
    }
}
