package rs.russian.portal.maintenance.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import rs.russian.portal.maintenance.domain.PortalMaintenanceSettings
import rs.russian.portal.maintenance.repository.PortalMaintenanceSettingsRepository
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.PrivilegedOps
import rs.russian.portal.shared.security.currentUserRoles
import rs.russian.portal.shared.security.realUserLogin
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.AccountRepository
import java.time.Instant
import java.util.Optional

class MaintenanceSettingsServiceTest {

    private val repository = mockk<PortalMaintenanceSettingsRepository>()
    private val accountRepository = mockk<AccountRepository>(relaxed = true)
    private val service = MaintenanceSettingsService(repository, accountRepository)

    @BeforeEach
    fun setUp() {
        PrivilegedOps.approverUsername = "legkov777"
        PrivilegedOps.accountLookup = null
        mockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
    }

    @AfterEach
    fun tearDown() {
        PrivilegedOps.accountLookup = null
        unmockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
    }

    @Test
    fun `publicPayload never exposes bypass token`() {
        every { repository.findById(1) } returns Optional.of(
            PortalMaintenanceSettings(
                id = 1,
                enabled = true,
                headline = "Closed",
                body = "Come later",
                launchAt = Instant.parse("2026-10-01T12:00:00Z"),
                bypassToken = "secret-token-value",
            )
        )

        val payload = service.publicPayload()
        assertTrue(payload.enabled)
        assertEquals("Closed", payload.headline)
        assertEquals("Come later", payload.body)
        assertEquals(Instant.parse("2026-10-01T12:00:00Z"), payload.launchAt)
        // compile-time: PublicMaintenancePayload has no bypassToken field
    }

    @Test
    fun `validateBypassToken accepts matching token when enabled`() {
        every { repository.findById(1) } returns Optional.of(
            PortalMaintenanceSettings(
                id = 1,
                enabled = true,
                bypassToken = "abc123",
            )
        )
        assertTrue(service.validateBypassToken("abc123"))
        assertFalse(service.validateBypassToken("wrong"))
    }

    @Test
    fun `validateBypassToken rejects when disabled`() {
        every { repository.findById(1) } returns Optional.of(
            PortalMaintenanceSettings(
                id = 1,
                enabled = false,
                bypassToken = "abc123",
            )
        )
        assertFalse(service.validateBypassToken("abc123"))
    }

    @Test
    fun `adminPayload forbidden for non privileged`() {
        every { realUserLogin() } returns "volunteer"
        every { currentUserRoles() } returns emptySet()
        assertThrows<NotAuthorizedException> { service.adminPayload() }
    }

    @Test
    fun `adminPayload returns token for privileged`() {
        every { realUserLogin() } returns "legkov777"
        every { currentUserRoles() } returns setOf(UserGroup.ADMIN_VOLUNTEER)
        every { repository.findById(1) } returns Optional.of(
            PortalMaintenanceSettings(
                id = 1,
                enabled = true,
                headline = "H",
                body = "B",
                bypassToken = "tok",
            )
        )
        val payload = service.adminPayload()
        assertEquals("tok", payload.bypassToken)
        assertNull(payload.launchAt)
    }

    @Test
    fun `replaceAdmin creates token when enabling without one`() {
        every { realUserLogin() } returns "admin"
        every { currentUserRoles() } returns setOf(UserGroup.ADMIN_SSO)
        val row = PortalMaintenanceSettings(id = 1, enabled = false, bypassToken = null)
        every { repository.findById(1) } returns Optional.of(row)
        every { repository.save(any()) } answers { firstArg() }

        val result = service.replaceAdmin(
            MaintenanceAdminSaveBody(
                enabled = true,
                headline = "Soon",
                body = "Wait",
                launchAt = null,
                regenerateBypassToken = false,
            )
        )
        assertTrue(result.enabled)
        assertFalse(result.bypassToken.isNullOrBlank())
        verify { repository.save(any()) }
    }
}
