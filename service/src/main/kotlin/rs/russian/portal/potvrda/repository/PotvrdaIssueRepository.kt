package rs.russian.portal.potvrda.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rs.russian.portal.potvrda.domain.PotvrdaIssue
import java.util.UUID

interface PotvrdaIssueRepository : JpaRepository<PotvrdaIssue, UUID> {

    @Query("SELECT COALESCE(MAX(p.sequenceNumber), 0) FROM PotvrdaIssue p WHERE p.registryYear = :year")
    fun maxSequenceForYear(@Param("year") year: Int): Int

    @Query(
        """
        SELECT p FROM PotvrdaIssue p
        WHERE (:search IS NULL OR :search = '' OR
            LOWER(p.volunteerFullName) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(p.volunteerUsername) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(p.confirmNumber) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY p.issueDate DESC, p.createTime DESC
        """
    )
    fun search(@Param("search") search: String?, pageable: Pageable): Page<PotvrdaIssue>
}
