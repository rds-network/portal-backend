package rs.russian.portal.report.repository.projections

import java.math.BigDecimal
import java.time.LocalDate

interface VolunteerWeekProjection {
    val username: String
    val year: Int
    val week: Int
    val weekStart: LocalDate
    val weekEnd: LocalDate
    val hoursWorked: BigDecimal
    val hoursRequired: Int
    val leaveDays: Int
}
