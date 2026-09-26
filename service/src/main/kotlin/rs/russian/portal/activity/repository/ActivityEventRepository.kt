package rs.russian.portal.activity.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rs.russian.portal.activity.domain.ActivityEvent

interface ActivityEventRepository : JpaRepository<ActivityEvent, Long> {
    @Query(
        """
        SELECT e FROM ActivityEvent e
        WHERE (:q IS NULL OR :q = ''
           OR LOWER(e.username) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(e.ip) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(e.path) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(e.action) LIKE LOWER(CONCAT('%', :q, '%')))
        """
    )
    fun search(@Param("q") q: String?, pageable: Pageable): Page<ActivityEvent>

    @Query(
        """
        SELECT e FROM ActivityEvent e
        WHERE e.createTime >= :since
        """
    )
    fun findSince(@Param("since") since: java.time.LocalDateTime, pageable: Pageable): List<ActivityEvent>
}
