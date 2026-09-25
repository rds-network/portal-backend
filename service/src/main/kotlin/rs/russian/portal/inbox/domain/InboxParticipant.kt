package rs.russian.portal.inbox.domain

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import rs.russian.portal.shared.jpa.JpaEntity
import java.time.LocalDateTime
import java.util.UUID

@Entity
class InboxParticipant(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id")
    var thread: InboxThread,

    var username: String,

    var unread: Boolean = true,
) : JpaEntity<UUID>() {

    override fun equalityProperties() = setOf(InboxParticipant::id)
}
