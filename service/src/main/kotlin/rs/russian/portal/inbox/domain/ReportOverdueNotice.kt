package rs.russian.portal.inbox.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import rs.russian.portal.shared.jpa.JpaEntity
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Entity
class ReportOverdueNotice(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    var username: String,

    var level: Int,

    var periodKey: String,

    var sentAt: OffsetDateTime = OffsetDateTime.now(),
) : JpaEntity<UUID>() {

    override fun equalityProperties() = setOf(ReportOverdueNotice::id)
}
