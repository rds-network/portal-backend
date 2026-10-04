package rs.russian.portal.talent.domain

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import rs.russian.portal.program.domain.Program
import rs.russian.portal.shared.jpa.JpaEntity
import rs.russian.portal.talent.domain.enums.TalentPostStatus
import rs.russian.portal.talent.domain.enums.TalentPostType
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "talent_post")
class TalentPost(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    @Enumerated(EnumType.STRING)
    var type: TalentPostType,

    @Enumerated(EnumType.STRING)
    var status: TalentPostStatus = TalentPostStatus.OPEN,

    var title: String,

    var body: String,

    var city: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_code")
    var program: Program? = null,

    /** Comma-separated skill tags. */
    var skills: String? = null,

    var authorUsername: String,

    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    var updatedAt: OffsetDateTime = OffsetDateTime.now(),

    var responseCount: Int = 0,
) : JpaEntity<UUID>() {

    override fun equalityProperties() = setOf(TalentPost::id)
}
