package rs.russian.portal.chat.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import rs.russian.portal.chat.domain.ChatMessage
import java.time.OffsetDateTime
import java.util.UUID

@Repository
interface ChatMessageRepository : JpaRepository<ChatMessage, UUID> {

    fun findByRoomIdOrderByCreatedAtDesc(roomId: UUID, pageable: Pageable): List<ChatMessage>

    fun findByRoomIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(
        roomId: UUID,
        createdAt: OffsetDateTime,
        pageable: Pageable,
    ): List<ChatMessage>
}
