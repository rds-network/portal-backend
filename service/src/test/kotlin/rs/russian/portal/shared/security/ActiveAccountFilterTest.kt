package rs.russian.portal.shared.security

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import rs.russian.portal.config.DefaultUserFilter
import rs.russian.portal.user.service.SessionService

class ActiveAccountFilterTest {
    private val access = mockk<AccountAccessService>()
    private val sessions = mockk<SessionService>(relaxed = true)
    private val filter = ActiveAccountFilter(access, sessions)
    private val request = MockHttpServletRequest()
    private val response = MockHttpServletResponse()
    private val chain = mockk<FilterChain>(relaxed = true)

    @AfterEach
    fun cleanup() = SecurityContextHolder.clearContext()

    @Test
    fun `existing OIDC session is rejected and revoked after deactivation`() {
        oidcSession()
        val session = request.session as MockHttpSession
        every { access.isActive(DefaultUserFilter.USERNAME) } returns false

        filter.doFilter(request, response, chain)

        assertEquals(401, response.status)
        assertTrue(session.isInvalid)
        assertNull(SecurityContextHolder.getContext().authentication)
        verify { sessions.invalidate(DefaultUserFilter.USERNAME) }
        verify(exactly = 0) { chain.doFilter(any(), any()) }
    }

    @Test
    fun `active OIDC session can continue`() {
        oidcSession()
        every { access.isActive(DefaultUserFilter.USERNAME) } returns true
        filter.doFilter(request, response, chain)
        verify { chain.doFilter(request, response) }
        verify(exactly = 0) { sessions.invalidate(any<String>()) }
    }

    @Test
    fun `verification outage denies request without destroying session`() {
        oidcSession()
        val session = request.session as MockHttpSession
        every { access.isActive(any()) } throws IllegalStateException("Unavailable")
        filter.doFilter(request, response, chain)
        assertEquals(503, response.status)
        assertFalse(session.isInvalid)
        verify(exactly = 0) { chain.doFilter(any(), any()) }
        verify(exactly = 0) { sessions.invalidate(any<String>()) }
    }

    @Test
    fun `ordinary bearer token cannot bypass inactive account check`() {
        jwt(false)
        every { access.isActive("volunteer") } returns false
        filter.doFilter(request, response, chain)
        assertEquals(401, response.status)
        verify(exactly = 0) { chain.doFilter(any(), any()) }
    }

    @Test
    fun `service JWT does not require a local volunteer account`() {
        jwt(true)
        filter.doFilter(request, response, chain)
        verify { chain.doFilter(request, response) }
        verify(exactly = 0) { access.isActive(any()) }
    }

    @Test
    fun `anonymous requests continue to existing authorization rules`() {
        filter.doFilter(request, response, chain)
        verify { chain.doFilter(request, response) }
        verify(exactly = 0) { access.isActive(any()) }
    }

    private fun oidcSession() {
        SecurityContextHolder.getContext().authentication = DefaultUserFilter(mockk()).getDefaultOAuth2Token()
        request.setSession(MockHttpSession())
    }

    private fun jwt(service: Boolean) {
        val token = Jwt.withTokenValue("token").header("alg", "RS256")
            .subject("volunteer").claim("is_service_account", service).build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(token)
    }
}
