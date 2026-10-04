package rs.russian.portal.talent.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rs.russian.portal.talent.domain.TalentResponse
import java.time.OffsetDateTime
import java.util.UUID

interface TalentResponseRepository : JpaRepository<TalentResponse, UUID> {
    fun findAllByPost_IdOrderByCreatedAtAsc(postId: UUID): List<TalentResponse>

    fun existsByPost_IdAndAuthorUsernameIgnoreCase(postId: UUID, authorUsername: String): Boolean

    fun findTop5ByPost_IdOrderByCreatedAtDesc(postId: UUID): List<TalentResponse>

    @Query(
        """
        SELECT COUNT(r) FROM TalentResponse r
        JOIN r.post p
        WHERE LOWER(p.authorUsername) = LOWER(:owner)
          AND LOWER(r.authorUsername) <> LOWER(:owner)
          AND r.createdAt > :since
        """
    )
    fun countNewForPostOwner(
        @Param("owner") owner: String,
        @Param("since") since: OffsetDateTime,
    ): Long
}
