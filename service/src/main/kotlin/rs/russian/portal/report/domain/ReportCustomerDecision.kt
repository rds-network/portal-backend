package rs.russian.portal.report.domain

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.shared.jpa.JpaEntity
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "report_customer_decision")
class ReportCustomerDecision(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    var report: Report,

    var customerUsername: String,

    @Enumerated(EnumType.STRING)
    var status: ReportStatus,

    var decidedBy: String,

    var decidedAt: OffsetDateTime = OffsetDateTime.now(),

    var note: String? = null,
) : JpaEntity<UUID>() {

    override fun equalityProperties() = setOf(ReportCustomerDecision::id)
}
