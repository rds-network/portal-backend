package rs.russian.portal.orglink.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import rs.russian.portal.shared.jpa.JpaEntity
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Entity
class OrgLink(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    var createTime: OffsetDateTime = OffsetDateTime.now(),

    var createdBy: String,

    var title: String,

    var url: String,

    var description: String? = null,

    var sortOrder: Int = 0,
) : JpaEntity<UUID>() {

    override fun equalityProperties() = setOf(OrgLink::id)
}
