package rs.russian.portal.accountstatus.api

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.accountstatus.service.AccountStatusService
import rs.russian.portal.shared.security.Authorized
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_SSO
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import java.util.UUID

@RestController
@RequestMapping("/user/account-status")
class AccountStatusController(
    private val accountStatusService: AccountStatusService,
) {

    @GetMapping("/meta")
    fun meta(): ResponseEntity<AccountStatusMetaDto> =
        ResponseEntity.ok(accountStatusService.meta())

    @Authorized(allowed = [ADMIN_SSO, ADMIN_VOLUNTEER])
    @PostMapping("/request")
    fun create(@RequestBody request: AccountStatusCreateRequest): ResponseEntity<AccountStatusChangeResultDto> {
        val result = accountStatusService.create(request)
        val status = if (result.pending) HttpStatus.ACCEPTED else HttpStatus.OK
        return ResponseEntity.status(status).body(result)
    }

    @Authorized(allowed = [ADMIN_SSO, ADMIN_VOLUNTEER])
    @GetMapping("/pending")
    fun pending(): ResponseEntity<List<AccountStatusRequestDto>> =
        ResponseEntity.ok(accountStatusService.pending())

    @Authorized(allowed = [ADMIN_SSO, ADMIN_VOLUNTEER])
    @GetMapping("/events")
    fun events(): ResponseEntity<List<AccountStatusEventDto>> =
        ResponseEntity.ok(accountStatusService.events())

    @Authorized(allowed = [ADMIN_SSO, ADMIN_VOLUNTEER])
    @PostMapping("/{id}/approve")
    fun approve(
        @PathVariable id: UUID,
        @RequestBody(required = false) request: AccountStatusDecisionRequest?,
    ): ResponseEntity<AccountStatusRequestDto> =
        ResponseEntity.ok(accountStatusService.approve(id, request))

    @Authorized(allowed = [ADMIN_SSO, ADMIN_VOLUNTEER])
    @PostMapping("/{id}/reject")
    fun reject(
        @PathVariable id: UUID,
        @RequestBody(required = false) request: AccountStatusDecisionRequest?,
    ): ResponseEntity<AccountStatusRequestDto> =
        ResponseEntity.ok(accountStatusService.reject(id, request))
}
