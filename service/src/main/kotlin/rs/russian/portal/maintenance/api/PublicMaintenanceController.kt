package rs.russian.portal.maintenance.api

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.maintenance.service.MaintenanceSettingsService

@RestController
class PublicMaintenanceController(
    private val maintenanceSettingsService: MaintenanceSettingsService,
) {

    @GetMapping("/public/maintenance", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getPublic(): ResponseEntity<PublicMaintenanceResponse> {
        val p = maintenanceSettingsService.publicPayload()
        return ResponseEntity.ok(
            PublicMaintenanceResponse(
                enabled = p.enabled,
                headline = p.headline,
                body = p.body,
                launchAt = p.launchAt?.toString(),
            ),
        )
    }

    @PostMapping(
        "/public/maintenance/unlock",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    fun unlock(@RequestBody body: MaintenanceUnlockRequest): ResponseEntity<Unit> {
        val token = body.token.trim()
        if (token.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return if (maintenanceSettingsService.validateBypassToken(token)) {
            ResponseEntity.ok().build()
        } else {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }
}

data class PublicMaintenanceResponse(
    val enabled: Boolean,
    val headline: String,
    val body: String,
    val launchAt: String?,
)

data class MaintenanceUnlockRequest(
    val token: String = "",
)
