package rs.russian.portal.missions.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.missions.service.PointMissionService
import rs.russian.portal.shared.security.Authorized
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_SSO
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import rs.russian.portal.user.domain.enums.UserGroup.MAIN_VOLUNTEER
import java.util.UUID

@RestController
@RequestMapping("/admin/point-missions")
class AdminPointMissionController(
    private val pointMissionService: PointMissionService,
) {
    @GetMapping
    @Authorized(allowed = [ADMIN, ADMIN_SSO, ADMIN_VOLUNTEER, MAIN_VOLUNTEER])
    fun list(): ResponseEntity<List<PointMissionDto>> =
        ResponseEntity.ok(pointMissionService.listAdmin())

    @GetMapping("/submissions/pending")
    @Authorized(allowed = [ADMIN, ADMIN_SSO, ADMIN_VOLUNTEER, MAIN_VOLUNTEER])
    fun pendingSubmissions(): ResponseEntity<List<PointMissionSubmissionDto>> =
        ResponseEntity.ok(pointMissionService.listPendingSubmissions())

    @PostMapping("/submissions/{id}/approve")
    @Authorized(allowed = [ADMIN, ADMIN_SSO, ADMIN_VOLUNTEER, MAIN_VOLUNTEER])
    fun approve(@PathVariable id: UUID): ResponseEntity<PointMissionSubmissionDto> =
        ResponseEntity.ok(pointMissionService.approveSubmission(id))

    @PostMapping("/submissions/{id}/reject")
    @Authorized(allowed = [ADMIN, ADMIN_SSO, ADMIN_VOLUNTEER, MAIN_VOLUNTEER])
    fun reject(
        @PathVariable id: UUID,
        @RequestBody(required = false) request: PointMissionRejectRequest?,
    ): ResponseEntity<PointMissionSubmissionDto> =
        ResponseEntity.ok(pointMissionService.rejectSubmission(id, request ?: PointMissionRejectRequest()))

    @PostMapping
    @Authorized(allowed = [ADMIN, ADMIN_SSO, ADMIN_VOLUNTEER, MAIN_VOLUNTEER])
    fun create(@RequestBody request: PointMissionWriteRequest): ResponseEntity<PointMissionDto> =
        ResponseEntity.ok(pointMissionService.create(request))

    @PatchMapping("/{id}")
    @Authorized(allowed = [ADMIN, ADMIN_SSO, ADMIN_VOLUNTEER, MAIN_VOLUNTEER])
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: PointMissionWriteRequest,
    ): ResponseEntity<PointMissionDto> =
        ResponseEntity.ok(pointMissionService.update(id, request))

    @DeleteMapping("/{id}")
    @Authorized(allowed = [ADMIN, ADMIN_SSO, ADMIN_VOLUNTEER, MAIN_VOLUNTEER])
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        pointMissionService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
