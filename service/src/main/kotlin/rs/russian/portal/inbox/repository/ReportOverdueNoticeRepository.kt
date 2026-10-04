package rs.russian.portal.inbox.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import rs.russian.portal.inbox.domain.ReportOverdueNotice
import java.util.UUID

interface ReportOverdueNoticeRepository : JpaRepository<ReportOverdueNotice, UUID> {
    fun existsByUsernameAndLevelAndPeriodKey(username: String, level: Int, periodKey: String): Boolean

    fun countByUsernameAndLevelLessThanAndCancelledAtIsNull(username: String, level: Int): Long

    @Query(
        """
        SELECT n.username AS username, COUNT(n) AS cnt
        FROM ReportOverdueNotice n
        WHERE n.level < 99 AND n.cancelledAt IS NULL
        GROUP BY n.username
        """
    )
    fun countGrouped(): List<WarningCountProjection>

    fun findAllByOrderBySentAtDesc(): List<ReportOverdueNotice>

    fun findByUsernameAndLevelLessThanAndCancelledAtIsNullOrderBySentAtDesc(
        username: String,
        level: Int,
    ): List<ReportOverdueNotice>
}
