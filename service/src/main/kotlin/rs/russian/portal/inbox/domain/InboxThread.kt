package rs.russian.portal.inbox.domain

import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import rs.russian.portal.shared.jpa.JpaEntity
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Entity
class InboxThread(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    var createTime: OffsetDateTime = OffsetDateTime.now(),

    var subject: String,

    var kind: String = KIND_MANUAL,

    var createdBy: String? = null,

    @OneToMany(mappedBy = "thread", cascade = [ALL], orphanRemoval = true)
    var participants: MutableList<InboxParticipant> = mutableListOf(),

    @OneToMany(mappedBy = "thread", cascade = [ALL], orphanRemoval = true)
    @OrderBy("createTime ASC")
    var messages: MutableList<InboxMessage> = mutableListOf(),
) : JpaEntity<UUID>() {

    override fun equalityProperties() = setOf(InboxThread::id)

    companion object {
        const val KIND_MANUAL = "MANUAL"
        const val KIND_TASK = "TASK"
        const val KIND_OVERDUE_HOURS = "OVERDUE_HOURS"
        const val KIND_OVERDUE_1 = "OVERDUE_1"
        const val KIND_OVERDUE_2 = "OVERDUE_2"
        const val KIND_OVERDUE_3 = "OVERDUE_3"
    }
}
