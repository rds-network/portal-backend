package rs.russian.portal.orglink.repository

import org.springframework.data.jpa.repository.JpaRepository
import rs.russian.portal.orglink.domain.OrgLink
import java.util.UUID

interface OrgLinkRepository : JpaRepository<OrgLink, UUID> {
    fun findAllByOrderBySortOrderAscTitleAsc(): List<OrgLink>
}
