package rs.russian.portal.missions.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.missions.service.PointMissionService
import java.util.UUID

@RestController
@RequestMapping("/point-missions")
class PointMissionController(
    private val pointMissionService: PointMissionService,
) {
    @GetMapping
    fun list(): ResponseEntity<List<PointMissionDto>> =
        ResponseEntity.ok(pointMissionService.listForVolunteer())

    @PostMapping("/{id}/claim")
    fun claim(@PathVariable id: UUID): ResponseEntity<PointMissionClaimResult> =
        ResponseEntity.ok(pointMissionService.claim(id))

    @PostMapping("/{id}/submit")
    fun submit(
        @PathVariable id: UUID,
        @RequestBody request: PointMissionSubmitRequest,
    ): ResponseEntity<PointMissionClaimResult> =
        ResponseEntity.ok(pointMissionService.submit(id, request))
}
