package rs.russian.portal.shared.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.filter.OncePerRequestFilter
import rs.russian.portal.user.service.SessionService

// Created only in the authenticated security chain, not registered as a servlet filter.
class ActiveAccountFilter(
    private val accountAccessService: AccountAccessService,
    private val sessionService: SessionService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authentication = currentAuthentication()
        val username = when {
            authentication?.principal is OidcUser -> (authentication.principal as OidcUser).nickName
            authentication is JwtAuthenticationToken -> {
                // Service identities are not synchronized into the local account table.
                if (authentication.token.getClaimAsBoolean("is_service_account") == true) {
                    filterChain.doFilter(request, response)
                    return
                }
                currentUserLogin()
            }
            else -> {
                filterChain.doFilter(request, response)
                return
            }
        }
        val active = try {
            !username.isNullOrBlank() && accountAccessService.isActive(username)
        } catch (exception: Exception) {
            // An unavailable identity provider must not grant access or destroy a valid session.
            log.warn("Unable to verify account activity ({})", exception.javaClass.simpleName)
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE)
            return
        }
        if (!active) {
            SecurityContextHolder.clearContext()
            request.getSession(false)?.invalidate()
            if (!username.isNullOrBlank()) sessionService.invalidate(username)
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
            return
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ActiveAccountFilter::class.java)
    }
}
