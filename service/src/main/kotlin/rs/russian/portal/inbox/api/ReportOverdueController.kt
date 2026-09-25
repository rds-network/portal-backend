package rs.russian.portal.inbox.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.inbox.service.ReportOverdueService
import rs.russian.portal.shared.security.Authorized
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import rs.russian.portal.user.domain.enums.UserGroup.MAIN_VOLUNTEER

@RestController
@RequestMapping("/report-overdue")
class ReportOverdueController(
    private val reportOverdueService: ReportOverdueService,
) {

    @Authorized(allowed = [ADMIN, ADMIN_VOLUNTEER, MAIN_VOLUNTEER])
    @GetMapping
    fun list(): ResponseEntity<List<ReportOverdueDto>> =
        ResponseEntity.ok(reportOverdueService.list())

    @Authorized(allowed = [ADMIN, ADMIN_VOLUNTEER, MAIN_VOLUNTEER])
    @GetMapping("/preview")
    fun preview(): ResponseEntity<OverduePreviewDto> =
        ResponseEntity.ok(reportOverdueService.preview())

    @Authorized(allowed = [ADMIN, ADMIN_VOLUNTEER, MAIN_VOLUNTEER])
    @PostMapping("/notify")
    fun notifyNow(@RequestBody(required = false) request: OverdueNotifyRequest?): ResponseEntity<Map<String, Int>> =
        ResponseEntity.ok(mapOf("sent" to reportOverdueService.notifyDue(request?.exclude.orEmpty())))
}
