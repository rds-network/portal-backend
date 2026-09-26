package rs.russian.portal.inbox.api

import com.fasterxml.jackson.annotation.JsonFormat
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
    val counterpartName: String? = null,
    val heatmapUser: String? = null,
    val reportId: String? = null,
    val recipient: String? = null,
    val recipientName: String? = null,
    val recipientLastSeen: OffsetDateTime? = null,
    val receivedAt: OffsetDateTime? = null,
    val ackRequired: Boolean = false,
    val needsAck: Boolean = false,
    /** Total messages in the thread (1 = only the original notice). */
    val messageCount: Int = 1,
    val lastAuthor: String? = null,
    val lastAuthorName: String? = null,
    val lastMessageTime: OffsetDateTime? = null,
    /** True when someone replied after the first system/staff message. */
    val hasReply: Boolean = false,
)

data class InboxMessageDto(
    val id: UUID,
    val author: String?,
    val authorName: String? = null,
    val body: String,
    val createTime: OffsetDateTime,
)

data class InboxThreadDetailDto(
    val id: UUID,
    val subject: String,
    val kind: String,
    val createdBy: String?,
    val createdByName: String? = null,
    val heatmapUser: String? = null,
    val reportId: String? = null,
    val recipient: String? = null,
    val recipientName: String? = null,
    val recipientLastSeen: OffsetDateTime? = null,
    val receivedAt: OffsetDateTime? = null,
    val ackRequired: Boolean = false,
    val needsAck: Boolean = false,
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

data class OverdueWeekDto(
    @JsonFormat(pattern = "yyyy-MM-dd")
    val weekStart: LocalDate,
    val hoursWorked: Double,
    val hoursRequired: Int,
)

data class ReportOverdueDto(
    val username: String,
    val fullName: String,
    val program: String?,
    val weeksMissed: Int,
    val hoursShort: Int = 0,
    val hoursWorked: Int = 0,
    val hoursRequired: Int = 0,
    val contractEnd: LocalDate? = null,
    val recentWeeks: List<OverdueWeekDto> = emptyList(),
    val level: String,
    val lastReportWeek: LocalDate?,
    val warningCount: Int = 0,
    val notified: Boolean = false,
    val watchlist: Boolean = false,
    val subject: String? = null,
    val body: String? = null,
)

data class OverdueNoticePersonDto(
    val username: String,
    val fullName: String,
    val program: String? = null,
    val warningCount: Int,
    val lastSentAt: OffsetDateTime? = null,
    val notified: Boolean,
    val watchlist: Boolean,
    val mupSent: Boolean,
)

data class OverdueNotifyResultDto(
    val sent: Int,
    val recipients: List<OverdueNoticePersonDto> = emptyList(),
)

data class OverduePreviewDto(
    val count: Int,
    val templates: List<OverdueTemplateDto>,
    val samples: List<ReportOverdueDto>,
)

data class OverdueTemplateDto(
    val level: String,
    val subject: String,
    val body: String,
)

data class OverdueNotifyRequest(
    val exclude: List<String> = emptyList(),
)

data class OverdueCancelRequest(
    val all: Boolean = false,
    val reason: String? = null,
)
