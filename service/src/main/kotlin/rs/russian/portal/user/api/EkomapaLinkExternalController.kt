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
            .ifEmpty { body.path("username").asText("").trim() }
            .ifEmpty { body.path("login").asText("").trim() }
        val email = sequenceOf("email", "mail", "userEmail")
            .map { body.path(it).asText("").trim() }
            .firstOrNull { it.isNotEmpty() }
        val idNode = when {
            !body.path("ekomapaUserId").isMissingNode -> body.path("ekomapaUserId")
            !body.path("ekomapa_user_id").isMissingNode -> body.path("ekomapa_user_id")
            !body.path("evoId").isMissingNode -> body.path("evoId")
            else -> body.path("ekomapaUserId")
        }
        val ekomapaUserId = when {
            idNode.canConvertToInt() -> idNode.asInt()
            idNode.isTextual -> idNode.asText("").trim().toIntOrNull() ?: 0
            idNode.isNumber -> idNode.intValue()
            else -> 0
        }
        if (ekomapaUserId <= 0) {
            throw InvalidRequestException(
                "ekomapaUserId must be a positive integer (got keys=${body.fieldNames().asSequence().toList()})",
            )
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
