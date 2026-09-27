package rs.russian.portal.dissolution.domain

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import rs.russian.portal.dissolution.domain.enums.DissolutionRequestStatus
import rs.russian.portal.shared.jpa.JpaEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "dissolution_request")
class DissolutionRequest(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    var username: String,

    var fromDate: LocalDate,

    @Enumerated(EnumType.STRING)
    var status: DissolutionRequestStatus = DissolutionRequestStatus.PENDING,

    var reason: String? = null,

    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    var decidedAt: OffsetDateTime? = null,

    var decidedBy: String? = null,

    var decisionReason: String? = null,
) : JpaEntity<UUID>() {

    override fun equalityProperties() = setOf(DissolutionRequest::id)
}
