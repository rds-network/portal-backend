package rs.russian.portal.yearreview.api

data class YearReviewStatsDto(
    val year: Int,
    val displayName: String,
    val username: String,
    val programCode: String?,
    val programNameRu: String?,
    val programNameEn: String?,
    val programNameSr: String?,
    val activeWeeks: Int,
    val longestStreak: Int,
    val earnedPoints: Long,
    val acceptedReports: Int,
    val completedTasks: Int,
    val badgesEarned: Int,
    val currentRank: Int?,
    val bestRank: Int?,
    val totalParticipants: Int,
    val curatorGratitudes: Int,
    val managerGratitudes: Int,
    val isPodium: Boolean,
    /** Curator / program-lead role this year (or currently assigned). */
    val isTeamBuilder: Boolean,
    val directionsCount: Int,
    val delegatesCount: Int,
    val reportsReviewed: Int,
    val peopleHelped: Int,
    /** Ideas & talent board activity. */
    val ideasCreated: Int,
    val responsesReceived: Int,
    val responsesSent: Int,
    val teamsJoined: Int,
    /** Soft weekly misses / gaps — used for gentle rank coaching copy. */
    val missedWeeks: Int,
    val irregularReporting: Boolean,
)

data class YearReviewCriterionProgressDto(
    val id: String,
    val met: Boolean,
    val current: Int,
    val required: Int,
)

data class YearReviewEligibilityDto(
    val eligible: Boolean,
    val criteriaMet: Int,
    val criteriaRequired: Int,
    val reasons: List<String>,
    val progress: List<YearReviewCriterionProgressDto>,
    val remainingHint: String?,
)

data class AnnualCertificateDto(
    val id: String,
    val year: Int,
    val certificateNumber: String,
    val verificationToken: String,
    val status: String,
    val issueSource: String,
    val qualificationReasons: List<String>,
    val recipientFullName: String?,
    val templateVersion: String,
    val issuedAt: String,
    val pdfFileId: String?,
)

data class YearReviewDto(
    val year: Int,
    val ready: Boolean,
    val stats: YearReviewStatsDto,
    val eligibility: YearReviewEligibilityDto,
    val certificate: AnnualCertificateDto?,
    val specialThanks: List<String>,
)

data class YearReviewCriteriaDto(
    val year: Int,
    val topRankMax: Int,
    val minAcceptedReports: Int,
    val minEarnedPoints: Int,
    val minActiveWeeks: Int,
    val minBadges: Int,
    val minCriteriaMet: Int,
    val enabled: Boolean,
)

data class YearReviewCriteriaUpdateRequest(
    val topRankMax: Int? = null,
    val minAcceptedReports: Int? = null,
    val minEarnedPoints: Int? = null,
    val minActiveWeeks: Int? = null,
    val minBadges: Int? = null,
    val minCriteriaMet: Int? = null,
    val enabled: Boolean? = null,
)

data class AnnualCertificateVerifyDto(
    val valid: Boolean,
    val status: String,
    val recipientFullName: String?,
    val documentTitle: String,
    val year: Int,
    val certificateNumber: String,
    val issuedAt: String,
    val organization: String,
)

data class AttachCertificatePdfRequest(
    val fileId: String,
)
