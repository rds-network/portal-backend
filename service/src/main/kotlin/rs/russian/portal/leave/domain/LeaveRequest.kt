package rs.russian.portal.leave.domain

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import rs.russian.portal.leave.domain.enums.LeaveRequestStatus
import rs.russian.portal.shared.jpa.JpaEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "leave_request")
class LeaveRequest(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    var username: String,

    var startDate: LocalDate,

    var endDate: LocalDate,

    @Enumerated(EnumType.STRING)
    var status: LeaveRequestStatus = LeaveRequestStatus.PENDING,

    var reason: String? = null,

    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    var decidedAt: OffsetDateTime? = null,

    var decidedBy: String? = null,
) : JpaEntity<UUID>() {

    override fun equalityProperties() = setOf(LeaveRequest::id)
}
