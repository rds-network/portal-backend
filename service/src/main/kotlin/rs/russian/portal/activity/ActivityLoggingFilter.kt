package rs.russian.portal.activity

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import rs.russian.portal.activity.service.ActivityService
import rs.russian.portal.shared.security.currentUserLogin

class ActivityLoggingFilter(
    private val activityService: ActivityService,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (request.method.equals("OPTIONS", ignoreCase = true)) return true
        val path = request.requestURI
        return path.startsWith("/actuator") ||
            path.startsWith("/csrf") ||
            path.startsWith("/oauth2") ||
            path.contains(".") && !path.startsWith("/api")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        filterChain.doFilter(request, response)
        val path = request.requestURI.removePrefix("/api")
        if (path.startsWith("/activity") && request.method == "GET") return
        val ip = request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.remoteAddr
        activityService.record(
            username = currentUserLogin(),
            ip = ip,
            method = request.method,
            path = path.ifBlank { request.requestURI },
            query = request.queryString,
        )
    }
}
