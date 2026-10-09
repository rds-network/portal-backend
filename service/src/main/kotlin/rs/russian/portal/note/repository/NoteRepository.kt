package rs.russian.portal.note.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import rs.russian.portal.note.domain.Note
import rs.russian.portal.note.domain.enums.EntityType
import java.util.UUID

@Repository
interface NoteRepository : JpaRepository<Note, UUID> {
    fun findAllByEntityIdAndEntityType(entityId: UUID, entityType: EntityType): List<Note>
}
