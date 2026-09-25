package rs.russian.portal.workassignment.domain

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import rs.russian.portal.shared.jpa.JpaEntity
import rs.russian.portal.workassignment.domain.enums.WorkAssignmentStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Entity
class WorkAssignment(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    var createTime: OffsetDateTime = OffsetDateTime.now(),

    var createdBy: String,

    var title: String,

    var body: String? = null,

    var assignee: String? = null,

    var assigneeName: String? = null,

    var customer: String? = null,

    var customerName: String? = null,

    @Enumerated(EnumType.STRING)
    var status: WorkAssignmentStatus = WorkAssignmentStatus.TODO,

    var dueDate: LocalDate? = null,

    var startedAt: OffsetDateTime? = null,

    var reportId: UUID? = null,
) : JpaEntity<UUID>() {

    override fun equalityProperties() = setOf(WorkAssignment::id, WorkAssignment::title)
}
