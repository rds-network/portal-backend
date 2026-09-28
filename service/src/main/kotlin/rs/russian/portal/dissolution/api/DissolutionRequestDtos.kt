package rs.russian.portal.dissolution.api

import com.fasterxml.jackson.annotation.JsonProperty
import rs.russian.portal.dissolution.domain.enums.DissolutionRequestStatus
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class DissolutionRequestMetaDto(
    val approverUsername: String,
    @get:JsonProperty("isDissolutionApprover")
    val isDissolutionApprover: Boolean,
)

data class DissolutionRequestDto(
    val id: UUID,
    val username: String,
    val fullName: String?,
    val programCode: String?,
    val fromDate: LocalDate,
    val status: DissolutionRequestStatus,
    val reason: String?,
    val createdAt: OffsetDateTime,
    val decidedAt: OffsetDateTime?,
    val decidedBy: String?,
    val decisionReason: String?,
    val mupLetterSentAt: OffsetDateTime? = null,
)

data class DissolutionRequestCreateRequest(
    val fromDate: LocalDate,
    val reason: String? = null,
    /** When set by a manager, create request for this user instead of self. */
    val username: String? = null,
)

data class DissolutionRequestRejectRequest(
    val reason: String? = null,
)
