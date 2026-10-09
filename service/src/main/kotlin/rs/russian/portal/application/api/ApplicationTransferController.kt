package rs.russian.portal.application.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.application.service.ApplicationTransferService
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.security.Authorized
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import rs.russian.portal.user.domain.enums.UserGroup.INTERVIEWER
import java.time.LocalDate

data class ApplicationTransferRequest(
    val from: String,
    val to: String,
    /** ISO date yyyy-MM-dd; optional, defaults to today. */
    val date: String? = null,
)

data class ApplicationTransferResponse(
    val moved: Int,
)

@RestController
@RequestMapping("/application")
class ApplicationTransferController(
    private val applicationTransferService: ApplicationTransferService,
) {
    @Authorized(allowed = [ADMIN_VOLUNTEER, INTERVIEWER])
    @PostMapping("/transfer")
    fun transfer(@RequestBody request: ApplicationTransferRequest): ResponseEntity<ApplicationTransferResponse> {
        val from = request.from.trim()
        val to = request.to.trim()
        if (from.isEmpty() || to.isEmpty()) throw InvalidRequestException("from and to are required")
        val date = request.date?.trim()?.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) }
        val result = applicationTransferService.transfer(from, to, date)
        return ResponseEntity.ok(ApplicationTransferResponse(moved = result.moved))
    }

    @Authorized(allowed = [ADMIN_VOLUNTEER, INTERVIEWER])
    @PostMapping("/transfer/revert")
    fun revert(@RequestBody request: ApplicationTransferRequest): ResponseEntity<ApplicationTransferResponse> {
        val from = request.from.trim()
        val to = request.to.trim()
        if (from.isEmpty() || to.isEmpty()) throw InvalidRequestException("from and to are required")
        val result = applicationTransferService.revert(from, to)
        return ResponseEntity.ok(ApplicationTransferResponse(moved = result.moved))
    }
}
