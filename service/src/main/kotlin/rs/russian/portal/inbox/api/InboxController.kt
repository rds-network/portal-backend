package rs.russian.portal.inbox.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.inbox.service.InboxService
import rs.russian.portal.shared.security.Authorized
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import rs.russian.portal.user.domain.enums.UserGroup.MAIN_VOLUNTEER
import java.util.UUID

@RestController
@RequestMapping("/inbox")
class InboxController(
    private val inboxService: InboxService,
) {

    @GetMapping
    fun list(): ResponseEntity<List<InboxThreadDto>> =
        ResponseEntity.ok(inboxService.list())

    @GetMapping("/unread-count")
    fun unread(): ResponseEntity<InboxUnreadDto> =
        ResponseEntity.ok(InboxUnreadDto(inboxService.unreadCount()))

    @GetMapping("/pending-ack")
    fun pendingAck(): ResponseEntity<InboxUnreadDto> =
        ResponseEntity.ok(InboxUnreadDto(inboxService.pendingAckCount()))

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ResponseEntity<InboxThreadDetailDto> =
        ResponseEntity.ok(inboxService.get(id))

    @Authorized(allowed = [ADMIN, ADMIN_VOLUNTEER, MAIN_VOLUNTEER])
    @PostMapping
    fun create(@RequestBody request: InboxCreateRequest): ResponseEntity<List<InboxThreadDto>> =
        ResponseEntity.ok(inboxService.create(request))

    @PostMapping("/{id}/reply")
    fun reply(
        @PathVariable id: UUID,
        @RequestBody request: InboxReplyRequest,
    ): ResponseEntity<InboxThreadDetailDto> =
        ResponseEntity.ok(inboxService.reply(id, request))

    @PostMapping("/{id}/ack")
    fun ack(@PathVariable id: UUID): ResponseEntity<InboxThreadDetailDto> =
        ResponseEntity.ok(inboxService.ack(id))

    @Authorized(allowed = [ADMIN, ADMIN_VOLUNTEER, MAIN_VOLUNTEER])
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        inboxService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
