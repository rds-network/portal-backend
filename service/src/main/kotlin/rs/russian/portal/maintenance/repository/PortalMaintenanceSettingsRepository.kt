package rs.russian.portal.maintenance.repository

import org.springframework.data.jpa.repository.JpaRepository
import rs.russian.portal.maintenance.domain.PortalMaintenanceSettings

interface PortalMaintenanceSettingsRepository : JpaRepository<PortalMaintenanceSettings, Int>
