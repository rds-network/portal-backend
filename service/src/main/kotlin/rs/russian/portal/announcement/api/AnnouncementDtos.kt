package rs.russian.portal.announcement.api

import java.util.UUID

data class BannerDto(
    val id: UUID,
    val title: String,
    val body: String,
    val createdBy: String?,
    val createTime: String,
)

data class AnnouncementManageDto(
    val id: UUID,
    val title: String,
    val createTime: String,
    val createdBy: String?,
    val audience: String,
    val programCode: String? = null,
    val targetUsername: String? = null,
    val banner: Boolean = false,
)

data class AnnouncementPublishRequest(
    val title: String,
    val body: String,
    val audience: String,
    val programCode: String? = null,
    val username: String? = null,
    val banner: Boolean = false,
)
