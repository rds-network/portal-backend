package rs.russian.portal.inbox.api

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class InboxThreadDto(
    val id: UUID,
    val createTime: OffsetDateTime,
    val subject: String,
    val kind: String,
    val createdBy: String?,
    val unread: Boolean,
    val lastBody: String?,
    val counterpart: String?,
)

data class InboxMessageDto(
    val id: UUID,
    val author: String?,
    val body: String,
    val createTime: OffsetDateTime,
)

data class InboxThreadDetailDto(
    val id: UUID,
    val subject: String,
    val kind: String,
    val createdBy: String?,
    val messages: List<InboxMessageDto>,
)

data class InboxCreateRequest(
    val subject: String,
    val body: String,
    val recipients: List<String>,
)

data class InboxReplyRequest(
    val body: String,
)

data class InboxUnreadDto(
    val count: Long,
)

data class ReportOverdueDto(
    val username: String,
    val fullName: String,
    val program: String?,
    val weeksMissed: Int,
    val hoursShort: Int = 0,
    val level: String,
    val lastReportWeek: LocalDate?,
)
