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
     * Body is parsed as a map so a Kotlin data-class / Jackson mismatch cannot 400 every sync call.
     * Expected JSON: { "user": "login-or-email", "ekomapaUserId": 32, "email": "optional@…" }
     */
    @AuthorizedService
    @PostMapping("/ekomapa/link")
    fun link(@RequestBody body: Map<String, Any?>): ResponseEntity<ExtEkomapaLinkResponse> {
        val user = (body["user"] as? String)?.trim().orEmpty()
        val email = (body["email"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val ekomapaUserId = parsePositiveInt(body["ekomapaUserId"])
            ?: throw InvalidRequestException("ekomapaUserId must be a positive integer")
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

    private fun parsePositiveInt(raw: Any?): Int? {
        val value = when (raw) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull()
            else -> null
        }
        return value?.takeIf { it > 0 }
    }
}
