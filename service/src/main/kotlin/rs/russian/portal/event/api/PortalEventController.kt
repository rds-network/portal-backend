package rs.russian.portal.event.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.event.service.PortalEventService
import java.util.UUID

@RestController
@RequestMapping("/events")
class PortalEventController(
    private val portalEventService: PortalEventService,
) {

    @GetMapping
    fun list(
        @RequestParam(required = false, defaultValue = "true") upcoming: Boolean,
        @RequestParam(required = false, defaultValue = "20") limit: Int,
    ): ResponseEntity<List<PortalEventDto>> =
        ResponseEntity.ok(
            if (upcoming) portalEventService.listUpcoming(limit) else portalEventService.listAll()
        )

    @PostMapping
    fun create(@RequestBody request: PortalEventWriteRequest): ResponseEntity<PortalEventDto> =
        ResponseEntity.ok(portalEventService.create(request))

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: PortalEventWriteRequest,
    ): ResponseEntity<PortalEventDto> =
        ResponseEntity.ok(portalEventService.update(id, request))

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        portalEventService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
