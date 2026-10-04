package rs.russian.portal.orglink.api

import java.time.OffsetDateTime
import java.util.UUID

data class OrgLinkDto(
    val id: UUID,
    val createTime: OffsetDateTime,
    val createdBy: String,
    val title: String,
    val url: String,
    val description: String?,
    val sortOrder: Int,
)

data class OrgLinkWriteRequest(
    val title: String,
    val url: String,
    val description: String? = null,
    val sortOrder: Int? = null,
)
