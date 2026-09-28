package rs.russian.portal.maintenance.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "portal_maintenance_settings")
class PortalMaintenanceSettings(
    @Id
    @Column(name = "id")
    var id: Int = 1,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = false,

    @Column(name = "headline", nullable = false, length = 500)
    var headline: String = "",

    @Column(name = "body", nullable = false, columnDefinition = "text")
    var body: String = "",

    @Column(name = "launch_at")
    var launchAt: Instant? = null,

    @Column(name = "bypass_token", length = 128)
    var bypassToken: String? = null,
)
