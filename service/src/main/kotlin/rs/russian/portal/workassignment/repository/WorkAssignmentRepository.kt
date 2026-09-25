package rs.russian.portal.workassignment.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import rs.russian.portal.workassignment.domain.WorkAssignment
import java.util.UUID

@Repository
interface WorkAssignmentRepository : JpaRepository<WorkAssignment, UUID> {
    fun findAllByOrderByCreateTimeDesc(): List<WorkAssignment>
    fun findByAssigneeOrderByCreateTimeDesc(assignee: String): List<WorkAssignment>
}
