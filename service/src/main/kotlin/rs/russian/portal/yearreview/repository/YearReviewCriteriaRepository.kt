package rs.russian.portal.yearreview.repository

import org.springframework.data.jpa.repository.JpaRepository
import rs.russian.portal.yearreview.domain.YearReviewCriteria
import java.util.Optional
import java.util.UUID

interface YearReviewCriteriaRepository : JpaRepository<YearReviewCriteria, UUID> {
    fun findByYear(year: Int): Optional<YearReviewCriteria>
}
