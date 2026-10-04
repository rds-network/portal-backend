package rs.russian.portal.applicationjoin.api

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.applicationjoin.service.ApplicationJoinAdminSaveBody
import rs.russian.portal.applicationjoin.service.ApplicationJoinPayload
import rs.russian.portal.applicationjoin.service.ApplicationJoinSettingsService

@RestController
class AdminApplicationJoinController(
    private val applicationJoinSettingsService: ApplicationJoinSettingsService,
) {

    @GetMapping("/admin/application-join", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getAdmin(): ResponseEntity<ApplicationJoinResponse> {
        val p = applicationJoinSettingsService.adminPayload()
        return ResponseEntity.ok(toResponse(p))
    }

    @PutMapping(
        "/admin/application-join",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    fun putAdmin(@RequestBody body: ApplicationJoinAdminSaveBody): ResponseEntity<ApplicationJoinResponse> {
        val p = applicationJoinSettingsService.replaceAdmin(body)
        return ResponseEntity.ok(toResponse(p))
    }
}

data class ApplicationJoinResponse(
    val title: String,
    val body: String,
    val agree1Label: String,
    val agree2Label: String,
    val buttonLabel: String,
    val updatedAt: String?,
)

internal fun toResponse(p: ApplicationJoinPayload) =
    ApplicationJoinResponse(
        title = p.title,
        body = p.body,
        agree1Label = p.agree1Label,
        agree2Label = p.agree2Label,
        buttonLabel = p.buttonLabel,
        updatedAt = p.updatedAt?.toString(),
    )
