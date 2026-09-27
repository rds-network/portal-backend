package rs.russian.portal.dissolution.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rs.russian.portal.dissolution.domain.DissolutionRequest
import rs.russian.portal.dissolution.domain.enums.DissolutionRequestStatus
import java.util.UUID

interface DissolutionRequestRepository : JpaRepository<DissolutionRequest, UUID> {
    fun findAllByUsernameIgnoreCaseOrderByCreatedAtDesc(username: String): List<DissolutionRequest>

    fun findAllByStatusOrderByCreatedAtAsc(status: DissolutionRequestStatus): List<DissolutionRequest>

    fun existsByUsernameIgnoreCaseAndStatus(username: String, status: DissolutionRequestStatus): Boolean

    @Query(
        """
        SELECT dr FROM DissolutionRequest dr
        WHERE dr.status <> :status
        ORDER BY dr.createdAt DESC
        """
    )
    fun findDecided(
        @Param("status") status: DissolutionRequestStatus,
        pageable: Pageable,
    ): List<DissolutionRequest>
}
