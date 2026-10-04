package rs.russian.portal.leave.api

import com.fasterxml.jackson.annotation.JsonProperty
import rs.russian.portal.leave.domain.enums.LeaveRequestStatus
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class LeaveRequestMetaDto(
    val approverUsername: String,
    @get:JsonProperty("isLeaveApprover")
    val isLeaveApprover: Boolean,
)

data class LeaveRequestDto(
    val id: UUID,
    val username: String,
    val fullName: String?,
    val programCode: String?,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: LeaveRequestStatus,
    val reason: String?,
    val createdAt: OffsetDateTime,
    val decidedAt: OffsetDateTime?,
    val decidedBy: String?,
)

data class LeaveRequestCreateRequest(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reason: String? = null,
    /** When set by a manager, create leave for this user instead of self. */
    val username: String? = null,
)

data class LeaveRequestRejectRequest(
    val reason: String? = null,
)
