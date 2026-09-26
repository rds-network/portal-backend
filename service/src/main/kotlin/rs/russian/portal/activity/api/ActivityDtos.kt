package rs.russian.portal.activity.api

import java.time.LocalDateTime

data class ActivityEventDto(
    val id: Long,
    val createTime: LocalDateTime,
    val username: String?,
    val ip: String?,
    val method: String,
    val path: String,
    val query: String?,
    val action: String,
    val link: String?,
)

data class ActivityPageDto(
    val content: List<ActivityEventDto>,
    val total: Long,
    val page: Int,
    val size: Int,
)

data class OnlinePresenceDto(
    val total: Int,
    val loggedIn: Int,
    val guests: Int,
    val people: List<OnlinePersonDto>,
)

data class OnlinePersonDto(
    val username: String?,
    val displayName: String,
    val ip: String?,
    val path: String,
    val query: String?,
    val lastSeen: LocalDateTime,
    val self: Boolean = false,
)
