package rs.russian.portal.shared.security

import io.authentik.model.User
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.DepersonalizationStatus
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.authentik.AuthentikService

class AccountAccessServiceTest {
    private val repository = mockk<AccountRepository>()
    private val authentik = mockk<AuthentikService>()
    private val service = AccountAccessService(repository, authentik)
    private val account = Account(id = 42, username = "user", email = "user@example.com", fullName = "User")

    @Test
    fun `local deactivation takes effect even with cached active remote status`() {
        every { repository.findForAuthenticationByUsername("user") } returns account
        remote(true)
        assertTrue(service.isActive("user"))
        assertTrue(service.isActive("user"))
        account.active = false
        assertFalse(service.isActive("user"))
        verify(exactly = 1) { authentik.getUser(42) }
    }

    @Test
    fun `direct authentik deactivation blocks a locally active account`() {
        every { repository.findForAuthenticationByUsername("user") } returns account
        remote(false)
        assertFalse(service.isActive("user"))
    }

    @Test
    fun `deleted remote user is denied`() {
        every { repository.findForAuthenticationByUsername("user") } returns account
        every { authentik.getUser(42) } returns null
        assertFalse(service.isActive("user"))
    }

    @Test
    fun `missing local account is denied`() {
        every { repository.findForAuthenticationByUsername("user") } returns null
        assertFalse(service.isActive("user"))
        verify(exactly = 0) { authentik.getUser(any<Int>()) }
    }

    @Test
    fun `depersonalized account is denied even if marked active`() {
        account.depersonalizationStatus = DepersonalizationStatus.DEPERSONALIZED
        every { repository.findForAuthenticationByUsername("user") } returns account
        assertFalse(service.isActive("user"))
        verify(exactly = 0) { authentik.getUser(any<Int>()) }
    }

    @Test
    fun `remote lookup failures are not cached as permission to enter`() {
        every { repository.findForAuthenticationByUsername("user") } returns account
        every { authentik.getUser(42) } throws IllegalStateException("Unavailable")
        assertThrows<IllegalStateException> { service.isActive("user") }
        remote(false)
        assertFalse(service.isActive("user"))
        verify(exactly = 2) { authentik.getUser(42) }
    }

    private fun remote(active: Boolean) {
        val user = mockk<User>()
        every { user.isActive } returns active
        every { authentik.getUser(42) } returns user
    }
}
