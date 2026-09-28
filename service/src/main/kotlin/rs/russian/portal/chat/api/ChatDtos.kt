package rs.russian.portal.chat.api

import java.time.OffsetDateTime
import java.util.UUID

data class ChatRoomsResponse(
    val rooms: List<ChatRoomDto>,
    val canSeeAll: Boolean,
    val canCreateProgramRoom: Boolean,
)

data class ChatRoomDto(
    val id: UUID,
    val type: String,
    val title: String,
    val programCode: String? = null,
    val programNameRu: String? = null,
    val programNameEn: String? = null,
    val programNameSr: String? = null,
    val createdAt: OffsetDateTime,
)

data class ChatCreateProgramRoomRequest(
    val programCode: String,
    val title: String? = null,
)

data class ChatMessageDto(
    val id: UUID,
    val roomId: UUID,
    val authorUsername: String,
    val authorFullName: String? = null,
    val body: String,
    val imageUrl: String? = null,
    val createdAt: OffsetDateTime,
    val mine: Boolean = false,
)

data class ChatSendMessageRequest(
    val body: String? = null,
    val imageUrl: String? = null,
)

data class ChatMemberDto(
    val username: String,
    val fullName: String,
    val programCode: String? = null,
    val lastSeenAt: OffsetDateTime? = null,
    val online: Boolean = false,
    val seenLabel: String? = null,
)
