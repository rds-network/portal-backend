package rs.russian.portal.event.domain

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import rs.russian.portal.event.domain.enums.PortalEventType
import rs.russian.portal.shared.jpa.JpaEntity
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "portal_event")
class PortalEvent(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    var createTime: OffsetDateTime = OffsetDateTime.now(),

    var createdBy: String,

    var title: String,

    var description: String? = null,

    var startsAt: OffsetDateTime,

    var location: String? = null,

    @Enumerated(EnumType.STRING)
    var type: PortalEventType = PortalEventType.OTHER,

    var programCode: String? = null,
) : JpaEntity<UUID>() {

    override fun equalityProperties() = setOf(PortalEvent::id)
}
