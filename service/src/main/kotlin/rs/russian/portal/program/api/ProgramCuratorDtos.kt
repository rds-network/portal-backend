package rs.russian.portal.program.api

data class ProgramCuratorDto(
    val programCode: String,
    val programNameRu: String,
    val programNameEn: String,
    val programNameSr: String,
    val username: String,
    val fullName: String,
)

data class ProgramCuratorWriteRequest(
    val programCode: String,
    val username: String,
)

data class ProgramCuratorDelegateDto(
    val programCode: String,
    val programNameRu: String,
    val programNameEn: String,
    val programNameSr: String,
    val curatorUsername: String,
    val curatorFullName: String,
    val delegateUsername: String,
    val delegateFullName: String,
)

data class ProgramCuratorDelegateWriteRequest(
    val programCode: String,
    val curatorUsername: String,
    val delegateUsername: String,
)

/** Who can be chosen as report task customer. */
data class ReportApproverDto(
    val username: String,
    val fullName: String,
    /** Администраторы принимают отчёты вне программ, поэтому у роли ADMIN программы нет. */
    val programCode: String? = null,
    val programNameRu: String? = null,
    val programNameEn: String? = null,
    val programNameSr: String? = null,
    val role: String,
    val curatorUsername: String? = null,
    val curatorFullName: String? = null,
)

/** Portal-wide moderators (ADMIN_VOLUNTEER) — global control, not program curators. */
data class PortalModeratorDto(
    val username: String,
    val fullName: String,
)
