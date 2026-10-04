package rs.russian.portal.program.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import rs.russian.portal.shared.jpa.JpaEntity
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "program_curator_delegate")
class ProgramCuratorDelegate(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    var programCode: String,

    var curatorUsername: String,

    var delegateUsername: String,

    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    var createdBy: String? = null,
) : JpaEntity<UUID>() {

    override fun equalityProperties() =
        setOf(
            ProgramCuratorDelegate::programCode,
            ProgramCuratorDelegate::curatorUsername,
            ProgramCuratorDelegate::delegateUsername,
        )
}
