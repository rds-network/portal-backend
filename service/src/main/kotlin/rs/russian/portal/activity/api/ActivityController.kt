package rs.russian.portal.activity.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.activity.service.ActivityService
import rs.russian.portal.shared.security.Authorized
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_SSO
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER

@RestController
@RequestMapping("/activity")
class ActivityController(
    private val activityService: ActivityService,
) {

    @Authorized(allowed = [ADMIN, ADMIN_VOLUNTEER, ADMIN_SSO])
    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "createTime") sort: String,
        @RequestParam(defaultValue = "desc") dir: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): ResponseEntity<ActivityPageDto> {
        val result = activityService.list(q, sort, dir, page, size)
        return ResponseEntity.ok(
            ActivityPageDto(
                content = result.content,
                total = result.totalElements,
                page = result.number,
                size = result.size,
            )
        )
    }
}
