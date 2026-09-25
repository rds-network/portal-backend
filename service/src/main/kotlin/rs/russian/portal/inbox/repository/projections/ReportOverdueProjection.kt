package rs.russian.portal.inbox.repository.projections

import java.time.LocalDate

interface ReportOverdueProjection {
    val username: String
    val fullName: String
    val program: String?
    val weeksMissed: Int
    val lastReportWeek: LocalDate?
}
