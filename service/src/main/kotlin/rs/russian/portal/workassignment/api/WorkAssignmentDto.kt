package rs.russian.portal.workassignment.api

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class WorkAssignmentDto(
    val id: UUID,
    val createTime: OffsetDateTime,
    val createdBy: String,
    val title: String,
    val body: String?,
    val assignee: String?,
    val assigneeName: String?,
    val status: String,
    val dueDate: LocalDate?,
    val reportId: UUID?,
)

data class WorkAssignmentCreateRequest(
    val title: String,
    val body: String? = null,
    val assignee: String? = null,
    val dueDate: LocalDate? = null,
)

data class WorkAssignmentPatchRequest(
    val title: String? = null,
    val body: String? = null,
    val assignee: String? = null,
    val status: String? = null,
    val dueDate: LocalDate? = null,
)
