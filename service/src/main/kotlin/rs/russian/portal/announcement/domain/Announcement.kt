package rs.russian.portal.announcement.domain

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import org.hibernate.annotations.SQLRestriction
import rs.russian.portal.announcement.domain.enums.AnnouncementAudience
import rs.russian.portal.program.domain.Program
import rs.russian.portal.shared.jpa.JpaEntity
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@SQLRestriction("active = true")
class Announcement(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    var createTime: OffsetDateTime = OffsetDateTime.now(),

    var createdBy: String,

    var title: String,

    var body: String,

    @Enumerated(EnumType.STRING)
    var audience: AnnouncementAudience,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_code")
    var program: Program? = null,

    var targetUsername: String? = null,

    var banner: Boolean = false,

    var active: Boolean = true,
) : JpaEntity<UUID>() {

    override fun equalityProperties() = setOf(Announcement::id, Announcement::title)
}
