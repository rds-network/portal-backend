package rs.russian.portal.leave.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rs.russian.portal.leave.domain.LeaveRequest
import rs.russian.portal.leave.domain.enums.LeaveRequestStatus
import java.time.LocalDate
import java.util.UUID

interface LeaveRequestRepository : JpaRepository<LeaveRequest, UUID> {
    fun findAllByUsernameIgnoreCaseOrderByCreatedAtDesc(username: String): List<LeaveRequest>

    fun findAllByStatusOrderByCreatedAtAsc(status: LeaveRequestStatus): List<LeaveRequest>

    @Query(
        """
        SELECT lr FROM LeaveRequest lr
        WHERE lr.status <> :status
        ORDER BY lr.createdAt DESC
        """
    )
    fun findDecided(
        @Param("status") status: LeaveRequestStatus,
        pageable: Pageable,
    ): List<LeaveRequest>

    @Query(
        """
        SELECT lr FROM LeaveRequest lr
        WHERE lr.status = :status
          AND LOWER(lr.username) IN :usernames
        ORDER BY lr.createdAt ASC
        """
    )
    fun findPendingForUsernames(
        @Param("status") status: LeaveRequestStatus,
        @Param("usernames") usernames: Collection<String>,
    ): List<LeaveRequest>

    @Query(
        """
        SELECT lr FROM LeaveRequest lr
        WHERE lr.status = :status
          AND LOWER(lr.username) = LOWER(:username)
          AND lr.startDate <= :to
          AND lr.endDate >= :from
        ORDER BY lr.startDate ASC
        """
    )
    fun findAcceptedOverlapping(
        @Param("username") username: String,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("status") status: LeaveRequestStatus,
    ): List<LeaveRequest>
}
