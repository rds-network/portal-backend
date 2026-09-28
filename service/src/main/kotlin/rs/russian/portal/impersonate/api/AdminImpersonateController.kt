package rs.russian.portal.impersonate.api

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.impersonate.service.ImpersonationService

@RestController
@RequestMapping("/admin/impersonate")
class AdminImpersonateController(
    private val impersonationService: ImpersonationService,
) {

    @GetMapping("/status")
    fun status(request: HttpServletRequest): ResponseEntity<ImpersonationStatusDto> =
        ResponseEntity.ok(impersonationService.status(request))

    @PostMapping
    fun start(
        @RequestBody body: ImpersonationStartRequest,
        request: HttpServletRequest,
    ): ResponseEntity<ImpersonationStatusDto> =
        ResponseEntity.ok(impersonationService.start(body, request))

    @DeleteMapping
    fun stop(request: HttpServletRequest): ResponseEntity<ImpersonationStatusDto> =
        ResponseEntity.ok(impersonationService.stop(request))
}
