package rs.russian.portal.achievements.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "volunteer_point_event")
class VolunteerPointEvent(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var username: String,

    /** Stable rule code, e.g. WEEKLY_LOGIN, REPORT_CLEAN. */
    @Column(nullable = false, length = 64)
    var code: String,

    @Column(nullable = false)
    var points: Int,

    /** Idempotency key within code (ISO week, report id, …). */
    @Column(name = "ref_id", length = 128)
    var refId: String? = null,

    @Column(length = 255)
    var title: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)
