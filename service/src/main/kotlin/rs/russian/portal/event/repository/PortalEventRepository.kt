package rs.russian.portal.event.repository

import org.springframework.data.jpa.repository.JpaRepository
import rs.russian.portal.event.domain.PortalEvent
import java.time.OffsetDateTime
import java.util.UUID

interface PortalEventRepository : JpaRepository<PortalEvent, UUID> {
    fun findAllByStartsAtGreaterThanEqualOrderByStartsAtAsc(from: OffsetDateTime): List<PortalEvent>

    fun findAllByOrderByStartsAtDesc(): List<PortalEvent>
}
