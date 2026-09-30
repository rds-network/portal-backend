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

    var recipient: String? = null,

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
        const val KIND_REPORT_CUSTOMER = "REPORT_CUSTOMER"
        const val KIND_REPORT_DECISION = "REPORT_DECISION"
        const val KIND_LEAVE_REQUEST = "LEAVE_REQUEST"
        const val KIND_LEAVE_DECISION = "LEAVE_DECISION"
        const val KIND_DISSOLUTION_REQUEST = "DISSOLUTION_REQUEST"
        const val KIND_DISSOLUTION_DECISION = "DISSOLUTION_DECISION"
        const val KIND_ACCOUNT_DEACTIVATED = "ACCOUNT_DEACTIVATED"
        const val KIND_ACCOUNT_STATUS_REQUEST = "ACCOUNT_STATUS_REQUEST"
        const val KIND_ACCOUNT_STATUS_DECISION = "ACCOUNT_STATUS_DECISION"
        const val KIND_ACCOUNT_STATUS_CHANGED = "ACCOUNT_STATUS_CHANGED"
        const val KIND_TALENT_RESPONSE = "TALENT_RESPONSE"
        const val KIND_TALENT_POST = "TALENT_POST"
    }
}
