package rs.russian.portal.missions.api

data class PointMissionDto(
    val id: String,
    val title: String,
    val description: String?,
    val points: Int,
    val link: String?,
    val active: Boolean,
    val oneTime: Boolean,
    val sortOrder: Int,
    /** For volunteer list: whether current user already claimed. */
    val claimed: Boolean = false,
)

data class PointMissionWriteRequest(
    val title: String,
    val description: String? = null,
    val points: Int,
    val link: String? = null,
    val active: Boolean = true,
    val oneTime: Boolean = true,
    val sortOrder: Int = 0,
)

data class PointMissionClaimResult(
    val missionId: String,
    val points: Int,
    val balance: Long,
    val alreadyClaimed: Boolean,
)
