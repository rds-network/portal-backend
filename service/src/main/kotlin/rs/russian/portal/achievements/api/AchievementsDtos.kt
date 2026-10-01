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
