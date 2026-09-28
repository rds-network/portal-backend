package rs.russian.portal.applicationjoin.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import rs.russian.portal.applicationjoin.domain.PortalApplicationJoinSettings
import rs.russian.portal.applicationjoin.repository.PortalApplicationJoinSettingsRepository
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.PrivilegedOps
import rs.russian.portal.shared.security.currentUserRoles
import rs.russian.portal.shared.security.realUserLogin
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.AccountRepository
import java.util.Optional

class ApplicationJoinSettingsServiceTest {

    private val repository = mockk<PortalApplicationJoinSettingsRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val service = ApplicationJoinSettingsService(repository, accountRepository)

    @BeforeEach
    fun setUp() {
        PrivilegedOps.approverUsername = "legkov777"
        PrivilegedOps.accountLookup = null
        mockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
        every { accountRepository.findByUsername(any()) } returns Optional.empty()
        every { accountRepository.findByEmail(any()) } returns Optional.empty()
    }

    @AfterEach
    fun tearDown() {
        PrivilegedOps.accountLookup = null
        unmockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
    }

    @Test
    fun `publicPayload returns CMS fields`() {
        every { repository.findById(1) } returns Optional.of(
            PortalApplicationJoinSettings(
                id = 1,
                title = "Title",
                body = "# Hello",
                agree1Label = "A1",
                agree2Label = "A2",
                buttonLabel = "Go",
            )
        )

        val payload = service.publicPayload()
        assertEquals("Title", payload.title)
        assertEquals("# Hello", payload.body)
        assertEquals("A1", payload.agree1Label)
        assertEquals("A2", payload.agree2Label)
        assertEquals("Go", payload.buttonLabel)
    }

    @Test
    fun `adminPayload forbidden for non privileged`() {
        every { realUserLogin() } returns "volunteer"
        every { currentUserRoles() } returns emptySet()
        assertThrows<NotAuthorizedException> { service.adminPayload() }
    }

    @Test
    fun `replaceAdmin saves for ADMIN_SSO`() {
        every { realUserLogin() } returns "admin"
        every { currentUserRoles() } returns setOf(UserGroup.ADMIN_SSO)
        val row = PortalApplicationJoinSettings(id = 1)
        every { repository.findById(1) } returns Optional.of(row)
        every { repository.save(any()) } answers { firstArg() }

        val result = service.replaceAdmin(
            ApplicationJoinAdminSaveBody(
                title = "New title",
                body = "Body md",
                agree1Label = "Agree one",
                agree2Label = "Agree two",
                buttonLabel = "Submit",
            )
        )
        assertEquals("New title", result.title)
        assertEquals("Body md", result.body)
        assertEquals("Agree one", result.agree1Label)
        assertEquals("Agree two", result.agree2Label)
        assertEquals("Submit", result.buttonLabel)
        assertTrue(result.updatedAt != null)
        verify { repository.save(any()) }
    }

    @Test
    fun `adminPayload allowed for Leonid`() {
        every { realUserLogin() } returns "legkov777"
        every { currentUserRoles() } returns emptySet()
        every { repository.findById(1) } returns Optional.of(
            PortalApplicationJoinSettings(id = 1, title = "T")
        )
        assertEquals("T", service.adminPayload().title)
    }
}
