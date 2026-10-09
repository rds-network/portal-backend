package rs.russian.portal.user.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.security.AuthorizedService
import rs.russian.portal.user.EkomapaVolunteerCode
import rs.russian.portal.user.service.AccountService

data class ExtEkomapaLinkRequest(
    val user: String,
    val ekomapaUserId: Int,
)

data class ExtEkomapaLinkResponse(
    val username: String,
    val ekomapaUserId: Int,
    val evoCode: String,
)

@RestController
@RequestMapping("/ext")
class EkomapaLinkExternalController(
    private val accountService: AccountService,
) {
    @AuthorizedService
    @PostMapping("/ekomapa/link")
    fun link(@RequestBody request: ExtEkomapaLinkRequest): ResponseEntity<ExtEkomapaLinkResponse> {
        val user = request.user.trim()
        if (user.isEmpty()) throw InvalidRequestException("user is required")
        if (request.ekomapaUserId <= 0) throw InvalidRequestException("ekomapaUserId must be positive")
        val account = accountService.linkEkomapaUserId(user, request.ekomapaUserId)
        return ResponseEntity.ok(
            ExtEkomapaLinkResponse(
                username = account.username,
                ekomapaUserId = request.ekomapaUserId,
                evoCode = EkomapaVolunteerCode.format(request.ekomapaUserId),
            )
        )
    }
}
