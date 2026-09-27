package rs.russian.portal.accountstatus.api

import com.fasterxml.jackson.annotation.JsonProperty
import rs.russian.portal.accountstatus.domain.enums.AccountStatusEventSource
import rs.russian.portal.accountstatus.domain.enums.AccountStatusRequestStatus
import java.time.OffsetDateTime
import java.util.UUID

data class AccountStatusMetaDto(
    val approverUsername: String,
    @get:JsonProperty("isAccountStatusApprover")
    val isAccountStatusApprover: Boolean,
)

data class AccountStatusRequestDto(
    val id: UUID,
    val targetAccountId: Int,
    val targetUsername: String,
    val targetFullName: String?,
    val requestedActive: Boolean,
    val status: AccountStatusRequestStatus,
    val reason: String?,
    val createdBy: String,
    val createdAt: OffsetDateTime,
    val decidedBy: String?,
    val decidedAt: OffsetDateTime?,
    val decisionReason: String?,
)

data class AccountStatusEventDto(
    val id: UUID,
    val accountId: Int,
    val accountUsername: String,
    val accountFullName: String?,
    val activeTo: Boolean,
    val source: AccountStatusEventSource,
    val actorUsername: String?,
    val reason: String?,
    val createdAt: OffsetDateTime,
    val requestId: UUID?,
)

data class AccountStatusCreateRequest(
    val accountId: Int,
    val requestedActive: Boolean,
    val reason: String? = null,
)

data class AccountStatusDecisionRequest(
    val reason: String? = null,
)

data class AccountStatusChangeResultDto(
    val pending: Boolean,
    val request: AccountStatusRequestDto? = null,
    val accountId: Int,
    val username: String,
    val active: Boolean,
)
