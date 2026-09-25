package rs.russian.portal.orglink.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.orglink.service.OrgLinkService
import java.util.UUID

@RestController
@RequestMapping("/org-links")
class OrgLinkController(
    private val orgLinkService: OrgLinkService,
) {

    @GetMapping
    fun list(): ResponseEntity<List<OrgLinkDto>> =
        ResponseEntity.ok(orgLinkService.list())

    @PostMapping
    fun create(@RequestBody request: OrgLinkWriteRequest): ResponseEntity<OrgLinkDto> =
        ResponseEntity.ok(orgLinkService.create(request))

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: OrgLinkWriteRequest,
    ): ResponseEntity<OrgLinkDto> =
        ResponseEntity.ok(orgLinkService.update(id, request))

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        orgLinkService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
