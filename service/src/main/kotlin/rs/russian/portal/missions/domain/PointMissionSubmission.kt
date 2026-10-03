package rs.russian.portal.missions.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "point_mission_submission")
class PointMissionSubmission(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(name = "mission_id", nullable = false)
    var missionId: UUID,

    @Column(nullable = false, length = 255)
    var username: String,

    @Column(name = "proof_text", nullable = false, length = 500)
    var proofText: String,

    /** PENDING | APPROVED | REJECTED */
    @Column(nullable = false, length = 20)
    var status: String = "PENDING",

    @Column(name = "reject_reason", length = 500)
    var rejectReason: String? = null,

    @Column(name = "reviewed_by", length = 255)
    var reviewedBy: String? = null,

    @Column(name = "reviewed_at")
    var reviewedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)
