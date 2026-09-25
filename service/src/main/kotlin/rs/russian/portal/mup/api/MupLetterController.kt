package rs.russian.portal.mup.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.mup.service.MupLetterService
import rs.russian.portal.shared.security.Authorized
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_SSO

@RestController
@RequestMapping("/mup-letters")
class MupLetterController(
    private val mupLetterService: MupLetterService,
) {

    @Authorized(allowed = [ADMIN, ADMIN_SSO])
    @GetMapping
    fun list(): ResponseEntity<List<MupLetterDto>> =
        ResponseEntity.ok(mupLetterService.list())

    @Authorized(allowed = [ADMIN, ADMIN_SSO])
    @GetMapping("/draft/{username}")
    fun draft(@PathVariable username: String): ResponseEntity<MupLetterDraft> =
        ResponseEntity.ok(mupLetterService.draft(username))

    @Authorized(allowed = [ADMIN, ADMIN_SSO])
    @PostMapping
    fun send(@RequestBody request: MupLetterSendRequest): ResponseEntity<MupLetterDto> =
        ResponseEntity.ok(mupLetterService.send(request))
}
