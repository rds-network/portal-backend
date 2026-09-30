package rs.russian.portal.orglink.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import rs.russian.portal.orglink.domain.OrgLink
import java.time.OffsetDateTime
import java.util.UUID

interface OrgLinkRepository : JpaRepository<OrgLink, UUID> {
    fun findAllByOrderBySortOrderAscTitleAsc(): List<OrgLink>

    fun countByCreateTimeAfter(createTime: OffsetDateTime): Long

    @Query("SELECT COUNT(o) FROM OrgLink o")
    fun countAll(): Long
}
