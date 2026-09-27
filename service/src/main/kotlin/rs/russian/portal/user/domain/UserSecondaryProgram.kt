package rs.russian.portal.user.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import rs.russian.portal.shared.jpa.JpaEntity
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "user_secondary_program")
class UserSecondaryProgram(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    var accountId: Int,

    var programCode: String,
) : JpaEntity<UUID>() {

    override fun equalityProperties() = setOf(UserSecondaryProgram::accountId, UserSecondaryProgram::programCode)
}
