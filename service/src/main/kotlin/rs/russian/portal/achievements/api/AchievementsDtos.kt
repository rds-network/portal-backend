package rs.russian.portal.achievements.api

data class AchievementDto(
    val id: String,
    val category: String,
    val title: String,
    val description: String,
    val points: Int,
    val unlocked: Boolean,
    val progress: Int? = null,
    val target: Int? = null,
)

data class PointEventDto(
    val code: String,
    val points: Int,
    val title: String?,
    val refId: String?,
    val createdAt: String,
)

data class InboxDeliveryStatsDto(
    val sent: Long,
    val delivered: Long,
    val pending: Long,
)

data class AchievementsMeDto(
    val balance: Long,
    val unlockedCount: Int,
    val totalCount: Int,
    val achievements: List<AchievementDto>,
    val recent: List<PointEventDto>,
    val inbox: InboxDeliveryStatsDto,
    val thisWeekVisited: Boolean,
)

data class PointLeaderDto(
    val rank: Int,
    val username: String,
    val fullName: String,
    val points: Long,
    val isMe: Boolean = false,
)

data class AchievementsLeaderboardDto(
    val leaders: List<PointLeaderDto>,
    val me: PointLeaderDto?,
    val totalParticipants: Int,
    /** Full table beyond the podium is only for managers/admins. */
    val fullList: Boolean = false,
)

data class ExtPointAwardRequest(
    val user: String,
    val code: String,
    val points: Int,
    val refId: String,
    val title: String,
)

data class ExtPointAwardResponse(
    val username: String,
    val balance: Long,
    val awarded: Boolean,
)
