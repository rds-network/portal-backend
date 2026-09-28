package rs.russian.portal.maintenance.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.maintenance.domain.PortalMaintenanceSettings
import rs.russian.portal.maintenance.repository.PortalMaintenanceSettingsRepository
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.PrivilegedOps
import rs.russian.portal.shared.security.currentUserRoles
import rs.russian.portal.shared.security.realUserLogin
import rs.russian.portal.user.repository.AccountRepository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class MaintenanceSettingsService(
    private val repository: PortalMaintenanceSettingsRepository,
    private val accountRepository: AccountRepository,
) {

    fun getRow(): PortalMaintenanceSettings =
        repository.findById(1).orElseGet { repository.save(defaultRow()) }

    fun publicPayload(): PublicMaintenancePayload {
        val row = getRow()
        return PublicMaintenancePayload(
            enabled = row.enabled,
            headline = row.headline,
            body = row.body,
            launchAt = row.launchAt,
        )
    }

    fun adminPayload(): AdminMaintenancePayload {
        assertPrivileged()
        val row = getRow()
        return AdminMaintenancePayload(
            enabled = row.enabled,
            headline = row.headline,
            body = row.body,
            launchAt = row.launchAt,
            bypassToken = row.bypassToken,
        )
    }

    fun validateBypassToken(token: String): Boolean {
        val row = repository.findById(1).orElse(null) ?: return false
        if (!row.enabled) return false
        val secret = row.bypassToken ?: return false
        return constantTimeEquals(secret, token)
    }

    @Transactional
    fun replaceAdmin(body: MaintenanceAdminSaveBody): AdminMaintenancePayload {
        assertPrivileged()
        val row = getRow()
        row.enabled = body.enabled
        row.headline = body.headline.take(500)
        row.body = body.body.take(20_000)
        row.launchAt = body.launchAt?.trim()?.takeIf { it.isNotEmpty() }?.let {
            runCatching { Instant.parse(it) }.getOrNull()
        }
        if (body.regenerateBypassToken) {
            row.bypassToken = newBypassToken()
        }
        if (row.enabled && row.bypassToken.isNullOrBlank()) {
            row.bypassToken = newBypassToken()
        }
        repository.save(row)
        return adminPayload()
    }

    fun assertPrivileged() {
        val realLogin = realUserLogin()
        val account = realLogin?.trim()?.takeIf { it.isNotEmpty() }?.let { key ->
            accountRepository.findByUsername(key).orElse(null)
                ?: accountRepository.findByEmail(key).orElse(null)
        }
        if (!PrivilegedOps.isAllowed(realLogin, currentUserRoles(), account)) {
            throw NotAuthorizedException()
        }
    }

    private fun defaultRow(): PortalMaintenanceSettings =
        PortalMaintenanceSettings(
            id = 1,
            enabled = false,
            headline = "",
            body = "",
            launchAt = null,
            bypassToken = null,
        )

    private fun newBypassToken(): String =
        UUID.randomUUID().toString().replace("-", "") +
            UUID.randomUUID().toString().replace("-", "").take(16)

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val ba = a.toByteArray(StandardCharsets.UTF_8)
        val bb = b.toByteArray(StandardCharsets.UTF_8)
        if (ba.size != bb.size) return false
        return MessageDigest.isEqual(ba, bb)
    }
}

data class PublicMaintenancePayload(
    val enabled: Boolean,
    val headline: String,
    val body: String,
    val launchAt: Instant?,
)

data class AdminMaintenancePayload(
    val enabled: Boolean,
    val headline: String,
    val body: String,
    val launchAt: Instant?,
    val bypassToken: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MaintenanceAdminSaveBody(
    val enabled: Boolean,
    val headline: String,
    val body: String,
    val launchAt: String? = null,
    val regenerateBypassToken: Boolean = false,
)
