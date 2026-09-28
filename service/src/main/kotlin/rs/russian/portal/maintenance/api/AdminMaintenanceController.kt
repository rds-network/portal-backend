package rs.russian.portal.maintenance.api

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.maintenance.service.MaintenanceAdminSaveBody
import rs.russian.portal.maintenance.service.MaintenanceSettingsService

@RestController
class AdminMaintenanceController(
    private val maintenanceSettingsService: MaintenanceSettingsService,
) {

    @GetMapping("/admin/maintenance", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getAdmin(): ResponseEntity<AdminMaintenanceResponse> {
        val p = maintenanceSettingsService.adminPayload()
        return ResponseEntity.ok(
            AdminMaintenanceResponse(
                enabled = p.enabled,
                headline = p.headline,
                body = p.body,
                launchAt = p.launchAt?.toString(),
                bypassToken = p.bypassToken,
            ),
        )
    }

    @PutMapping(
        "/admin/maintenance",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    fun putAdmin(@RequestBody body: MaintenanceAdminSaveBody): ResponseEntity<AdminMaintenanceResponse> {
        val p = maintenanceSettingsService.replaceAdmin(body)
        return ResponseEntity.ok(
            AdminMaintenanceResponse(
                enabled = p.enabled,
                headline = p.headline,
                body = p.body,
                launchAt = p.launchAt?.toString(),
                bypassToken = p.bypassToken,
            ),
        )
    }
}

data class AdminMaintenanceResponse(
    val enabled: Boolean,
    val headline: String,
    val body: String,
    val launchAt: String?,
    val bypassToken: String?,
)
