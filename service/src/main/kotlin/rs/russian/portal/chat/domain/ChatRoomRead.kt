package rs.russian.portal.chat.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "chat_room_read")
@IdClass(ChatRoomReadId::class)
class ChatRoomRead(
    @Id
    var username: String = "",

    @Id
    @Column(name = "room_id")
    var roomId: UUID = UUID(0, 0),

    @Column(name = "last_read_at", nullable = false)
    var lastReadAt: OffsetDateTime = OffsetDateTime.now(),
)
