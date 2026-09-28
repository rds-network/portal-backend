package rs.russian.portal.chat.domain

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import rs.russian.portal.chat.domain.enums.ChatRoomType
import rs.russian.portal.program.domain.Program
import rs.russian.portal.shared.jpa.JpaEntity
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "chat_room")
class ChatRoom(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    @Enumerated(EnumType.STRING)
    var type: ChatRoomType,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_code")
    var program: Program? = null,

    var title: String,

    var createdBy: String,

    var createdAt: OffsetDateTime = OffsetDateTime.now(),
) : JpaEntity<UUID>() {

    override fun equalityProperties() = setOf(ChatRoom::id)
}
