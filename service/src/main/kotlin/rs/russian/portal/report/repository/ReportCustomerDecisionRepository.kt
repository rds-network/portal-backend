package rs.russian.portal.report.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rs.russian.portal.report.domain.ReportCustomerDecision
import java.util.UUID

interface ReportCustomerDecisionRepository : JpaRepository<ReportCustomerDecision, UUID> {

    fun findAllByReportId(reportId: UUID): List<ReportCustomerDecision>

    fun findByReportIdAndCustomerUsernameIgnoreCase(
        reportId: UUID,
        customerUsername: String,
    ): ReportCustomerDecision?

    // clearAutomatically=false: callers often keep mutating the same Report instance after this delete;
    // clearing the persistence context would detach it and drop status/moderator updates.
    @Modifying(clearAutomatically = false, flushAutomatically = true)
    @Query("DELETE FROM ReportCustomerDecision d WHERE d.report.id = :reportId")
    fun deleteAllByReportId(@Param("reportId") reportId: UUID)

    @Query(
        """
        SELECT COUNT(d) FROM ReportCustomerDecision d
        WHERE LOWER(d.decidedBy) = LOWER(:username)
          AND d.status = rs.russian.portal.report.domain.enums.ReportStatus.ACCEPTED
          AND d.decidedAt >= :from AND d.decidedAt < :until
        """
    )
    fun countAcceptedDecisionsInRange(
        @Param("username") username: String,
        @Param("from") from: java.time.OffsetDateTime,
        @Param("until") until: java.time.OffsetDateTime,
    ): Long

    @Query(
        """
        SELECT COUNT(DISTINCT LOWER(d.report.account.username)) FROM ReportCustomerDecision d
        WHERE LOWER(d.decidedBy) = LOWER(:username)
          AND d.status = rs.russian.portal.report.domain.enums.ReportStatus.ACCEPTED
          AND d.decidedAt >= :from AND d.decidedAt < :until
        """
    )
    fun countDistinctHelpedAuthorsInRange(
        @Param("username") username: String,
        @Param("from") from: java.time.OffsetDateTime,
        @Param("until") until: java.time.OffsetDateTime,
    ): Long
}
