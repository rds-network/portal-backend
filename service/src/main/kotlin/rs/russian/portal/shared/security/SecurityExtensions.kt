package rs.russian.portal.shared.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.oidc.OidcUserInfo
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import rs.russian.portal.impersonate.service.ImpersonationService
import rs.russian.portal.user.domain.enums.UserGroup

fun currentAuthentication(): Authentication? = SecurityContextHolder.getContext().authentication

fun currentHttpRequest(): HttpServletRequest? =
    (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request

fun currentUser(): OidcUser? {
    return try {
        currentAuthentication()?.principal as? OidcUser
    } catch (_: Exception) {
        null
    }
}

/** Real authenticated principal login (never switched by impersonation). */
fun realUserLogin(): String? {
    val authentication = currentAuthentication()
    return when (val principal = authentication?.principal) {
        is OidcUser -> principal.nickName
        is Jwt -> principal.getClaimAsString("preferred_username") ?: principal.subject
        else -> null
    }
}

/**
 * Effective login for business logic: impersonation target when the real actor is allowed
 * and a target is set (session or [rs.russian.portal.impersonate.ImpersonationKeys.HEADER_USERNAME]),
 * otherwise [realUserLogin].
 */
fun effectiveUserLogin(): String? {
    val real = realUserLogin() ?: return null
    if (!PrivilegedOps.isAllowed(real, currentUserRoles())) return real
    val target = ImpersonationService.resolveTarget(currentHttpRequest()) ?: return real
    if (target.equals(real, ignoreCase = true)) return real
    return target
}

/** Alias for [effectiveUserLogin] so existing call sites see the impersonated volunteer. */
fun currentUserLogin(): String? = effectiveUserLogin()

fun currentUserRoles(): Set<UserGroup>? {
    return when (val authentication = currentAuthentication()) {
        is JwtAuthenticationToken -> {
            val groups = authentication.tokenAttributes["groups"]
            if (groups is Collection<*>) {
                groups.filterIsInstance<String>().mapNotNull { UserGroup.of(it) }.toSet()
            } else {
                emptySet()
            }
        }

        else -> {
            val principal = authentication?.principal
            if (principal is OidcUser) {
                principal.userInfo?.userGroups()
            } else {
                null
            }
        }
    }
}

fun OidcUserInfo.userGroups(): Set<UserGroup> {
    val groups = mutableSetOf<UserGroup>()
    if (claims["groups"] is Collection<*>) {
        (claims["groups"] as Collection<*>).forEach { oauthGroup ->
            if (oauthGroup is String) {
                UserGroup.of(oauthGroup)?.let { groups.add(it) }
            }
        }
    }
    return groups
}
