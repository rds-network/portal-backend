package rs.russian.portal.chat.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import rs.russian.portal.chat.domain.ChatRoomRead
import rs.russian.portal.chat.domain.ChatRoomReadId
import java.util.UUID

@Repository
interface ChatRoomReadRepository : JpaRepository<ChatRoomRead, ChatRoomReadId> {

    fun findByUsernameIgnoreCaseAndRoomId(username: String, roomId: UUID): ChatRoomRead?

    @Query(
        value = """
            SELECT COUNT(*)
            FROM chat_message m
            LEFT JOIN chat_room_read r
              ON r.room_id = m.room_id AND LOWER(r.username) = LOWER(:username)
            WHERE m.room_id IN (:roomIds)
              AND LOWER(m.author_username) <> LOWER(:username)
              AND (r.last_read_at IS NULL OR m.created_at > r.last_read_at)
            """,
        nativeQuery = true,
    )
    fun countUnread(
        @Param("username") username: String,
        @Param("roomIds") roomIds: Collection<UUID>,
    ): Long
}
