package rs.russian.portal.accountstatus.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rs.russian.portal.accountstatus.domain.AccountStatusRequest
import rs.russian.portal.accountstatus.domain.enums.AccountStatusRequestStatus
import java.util.UUID

interface AccountStatusRequestRepository : JpaRepository<AccountStatusRequest, UUID> {

    fun findAllByStatusOrderByCreatedAtAsc(status: AccountStatusRequestStatus): List<AccountStatusRequest>

    fun existsByTargetAccountIdAndRequestedActiveAndStatus(
        targetAccountId: Int,
        requestedActive: Boolean,
        status: AccountStatusRequestStatus,
    ): Boolean

    @Query(
        """
        SELECT r FROM AccountStatusRequest r
        WHERE r.status <> :pending
        ORDER BY r.createdAt DESC
        """
    )
    fun findDecided(
        @Param("pending") pending: AccountStatusRequestStatus,
        pageable: Pageable,
    ): List<AccountStatusRequest>
}
