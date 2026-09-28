package rs.russian.portal.applicationjoin.repository

import org.springframework.data.jpa.repository.JpaRepository
import rs.russian.portal.applicationjoin.domain.PortalApplicationJoinSettings

interface PortalApplicationJoinSettingsRepository : JpaRepository<PortalApplicationJoinSettings, Int>
