package rs.russian.portal.report.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import rs.russian.portal.report.domain.Report
import rs.russian.portal.report.domain.Report.Companion.GRAPH_FULL
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.report.repository.projections.ProgramStatProjection
import java.time.OffsetDateTime
import java.util.*

@Repository
interface ReportRepository : JpaRepository<Report, UUID> {

    @EntityGraph(value = GRAPH_FULL)
    override fun findById(id: UUID): Optional<Report>

    @EntityGraph(value = GRAPH_FULL)
    fun findAllByIdIn(values: Collection<UUID>, sort: Sort): List<Report>

    fun findAll(specification: Specification<Report>, pageable: Pageable): Page<Report>

    fun findTopByAccountUsernameAndStatusOrderByCreateTimeDesc(
        username: String,
        status: ReportStatus,
    ): Report?

    @Query(
        """
        SELECT
            g.code AS groupCode,
            COUNT(DISTINCT r.account.username) AS count,
            COALESCE(SUM(t.timeSpent), 0) / 60.0 AS totalTimeSpent
        FROM Report r
        LEFT JOIN r.tasks t
        LEFT JOIN r.account.info.project.statisticGroups g
        WHERE r.createTime BETWEEN :start AND :end
        GROUP BY g.code
        """
    )
    fun fetchProgramStatsByGroup(
        @Param("start") start: OffsetDateTime,
        @Param("end") end: OffsetDateTime
    ): List<ProgramStatProjection>

    @Query(
        value = """
        SELECT r.id FROM Report r
        WHERE EXISTS (
            SELECT 1 FROM Task t
            WHERE t.report = r AND LOWER(t.customer.username) IN :logins
        )
          AND (:status IS NULL OR r.status = :status)
        ORDER BY r.createTime DESC
        """,
        countQuery = """
        SELECT COUNT(r.id) FROM Report r
        WHERE EXISTS (
            SELECT 1 FROM Task t
            WHERE t.report = r AND LOWER(t.customer.username) IN :logins
        )
          AND (:status IS NULL OR r.status = :status)
        """
    )
    fun findIdsByCustomers(
        @Param("logins") logins: Collection<String>,
        @Param("status") status: ReportStatus?,
        pageable: Pageable,
    ): Page<UUID>

    @Query(
        """
        SELECT COUNT(r.id) FROM Report r
        WHERE EXISTS (
            SELECT 1 FROM Task t
            WHERE t.report = r AND LOWER(t.customer.username) IN :logins
        )
          AND (:status IS NULL OR r.status = :status)
        """
    )
    fun countByCustomers(
        @Param("logins") logins: Collection<String>,
        @Param("status") status: ReportStatus?,
    ): Long
}
