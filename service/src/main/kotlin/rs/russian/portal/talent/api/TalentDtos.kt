package rs.russian.portal.talent.api

import rs.russian.portal.talent.domain.enums.TalentPostStatus
import rs.russian.portal.talent.domain.enums.TalentPostType
import java.time.OffsetDateTime
import java.util.UUID

data class TalentPostDto(
    val id: UUID,
    val type: TalentPostType,
    val status: TalentPostStatus,
    val title: String,
    val body: String,
    val city: String?,
    val programCode: String?,
    val programNameRu: String?,
    val skills: List<String>,
    val authorUsername: String,
    val authorFullName: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val responseCount: Int,
    val responderUsernames: List<String> = emptyList(),
    val mine: Boolean = false,
    val alreadyResponded: Boolean = false,
)

data class TalentPostCreateRequest(
    val type: TalentPostType,
    val title: String,
    val body: String,
    val city: String? = null,
    val programCode: String? = null,
    val skills: List<String>? = null,
)

data class TalentResponseDto(
    val id: UUID,
    val postId: UUID,
    val authorUsername: String,
    val authorFullName: String?,
    val message: String,
    val createdAt: OffsetDateTime,
)

data class TalentResponseCreateRequest(
    val message: String,
)

data class TalentSkillsDto(
    val skills: List<String>,
)

data class TalentSkillsUpdateRequest(
    val skills: List<String>,
)

data class TalentPostsPageDto(
    val content: List<TalentPostDto>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
    val size: Int,
)

data class TalentUnreadDto(
    val count: Long,
)
