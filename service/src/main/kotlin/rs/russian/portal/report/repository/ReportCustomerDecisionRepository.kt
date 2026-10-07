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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ReportCustomerDecision d WHERE d.report.id = :reportId")
    fun deleteAllByReportId(@Param("reportId") reportId: UUID)
}
