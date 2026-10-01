package rs.russian.portal.achievements.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rs.russian.portal.achievements.domain.VolunteerPointEvent
import java.util.UUID

interface VolunteerPointEventRepository : JpaRepository<VolunteerPointEvent, UUID> {
    fun findByUsernameOrderByCreatedAtDesc(username: String): List<VolunteerPointEvent>

    fun existsByUsernameAndCodeAndRefId(username: String, code: String, refId: String): Boolean

    @Query(
        """
        SELECT COALESCE(SUM(e.points), 0) FROM VolunteerPointEvent e
        WHERE LOWER(e.username) = LOWER(:username)
        """
    )
    fun sumPoints(@Param("username") username: String): Long

    fun countByUsernameAndCode(username: String, code: String): Long
}
