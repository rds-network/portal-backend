package rs.russian.portal.chat.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.chat.service.ChatService
import java.util.UUID

@RestController
@RequestMapping("/chat")
class ChatController(
    private val chatService: ChatService,
) {

    @GetMapping("/rooms")
    fun listRooms(): ResponseEntity<ChatRoomsResponse> =
        ResponseEntity.ok(chatService.listRooms())

    /** Managers and program curators — authorization enforced in [ChatService.createProgramRoom]. */
    @PostMapping("/rooms")
    fun createProgramRoom(@RequestBody request: ChatCreateProgramRoomRequest): ResponseEntity<ChatRoomDto> =
        ResponseEntity.ok(chatService.createProgramRoom(request))

    @GetMapping("/rooms/{roomId}/messages")
    fun listMessages(
        @PathVariable roomId: UUID,
        @RequestParam(required = false) afterId: UUID?,
        @RequestParam(required = false, defaultValue = "80") limit: Int,
    ): ResponseEntity<List<ChatMessageDto>> =
        ResponseEntity.ok(chatService.listMessages(roomId, afterId, limit))

    @PostMapping("/rooms/{roomId}/messages")
    fun sendMessage(
        @PathVariable roomId: UUID,
        @RequestBody request: ChatSendMessageRequest,
    ): ResponseEntity<ChatMessageDto> =
        ResponseEntity.ok(chatService.sendMessage(roomId, request))

    @GetMapping("/rooms/{roomId}/members")
    fun listMembers(@PathVariable roomId: UUID): ResponseEntity<List<ChatMemberDto>> =
        ResponseEntity.ok(chatService.listMembers(roomId))

    /** Heartbeat while the chat page is open — refreshes account.lastSeenAt. */
    @PostMapping("/presence")
    fun presence(): ResponseEntity<Void> {
        chatService.touchPresence()
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/unread")
    fun unread(): ResponseEntity<ChatUnreadResponse> =
        ResponseEntity.ok(chatService.unreadSummary())

    @PostMapping("/rooms/{roomId}/read")
    fun markRead(@PathVariable roomId: UUID): ResponseEntity<ChatOkResponse> {
        chatService.markRead(roomId)
        return ResponseEntity.ok(ChatOkResponse())
    }
}
