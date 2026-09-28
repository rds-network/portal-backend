package rs.russian.portal.impersonate.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import rs.russian.portal.impersonate.ImpersonationKeys
import rs.russian.portal.impersonate.api.ImpersonationStartRequest
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.PrivilegedOps
import rs.russian.portal.shared.security.currentUserRoles
import rs.russian.portal.shared.security.realUserLogin
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.AccountRepository
import java.util.Optional

class ImpersonationServiceTest {

    private val accountRepository = mockk<AccountRepository>()
    private val service = ImpersonationService(accountRepository)

    private val leonid = Account(
        id = 1,
        username = "legkov777",
        email = "leonid@example.com",
        fullName = "Leonid",
        active = true,
        groups = setOf(UserGroup.ADMIN_VOLUNTEER),
    )
    private val adminSso = Account(
        id = 2,
        username = "admin_sso",
        email = "admin@example.com",
        fullName = "Admin SSO",
        active = true,
        groups = setOf(UserGroup.ADMIN_SSO),
    )
    private val volunteer = Account(
        id = 3,
        username = "volunteer",
        email = "vol@example.com",
        fullName = "Volunteer Name",
        active = true,
        groups = emptySet(),
    )
    private val stranger = Account(
        id = 4,
        username = "stranger",
        email = "stranger@example.com",
        fullName = "Stranger",
        active = true,
        groups = emptySet(),
    )

    @BeforeEach
    fun setUp() {
        PrivilegedOps.approverUsername = "legkov777"
        PrivilegedOps.accountLookup = null
        mockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
        every { accountRepository.findByUsername("legkov777") } returns Optional.of(leonid)
        every { accountRepository.findByUsername("admin_sso") } returns Optional.of(adminSso)
        every { accountRepository.findByUsername("volunteer") } returns Optional.of(volunteer)
        every { accountRepository.findByUsername("stranger") } returns Optional.of(stranger)
        every { accountRepository.findByEmail(any()) } returns Optional.empty()
    }

    @AfterEach
    fun tearDown() {
        PrivilegedOps.accountLookup = null
        unmockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
    }

    @Test
    fun `leave approver can start impersonation`() {
        every { realUserLogin() } returns "legkov777"
        every { currentUserRoles() } returns setOf(UserGroup.ADMIN_VOLUNTEER)
        val session = mockk<HttpSession>(relaxed = true)
        val request = mockk<HttpServletRequest>()
        var stored: String? = null
        every { request.getSession(true) } returns session
        every { request.getSession(false) } returns session
        every { request.getHeader(ImpersonationKeys.HEADER_USERNAME) } returns null
        every { session.setAttribute(ImpersonationKeys.SESSION_USERNAME, any()) } answers {
            stored = secondArg()
        }
        every { session.getAttribute(ImpersonationKeys.SESSION_USERNAME) } answers { stored }

        val status = service.start(ImpersonationStartRequest("volunteer"), request)

        assertTrue(status.active)
        assertTrue(status.canImpersonate)
        assertEquals("volunteer", status.targetUsername)
        assertEquals("Volunteer Name", status.targetFullName)
        assertEquals("legkov777", status.realUsername)
    }

    @Test
    fun `ADMIN_SSO can start impersonation`() {
        every { realUserLogin() } returns "admin_sso"
        every { currentUserRoles() } returns setOf(UserGroup.ADMIN_SSO)
        val session = mockk<HttpSession>(relaxed = true)
        val request = mockk<HttpServletRequest>()
        var stored: String? = null
        every { request.getSession(true) } returns session
        every { request.getSession(false) } returns session
        every { request.getHeader(ImpersonationKeys.HEADER_USERNAME) } returns null
        every { session.setAttribute(ImpersonationKeys.SESSION_USERNAME, any()) } answers {
            stored = secondArg()
        }
        every { session.getAttribute(ImpersonationKeys.SESSION_USERNAME) } answers { stored }

        val status = service.start(ImpersonationStartRequest("volunteer"), request)
        assertTrue(status.canImpersonate)
        assertTrue(status.active)
    }

    @Test
    fun `regular volunteer cannot start impersonation`() {
        every { realUserLogin() } returns "stranger"
        every { currentUserRoles() } returns emptySet()
        val request = mockk<HttpServletRequest>()

        assertThrows<NotAuthorizedException> {
            service.start(ImpersonationStartRequest("volunteer"), request)
        }
    }

    @Test
    fun `cannot impersonate ADMIN_SSO`() {
        every { realUserLogin() } returns "legkov777"
        every { currentUserRoles() } returns setOf(UserGroup.ADMIN_VOLUNTEER)
        val request = mockk<HttpServletRequest>()

        assertThrows<InvalidRequestException> {
            service.start(ImpersonationStartRequest("admin_sso"), request)
        }
    }

    @Test
    fun `status without privilege reports inactive even if session set`() {
        every { realUserLogin() } returns "stranger"
        every { currentUserRoles() } returns emptySet()
        val session = mockk<HttpSession>()
        val request = mockk<HttpServletRequest>()
        every { request.getSession(false) } returns session
        every { request.getHeader(ImpersonationKeys.HEADER_USERNAME) } returns null
        every { session.getAttribute(ImpersonationKeys.SESSION_USERNAME) } returns "volunteer"

        val status = service.status(request)
        assertFalse(status.canImpersonate)
        assertFalse(status.active)
    }

    @Test
    fun `approver email preferred_username can start impersonation`() {
        every { realUserLogin() } returns "legkov777@gmail.com"
        every { currentUserRoles() } returns setOf(UserGroup.ADMIN_VOLUNTEER)
        every { accountRepository.findByUsername("legkov777@gmail.com") } returns Optional.empty()
        every { accountRepository.findByEmail("legkov777@gmail.com") } returns Optional.of(leonid)
        val session = mockk<HttpSession>(relaxed = true)
        val request = mockk<HttpServletRequest>()
        var stored: String? = null
        every { request.getSession(true) } returns session
        every { request.getSession(false) } returns session
        every { request.getHeader(ImpersonationKeys.HEADER_USERNAME) } returns null
        every { session.setAttribute(ImpersonationKeys.SESSION_USERNAME, any()) } answers {
            stored = secondArg()
        }
        every { session.getAttribute(ImpersonationKeys.SESSION_USERNAME) } answers { stored }

        val status = service.start(ImpersonationStartRequest("volunteer"), request)

        assertTrue(status.canImpersonate)
        assertTrue(status.active)
        assertEquals("legkov777", status.realUsername)
    }

    @Test
    fun `account username match allows status when oidc login differs`() {
        every { realUserLogin() } returns "other_oidc_login"
        every { currentUserRoles() } returns setOf(UserGroup.ADMIN_VOLUNTEER)
        every { accountRepository.findByUsername("other_oidc_login") } returns Optional.of(leonid)
        every { accountRepository.findByEmail("other_oidc_login") } returns Optional.empty()

        val status = service.status(null)
        assertTrue(status.canImpersonate)
        assertEquals("legkov777", status.realUsername)
    }
}
