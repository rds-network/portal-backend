package rs.russian.portal.yearreview.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import rs.russian.portal.shared.jpa.JpaEntity
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "year_review_criteria")
class YearReviewCriteria(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    @Column(nullable = false, unique = true)
    var year: Int,

    @Column(name = "top_rank_max", nullable = false)
    var topRankMax: Int = 10,

    @Column(name = "min_accepted_reports", nullable = false)
    var minAcceptedReports: Int = 3,

    @Column(name = "min_earned_points", nullable = false)
    var minEarnedPoints: Int = 150,

    @Column(name = "min_active_weeks", nullable = false)
    var minActiveWeeks: Int = 20,

    @Column(name = "min_badges", nullable = false)
    var minBadges: Int = 5,

    @Column(name = "min_criteria_met", nullable = false)
    var minCriteriaMet: Int = 2,

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) : JpaEntity<UUID>() {
    override fun equalityProperties() = setOf(YearReviewCriteria::id)
}
