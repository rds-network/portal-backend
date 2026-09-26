package rs.russian.portal.event.api

import rs.russian.portal.event.domain.enums.PortalEventType
import java.time.OffsetDateTime
import java.util.UUID

data class PortalEventDto(
    val id: UUID,
    val createTime: OffsetDateTime,
    val createdBy: String,
    val title: String,
    val description: String?,
    val startsAt: OffsetDateTime,
    val location: String?,
    val type: PortalEventType,
    val programCode: String?,
)

data class PortalEventWriteRequest(
    val title: String,
    val description: String? = null,
    val startsAt: OffsetDateTime,
    val location: String? = null,
    val type: PortalEventType = PortalEventType.OTHER,
    val programCode: String? = null,
)
