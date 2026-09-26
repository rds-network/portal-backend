package rs.russian.portal.meta.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.shared.security.Authorized
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_SSO

data class MetaVersionDto(
    val build: String,
    val sha: String,
    val builtAt: String? = null,
)

@RestController
@RequestMapping("/meta")
class MetaController {

    @Authorized(allowed = [ADMIN, ADMIN_SSO])
    @GetMapping("/version")
    fun version(): ResponseEntity<MetaVersionDto> {
        val sha = (System.getenv("GIT_SHA") ?: System.getenv("GIT_COMMIT") ?: "dev").take(7)
        val build = System.getenv("BUILD_NUMBER")
            ?: System.getenv("GITHUB_RUN_NUMBER")
            ?: "local"
        val builtAt = System.getenv("BUILD_TIME")
        return ResponseEntity.ok(MetaVersionDto(build = build, sha = sha, builtAt = builtAt))
    }
}
