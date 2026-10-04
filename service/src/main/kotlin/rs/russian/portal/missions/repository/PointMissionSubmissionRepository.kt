package rs.russian.portal.missions.repository

import org.springframework.data.jpa.repository.JpaRepository
import rs.russian.portal.missions.domain.PointMissionSubmission
import java.util.UUID

interface PointMissionSubmissionRepository : JpaRepository<PointMissionSubmission, UUID> {
    fun findByUsernameAndMissionIdOrderByCreatedAtDesc(
        username: String,
        missionId: UUID,
    ): List<PointMissionSubmission>

    fun findFirstByUsernameAndMissionIdAndStatusInOrderByCreatedAtDesc(
        username: String,
        missionId: UUID,
        statuses: Collection<String>,
    ): PointMissionSubmission?

    fun findByStatusOrderByCreatedAtAsc(status: String): List<PointMissionSubmission>

    fun findByStatusOrderByReviewedAtDescCreatedAtDesc(status: String): List<PointMissionSubmission>

    fun findFirstByUsernameAndMissionIdAndStatusOrderByReviewedAtDescCreatedAtDesc(
        username: String,
        missionId: UUID,
        status: String,
    ): PointMissionSubmission?

    fun existsByUsernameAndMissionIdAndStatus(username: String, missionId: UUID, status: String): Boolean
}
