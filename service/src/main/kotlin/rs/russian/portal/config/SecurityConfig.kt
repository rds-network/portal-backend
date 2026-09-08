package rs.russian.portal.config

import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponse.SC_MOVED_PERMANENTLY
import jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders.LOCATION
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.header.HeaderWriterFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import rs.russian.portal.clanovi.ClanoviApiKeyFilter
import rs.russian.portal.shared.security.AccountAccessService
import rs.russian.portal.shared.security.ActiveAccountFilter
import rs.russian.portal.shared.security.ServiceAccountLoggingFilter
import rs.russian.portal.user.service.AccountService
import rs.russian.portal.user.service.SessionService

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val accountService: AccountService,
    private val appProperties: AppProperties,
) {

    private val clanoviApiKeyFilter = ClanoviApiKeyFilter(appProperties)

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            val (patterns, origins) = appProperties.allowedOrigins.partition { it.contains("*") }
            allowedOrigins = origins.ifEmpty { null }
            allowedOriginPatterns = patterns.ifEmpty { null }
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            exposedHeaders = listOf(LOCATION)
            allowCredentials = true
            maxAge = 3600L
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }

    @Bean
    @Profile("!no-auth")
    fun securityFilterChain(
        httpSecurity: HttpSecurity,
        accountAccessService: AccountAccessService,
        sessionService: SessionService,
    ): SecurityFilterChain = httpSecurity
        .cors {
            it.configurationSource(corsConfigurationSource())
        }
        .csrf {
            // Cookie-based CSRF tokens for SPA frontend
            it.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            // Plain handler for SPA (no XOR encoding needed for header-based tokens)
            it.csrfTokenRequestHandler(CsrfTokenRequestAttributeHandler())
            // Disable CSRF for public endpoints (they have captcha protection) or if it's a service request (JWT)
            it.ignoringRequestMatchers(*CSRF_DISABLED_ENDPOINTS)
            it.ignoringRequestMatchers({ req ->
                req.getHeader("Authorization")?.startsWith("Bearer ") ?: false
            })
        }
        .authorizeHttpRequests {
            it
                .requestMatchers(*WHITELIST).permitAll()
                .anyRequest().authenticated()
        }
        .oauth2Login {
            it
                .authorizationEndpoint { endpoint ->
                    endpoint.baseUri("/oauth2/login")
                }
                .redirectionEndpoint { endpoint ->
                    endpoint.baseUri("/oauth2/code")
                }
                .userInfoEndpoint { endpoint ->
                    val delegate = OidcUserService()
                    endpoint.oidcUserService { request ->
                        val user = delegate.loadUser(request)
                        try {
                            // Validate before Spring stores an authenticated SecurityContext/session.
                            accountService.createOrUpdateAccount(user)
                        } catch (exception: OAuth2AuthenticationException) {
                            throw exception
                        } catch (exception: Exception) {
                            throw OAuth2AuthenticationException(OAuth2Error("temporarily_unavailable"), exception)
                        }
                        user
                    }
                }
                .failureHandler { request, res, exception ->
                    SecurityContextHolder.clearContext()
                    request.getSession(false)?.invalidate()
                    val unavailable = (exception as? OAuth2AuthenticationException)?.error?.errorCode == "temporarily_unavailable"
                    res.sendError(if (unavailable) HttpServletResponse.SC_SERVICE_UNAVAILABLE else SC_UNAUTHORIZED)
                }
                .successHandler { _, res, _ ->
                    res.status = SC_MOVED_PERMANENTLY
                    res.setHeader(LOCATION, "${appProperties.frontendUri}/login")
                }
        }
        .exceptionHandling {
            it.authenticationEntryPoint { _, res, _ ->
                res.sendError(SC_UNAUTHORIZED)
            }
        }
        .sessionManagement {
            it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
        }
        .oauth2ResourceServer {
            it.jwt {}
        }
        .addFilterBefore(ActiveAccountFilter(accountAccessService, sessionService), AuthorizationFilter::class.java)
        .addFilterAfter(ServiceAccountLoggingFilter(), HeaderWriterFilter::class.java)
        .addFilterAfter(clanoviApiKeyFilter, HeaderWriterFilter::class.java)
        .headers {
            it.contentSecurityPolicy { csp ->
                csp.policyDirectives(buildCspPolicy())
            }
        }
        .build()

    @Bean
    @Profile("no-auth")
    fun securityFilterChainNoAuth(
        httpSecurity: HttpSecurity,
        defaultUserFilter: DefaultUserFilter,
    ): SecurityFilterChain = httpSecurity
        .cors {
            it.configurationSource(corsConfigurationSource())
        }
        .csrf {
            it.disable()
        }
        .authorizeHttpRequests {
            it.anyRequest().permitAll()
        }
        .sessionManagement {
            it.sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
        }
        .addFilterBefore(clanoviApiKeyFilter, OAuth2LoginAuthenticationFilter::class.java)
        .addFilterBefore(defaultUserFilter, OAuth2LoginAuthenticationFilter::class.java)
        .build()

    private fun buildCspPolicy(): String {
        val allowedOrigins = appProperties.allowedOrigins.joinToString(" ")
        return listOf(
            "default-src 'self'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' data: blob:",
            "connect-src 'self' $allowedOrigins",
        ).joinToString("; ")
    }

    companion object {
        val WHITELIST = arrayOf(
            "/actuator/health",
            "/application/create",
            "/application/status/{id}",
            "/application/searchByEmail",
            "/turnstile",
            "/cities",
            "/cities/search",
            "/cities/{code}",
            "/csrf",
            "/programs",
            "/projects",
        )

        // Endpoints that don't need CSRF (public endpoints protected by captcha or non-sensitive)
        private val CSRF_DISABLED_ENDPOINTS = arrayOf(
            "/application/create",
            "/actuator/health",
            "/turnstile",
            "/clanovi/**",
        )
    }

}
