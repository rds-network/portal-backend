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
    val visualType: String = "PICTOGRAM",
    val visualKey: String? = null,
    val imageUrl: String? = null,
    val requiresReview: Boolean = true,
    val proofLabel: String? = null,
    /** For volunteer list: points already awarded. */
    val claimed: Boolean = false,
    /** NONE | PENDING | APPROVED | REJECTED — latest submission for review missions. */
    val submissionStatus: String? = null,
    val proofText: String? = null,
    val rejectReason: String? = null,
)

data class PointMissionWriteRequest(
    val title: String,
    val description: String? = null,
    val points: Int,
    val link: String? = null,
    val active: Boolean = true,
    val oneTime: Boolean = true,
    val sortOrder: Int = 0,
    val visualType: String? = "PICTOGRAM",
    val visualKey: String? = null,
    val imageUrl: String? = null,
    val requiresReview: Boolean = true,
    val proofLabel: String? = null,
)

data class PointMissionClaimResult(
    val missionId: String,
    val points: Int,
    val balance: Long,
    val alreadyClaimed: Boolean,
    val submissionStatus: String? = null,
)

data class PointMissionSubmitRequest(
    val proofText: String,
)

data class PointMissionSubmissionDto(
    val id: String,
    val missionId: String,
    val missionTitle: String,
    val points: Int,
    val username: String,
    val proofText: String,
    val status: String,
    val rejectReason: String?,
    val reviewedBy: String?,
    val reviewedAt: String?,
    val createdAt: String,
)

data class PointMissionRejectRequest(
    val reason: String? = null,
)
