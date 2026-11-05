package rs.russian.portal.potvrda.domain

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import rs.russian.portal.shared.jpa.JpaEntity
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

enum class PotvrdaKind {
    OFFICIAL,
    MEMBERSHIP,
}

@Entity
class PotvrdaIssue(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    var createTime: OffsetDateTime = OffsetDateTime.now(),
    var issuedBy: String,

    var confirmNumber: String,
    var registryYear: Int,
    var sequenceNumber: Int,

    @Enumerated(EnumType.STRING)
    var kind: PotvrdaKind,

    var volunteerUsername: String,
    var volunteerFullName: String,
    var issueDate: LocalDate,

    var periodFrom: LocalDate? = null,
    var periodTo: LocalDate? = null,
    var totalHours: BigDecimal? = null,
    var programCode: String? = null,
    var servicesDescription: String? = null,
) : JpaEntity<UUID>() {

    override fun equalityProperties() = setOf(PotvrdaIssue::id)
}
