package rs.russian.portal.talent.domain

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import rs.russian.portal.shared.jpa.JpaEntity
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "talent_response")
class TalentResponse(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    var post: TalentPost,

    var authorUsername: String,

    var message: String,

    var createdAt: OffsetDateTime = OffsetDateTime.now(),
) : JpaEntity<UUID>() {

    override fun equalityProperties() = setOf(TalentResponse::id)
}
