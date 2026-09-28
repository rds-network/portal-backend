package rs.russian.portal.applicationjoin.api

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.applicationjoin.service.ApplicationJoinSettingsService

@RestController
class PublicApplicationJoinController(
    private val applicationJoinSettingsService: ApplicationJoinSettingsService,
) {

    @GetMapping("/public/application-join", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getPublic(): ResponseEntity<ApplicationJoinResponse> {
        val p = applicationJoinSettingsService.publicPayload()
        return ResponseEntity.ok(toResponse(p))
    }
}
