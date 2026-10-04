package rs.russian.portal.user.repository

import java.time.LocalDateTime

interface LastSeenProjection {
    val username: String
    val lastSeen: LocalDateTime?
}
