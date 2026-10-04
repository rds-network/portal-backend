package rs.russian.portal.chat.domain

import java.io.Serializable
import java.util.UUID

data class ChatRoomReadId(
    var username: String = "",
    var roomId: UUID = UUID(0, 0),
) : Serializable
