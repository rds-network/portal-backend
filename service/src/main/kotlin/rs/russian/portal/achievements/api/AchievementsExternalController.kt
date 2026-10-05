package rs.russian.portal.achievements.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.achievements.service.AchievementsService
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.security.AuthorizedService

@RestController
@RequestMapping("/ext")
class AchievementsExternalController(
    private val achievementsService: AchievementsService,
) {
    @AuthorizedService
    @PostMapping("/achievements/award")
    fun award(@RequestBody request: ExtPointAwardRequest): ResponseEntity<ExtPointAwardResponse> {
        val user = request.user.trim()
        if (user.isEmpty()) throw InvalidRequestException("user is required")
        if (request.code.isBlank()) throw InvalidRequestException("code is required")
        if (request.refId.isBlank()) throw InvalidRequestException("refId is required")
        if (request.points == 0) throw InvalidRequestException("points must be non-zero")
        val (balance, awarded) = achievementsService.awardExternal(
            username = user,
            code = request.code.trim(),
            points = request.points,
            refId = request.refId.trim(),
            title = request.title.trim().ifBlank { request.code },
        )
        return ResponseEntity.ok(
            ExtPointAwardResponse(
                username = user.lowercase(),
                balance = balance,
                awarded = awarded,
            )
        )
    }
}
