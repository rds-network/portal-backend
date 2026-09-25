package rs.russian.portal.inbox.repository

import org.springframework.data.jpa.repository.JpaRepository
import rs.russian.portal.inbox.domain.ReportOverdueNotice
import java.util.UUID

interface ReportOverdueNoticeRepository : JpaRepository<ReportOverdueNotice, UUID> {
    fun existsByUsernameAndLevelAndPeriodKey(username: String, level: Int, periodKey: String): Boolean
}
