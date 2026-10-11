package rs.russian.portal.report.api

import java.time.LocalDate
import java.util.UUID

/** Request body for POST /ext/report/tasks (Ekomapa create-or-append). */
data class ExtAppendReportTasksRequest(
    val user: String,
    val tasks: List<ExtCreateTaskRequest> = emptyList(),
    val reportId: UUID? = null,
)

data class ExtCreateTaskRequest(
    val date: LocalDate,
    val name: String,
    val description: String,
    val timeSpent: Int,
    val result: String? = null,
    val nameSr: String? = null,
    val descriptionSr: String? = null,
)
