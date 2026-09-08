package rs.russian.portal.user.service

import io.authentik.model.User
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.oidc.OidcUserInfo
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import rs.russian.portal.config.DefaultUserFilter
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.DepersonalizationStatus
import rs.russian.portal.user.mapper.UserMapper
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.authentik.AuthentikService
import java.util.Optional

class AccountAuthenticationTest {
    private val mapper = mockk<UserMapper>(relaxed = true)
    private val repository = mockk<AccountRepository>(relaxed = true)
    private val authentik = mockk<AuthentikService>(relaxed = true)
    private val sessions = mockk<SessionService>(relaxed = true)
    private val service = AccountService(mapper, mockk(), mockk(), mockk(), mockk(), mockk(), repository,
        authentik, mockk(), sessions)
    private val principal = DefaultUserFilter(mockk()).getDefaultOAuth2Token().principal as OidcUser
    private val account = Account(id = 42, username = DefaultUserFilter.USERNAME,
        email = DefaultUserFilter.EMAIL, fullName = "User")

    @Test
    fun `OIDC login rejects inactive authentik user before updating account`() {
        remote(false)
        assertThrows<OAuth2AuthenticationException> { service.createOrUpdateAccount(principal) }
        verify(exactly = 0) { mapper.update(any<OidcUserInfo>(), any()) }
        verify(exactly = 0) { repository.saveAndFlush(any<Account>()) }
    }

    @Test
    fun `OIDC login cannot reactivate a locally disabled account`() {
        remote(true)
        account.active = false
        assertThrows<OAuth2AuthenticationException> { service.createOrUpdateAccount(principal) }
        assertFalse(account.active)
        verify(exactly = 0) { mapper.update(any<OidcUserInfo>(), any()) }
    }

    @Test
    fun `OIDC login rejects depersonalized account`() {
        remote(true)
        account.depersonalizationStatus = DepersonalizationStatus.DEPERSONALIZED
        assertThrows<OAuth2AuthenticationException> { service.createOrUpdateAccount(principal) }
        verify(exactly = 0) { repository.saveAndFlush(any<Account>()) }
    }

    @Test
    fun `OIDC login rejects missing remote user`() {
        every { authentik.getUser(DefaultUserFilter.EMAIL) } returns null
        assertThrows<OAuth2AuthenticationException> { service.createOrUpdateAccount(principal) }
    }

    @Test
    fun `active user can log in`() {
        remote(true)
        service.createOrUpdateAccount(principal)
        verify { mapper.update(principal.userInfo, account) }
        verify { repository.saveAndFlush(account) }
    }

    @Test
    fun `first login creates active user after remote verification`() {
        remote(true)
        every { repository.findById(42) } returns Optional.empty()
        every { mapper.map(principal.userInfo) } returns account
        service.createOrUpdateAccount(principal)
        verify { repository.saveAndFlush(match<Account> { it.id == 42 && it.active }) }
    }

    @Test
    fun `deactivation updates authentik and revokes portal sessions`() {
        every { repository.findById(42) } returns Optional.of(account)
        service.switchActiveState(42, false)
        assertFalse(account.active)
        verify { authentik.switchActiveState(account, false) }
        verify { sessions.invalidate(account.username) }
    }

    @Test
    fun `repeated deactivation still revokes sessions`() {
        account.active = false
        every { repository.findById(42) } returns Optional.of(account)
        service.switchActiveState(42, false)
        verify { sessions.invalidate(account.username) }
    }

    @Test
    fun `scheduler saving inactive account revokes sessions`() {
        account.active = false
        every { repository.saveAndFlush(account) } returns account
        service.save(account)
        verify { sessions.invalidate(account.username) }
    }

    private fun remote(active: Boolean) {
        val user = mockk<User>()
        every { user.isActive } returns active
        every { user.pk } returns 42
        every { user.username } returns account.username
        every { authentik.getUser(DefaultUserFilter.EMAIL) } returns user
        every { repository.findById(42) } returns Optional.of(account)
        every { repository.saveAndFlush(any<Account>()) } answers { firstArg() }
    }
}
