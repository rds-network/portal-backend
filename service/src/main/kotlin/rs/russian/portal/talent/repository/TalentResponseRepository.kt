package rs.russian.portal.talent.repository

import org.springframework.data.jpa.repository.JpaRepository
import rs.russian.portal.talent.domain.TalentResponse
import java.util.UUID

interface TalentResponseRepository : JpaRepository<TalentResponse, UUID> {
    fun findAllByPost_IdOrderByCreatedAtAsc(postId: UUID): List<TalentResponse>

    fun existsByPost_IdAndAuthorUsernameIgnoreCase(postId: UUID, authorUsername: String): Boolean

    fun findTop5ByPost_IdOrderByCreatedAtDesc(postId: UUID): List<TalentResponse>
}
