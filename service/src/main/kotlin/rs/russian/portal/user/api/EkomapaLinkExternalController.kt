package rs.russian.portal.user.api

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.security.AuthorizedService
import rs.russian.portal.user.EkomapaVolunteerCode
import rs.russian.portal.user.service.AccountService

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
    /**
     * JsonNode avoids Kotlin Map/data-class Jackson 400s on the service-account sync path.
     * Expected JSON: { "user": "login-or-email", "ekomapaUserId": 32, "email": "optional@…" }
     */
    @AuthorizedService
    @PostMapping("/ekomapa/link")
    fun link(@RequestBody body: JsonNode): ResponseEntity<ExtEkomapaLinkResponse> {
        val user = body.path("user").asText("").trim()
        val email = body.path("email").asText("").trim().takeIf { it.isNotEmpty() }
        val ekomapaUserId = when {
            body.path("ekomapaUserId").canConvertToInt() -> body.path("ekomapaUserId").asInt()
            body.path("ekomapa_user_id").canConvertToInt() -> body.path("ekomapa_user_id").asInt()
            else -> 0
        }
        if (ekomapaUserId <= 0) {
            throw InvalidRequestException("ekomapaUserId must be a positive integer")
        }
        if (user.isEmpty() && email.isNullOrBlank()) {
            throw InvalidRequestException("user or email is required")
        }
        val account = accountService.linkEkomapaUserId(
            username = user.ifEmpty { email!! },
            ekomapaUserId = ekomapaUserId,
            email = email,
        )
        return ResponseEntity.ok(
            ExtEkomapaLinkResponse(
                username = account.username,
                ekomapaUserId = ekomapaUserId,
                evoCode = EkomapaVolunteerCode.format(ekomapaUserId),
            )
        )
    }
}
