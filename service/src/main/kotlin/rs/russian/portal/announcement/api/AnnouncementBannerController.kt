package rs.russian.portal.announcement.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.generated.model.AnnouncementDto
import rs.russian.portal.announcement.service.AnnouncementService

@RestController
@RequestMapping("/announcements")
class AnnouncementBannerController(
    private val announcementService: AnnouncementService,
) {

    @GetMapping("/banner")
    fun banner(): ResponseEntity<BannerDto> {
        val banner = announcementService.getActiveBanner() ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(banner)
    }

    @PostMapping("/publish")
    fun publish(@RequestBody request: AnnouncementPublishRequest): ResponseEntity<AnnouncementDto> =
        ResponseEntity.ok(
            announcementService.publish(
                title = request.title,
                body = request.body,
                audienceName = request.audience,
                programCode = request.programCode,
                username = request.username,
                banner = request.banner,
            )
        )
}
