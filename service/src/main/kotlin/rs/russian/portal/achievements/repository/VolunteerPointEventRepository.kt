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

    @Query(
        """
        SELECT COUNT(e) FROM VolunteerPointEvent e
        WHERE LOWER(e.username) = LOWER(:username) AND e.code = :code
        """
    )
    fun countByUsernameIgnoreCaseAndCode(
        @Param("username") username: String,
        @Param("code") code: String,
    ): Long

    fun findTop200ByCodeOrderByCreatedAtDesc(code: String): List<VolunteerPointEvent>

    @Query(
        """
        SELECT LOWER(e.username), COALESCE(SUM(e.points), 0)
        FROM VolunteerPointEvent e
        GROUP BY LOWER(e.username)
        ORDER BY COALESCE(SUM(e.points), 0) DESC
        """
    )
    fun sumPointsGrouped(): List<Array<Any>>

    @Query(
        """
        SELECT e FROM VolunteerPointEvent e
        WHERE LOWER(e.username) = LOWER(:username)
          AND e.createdAt >= :from AND e.createdAt < :until
        ORDER BY e.createdAt ASC
        """
    )
    fun findByUsernameAndCreatedAtRange(
        @Param("username") username: String,
        @Param("from") from: java.time.LocalDateTime,
        @Param("until") until: java.time.LocalDateTime,
    ): List<VolunteerPointEvent>

    @Query(
        """
        SELECT COALESCE(SUM(e.points), 0) FROM VolunteerPointEvent e
        WHERE LOWER(e.username) = LOWER(:username)
          AND e.points > 0
          AND e.createdAt >= :from AND e.createdAt < :until
        """
    )
    fun sumPositivePointsInRange(
        @Param("username") username: String,
        @Param("from") from: java.time.LocalDateTime,
        @Param("until") until: java.time.LocalDateTime,
    ): Long

    @Query(
        """
        SELECT COUNT(e) FROM VolunteerPointEvent e
        WHERE LOWER(e.username) = LOWER(:username)
          AND e.code = :code
          AND e.createdAt >= :from AND e.createdAt < :until
        """
    )
    fun countByUsernameAndCodeInRange(
        @Param("username") username: String,
        @Param("code") code: String,
        @Param("from") from: java.time.LocalDateTime,
        @Param("until") until: java.time.LocalDateTime,
    ): Long
}
