package rs.russian.portal.workassignment.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import rs.russian.portal.workassignment.domain.WorkAssignment
import rs.russian.portal.workassignment.domain.enums.WorkAssignmentStatus
import java.util.UUID

@Repository
interface WorkAssignmentRepository : JpaRepository<WorkAssignment, UUID> {
    fun findAllByOrderByCreateTimeDesc(): List<WorkAssignment>
    fun findByAssigneeOrderByCreateTimeDesc(assignee: String): List<WorkAssignment>
    fun findByCustomerOrderByCreateTimeDesc(customer: String): List<WorkAssignment>

    @Query(
        """
        SELECT COUNT(w) FROM WorkAssignment w
        WHERE LOWER(w.assignee) = LOWER(:assignee)
          AND w.status IN :statuses
        """
    )
    fun countOpenForAssignee(
        @Param("assignee") assignee: String,
        @Param("statuses") statuses: Collection<WorkAssignmentStatus>,
    ): Long
}
