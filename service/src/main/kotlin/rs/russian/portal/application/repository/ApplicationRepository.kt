package rs.russian.portal.application.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import rs.russian.portal.application.domain.Application
import rs.russian.portal.application.domain.Application.Companion.GRAPH_FULL
import rs.russian.portal.application.domain.ApplicationStatus
import rs.russian.portal.application.domain.ApplicationType
import java.time.LocalDateTime
import java.util.*

@Repository
interface ApplicationRepository : JpaRepository<Application, UUID> {

    @EntityGraph(GRAPH_FULL)
    override fun findById(id: UUID): Optional<Application>

    @EntityGraph(GRAPH_FULL)
    fun findByEmail(email: String): Optional<Application>

    @EntityGraph(GRAPH_FULL)
    fun findAllByEmail(email: String): List<Application>

    @EntityGraph(GRAPH_FULL)
    fun findByEmailAndStatusNotIn(email: String, statuses: List<ApplicationStatus>): Optional<Application>

    @EntityGraph(GRAPH_FULL)
    fun findByEmailAndPassportAndStatusNotIn(
        email: String,
        passport: String,
        statuses: List<ApplicationStatus>,
    ): Optional<Application>

    @EntityGraph(GRAPH_FULL)
    fun findAllByIdIn(idList: Collection<UUID>, sort: Sort): List<Application>

    fun existsByEmailAndTypeAndStatusNotIn(
        email: String,
        type: ApplicationType,
        statuses: List<ApplicationStatus>
    ): Boolean

    fun findAll(specification: Specification<Application>, pageable: Pageable): Page<Application>

    @Query(
        """
        SELECT a FROM Application a
        WHERE a.email <> :depersonalized
          AND COALESCE(a.version, a.created) > :since
        ORDER BY COALESCE(a.version, a.created) ASC, a.id ASC
        """
    )
    fun findChangedSince(
        @Param("since") since: LocalDateTime,
        @Param("depersonalized") depersonalized: String,
        pageable: Pageable,
    ): List<Application>

    @Query(
        """
        SELECT a FROM Application a
        WHERE a.email <> :depersonalized
          AND a.type = :type
          AND a.status <> rs.russian.portal.application.domain.ApplicationStatus.DONE
          AND a.status <> rs.russian.portal.application.domain.ApplicationStatus.DENY
        ORDER BY a.created DESC, a.id DESC
        """
    )
    fun findOpenByType(
        @Param("type") type: ApplicationType,
        @Param("depersonalized") depersonalized: String,
        pageable: Pageable,
    ): List<Application>

    @Query(
        """
        SELECT a FROM Application a
        WHERE LOWER(a.assignee) = LOWER(:assignee)
          AND a.status NOT IN :closed
        ORDER BY a.created DESC, a.id DESC
        """
    )
    fun findOpenByAssigneeIgnoreCase(
        @Param("assignee") assignee: String,
        @Param("closed") closed: Collection<ApplicationStatus>,
    ): List<Application>
}
