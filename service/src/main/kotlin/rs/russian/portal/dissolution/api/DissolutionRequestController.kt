package rs.russian.portal.dissolution.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.dissolution.service.DissolutionRequestService
import java.util.UUID

@RestController
@RequestMapping("/dissolution-requests")
class DissolutionRequestController(
    private val dissolutionRequestService: DissolutionRequestService,
) {

    @PostMapping
    fun create(@RequestBody request: DissolutionRequestCreateRequest): ResponseEntity<DissolutionRequestDto> =
        ResponseEntity.ok(dissolutionRequestService.create(request))

    @GetMapping("/mine")
    fun mine(): ResponseEntity<List<DissolutionRequestDto>> =
        ResponseEntity.ok(dissolutionRequestService.mine())

    @GetMapping("/pending")
    fun pending(): ResponseEntity<List<DissolutionRequestDto>> =
        ResponseEntity.ok(dissolutionRequestService.pending())

    @GetMapping("/history")
    fun history(): ResponseEntity<List<DissolutionRequestDto>> =
        ResponseEntity.ok(dissolutionRequestService.history())

    @PostMapping("/{id}/accept")
    fun accept(@PathVariable id: UUID): ResponseEntity<DissolutionRequestDto> =
        ResponseEntity.ok(dissolutionRequestService.accept(id))

    @PostMapping("/{id}/reject")
    fun reject(
        @PathVariable id: UUID,
        @RequestBody(required = false) request: DissolutionRequestRejectRequest?,
    ): ResponseEntity<DissolutionRequestDto> =
        ResponseEntity.ok(dissolutionRequestService.reject(id, request))

    @PostMapping("/{id}/cancel")
    fun cancel(@PathVariable id: UUID): ResponseEntity<DissolutionRequestDto> =
        ResponseEntity.ok(dissolutionRequestService.cancel(id))
}
