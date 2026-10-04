package rs.russian.portal.program.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import rs.russian.portal.shared.jpa.JpaEntity
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "program_curator")
class ProgramCurator(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    var programCode: String,

    var username: String,
) : JpaEntity<UUID>() {

    override fun equalityProperties() = setOf(ProgramCurator::programCode, ProgramCurator::username)
}
