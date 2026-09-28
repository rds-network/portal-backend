package rs.russian.portal.chat.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import rs.russian.portal.chat.domain.ChatRoom
import rs.russian.portal.chat.domain.enums.ChatRoomType
import java.util.Optional
import java.util.UUID

@Repository
interface ChatRoomRepository : JpaRepository<ChatRoom, UUID> {

    fun findByType(type: ChatRoomType): Optional<ChatRoom>

    fun findByTypeAndProgram_CodeIgnoreCase(type: ChatRoomType, programCode: String): Optional<ChatRoom>

    fun findAllByOrderByTypeAscTitleAsc(): List<ChatRoom>

    @Query(
        """
        SELECT r FROM ChatRoom r
        LEFT JOIN FETCH r.program
        ORDER BY r.type ASC, r.title ASC
        """
    )
    fun findAllWithProgram(): List<ChatRoom>

    @Query(
        """
        SELECT r FROM ChatRoom r
        LEFT JOIN FETCH r.program
        WHERE r.id = :id
        """
    )
    fun findByIdWithProgram(@Param("id") id: UUID): Optional<ChatRoom>
}
