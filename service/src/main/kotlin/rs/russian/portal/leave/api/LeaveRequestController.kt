package rs.russian.portal.leave.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.leave.service.LeaveRequestService
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/leave-requests")
class LeaveRequestController(
    private val leaveRequestService: LeaveRequestService,
) {

    @PostMapping
    fun create(@RequestBody request: LeaveRequestCreateRequest): ResponseEntity<LeaveRequestDto> =
        ResponseEntity.ok(leaveRequestService.create(request))

    @GetMapping("/mine")
    fun mine(): ResponseEntity<List<LeaveRequestDto>> =
        ResponseEntity.ok(leaveRequestService.mine())

    @GetMapping("/pending")
    fun pending(): ResponseEntity<List<LeaveRequestDto>> =
        ResponseEntity.ok(leaveRequestService.pending())

    @GetMapping("/history")
    fun history(): ResponseEntity<List<LeaveRequestDto>> =
        ResponseEntity.ok(leaveRequestService.history())

    @PostMapping("/{id}/accept")
    fun accept(@PathVariable id: UUID): ResponseEntity<LeaveRequestDto> =
        ResponseEntity.ok(leaveRequestService.accept(id))

    @PostMapping("/{id}/reject")
    fun reject(
        @PathVariable id: UUID,
        @RequestBody(required = false) request: LeaveRequestRejectRequest?,
    ): ResponseEntity<LeaveRequestDto> =
        ResponseEntity.ok(leaveRequestService.reject(id, request))

    @GetMapping("/active")
    fun active(
        @RequestParam(required = false) username: String?,
        @RequestParam(required = false) from: LocalDate?,
        @RequestParam(required = false) to: LocalDate?,
    ): ResponseEntity<List<LeaveRequestDto>> =
        ResponseEntity.ok(leaveRequestService.active(username, from, to))
}
