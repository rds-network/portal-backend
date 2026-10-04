package rs.russian.portal.achievements.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.achievements.service.AchievementsService

@RestController
@RequestMapping("/achievements")
class AchievementsController(
    private val achievementsService: AchievementsService,
) {
    @GetMapping("/me")
    fun me(): ResponseEntity<AchievementsMeDto> =
        ResponseEntity.ok(achievementsService.me())
}
