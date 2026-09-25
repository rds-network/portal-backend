package rs.russian.portal.workassignment.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.shared.security.Authorized
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import rs.russian.portal.user.domain.enums.UserGroup.MAIN_VOLUNTEER
import rs.russian.portal.workassignment.service.WorkAssignmentService
import java.util.UUID

@RestController
@RequestMapping("/work-assignments")
class WorkAssignmentController(
    private val workAssignmentService: WorkAssignmentService,
) {

    @GetMapping
    fun list(): ResponseEntity<List<WorkAssignmentDto>> =
        ResponseEntity.ok(workAssignmentService.list())

    @Authorized(allowed = [ADMIN, ADMIN_VOLUNTEER, MAIN_VOLUNTEER])
    @PostMapping
    fun create(@RequestBody request: WorkAssignmentCreateRequest): ResponseEntity<WorkAssignmentDto> =
        ResponseEntity.ok(workAssignmentService.create(request))

    @PatchMapping("/{id}")
    fun patch(
        @PathVariable id: UUID,
        @RequestBody request: WorkAssignmentPatchRequest,
    ): ResponseEntity<WorkAssignmentDto> =
        ResponseEntity.ok(workAssignmentService.patch(id, request))
}
