package rs.russian.portal.shared.security

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import rs.russian.portal.config.AppProperties
import rs.russian.portal.config.SecurityConfig
import rs.russian.portal.user.service.AccountService
import rs.russian.portal.user.service.SessionService

@ExtendWith(SpringExtension::class)
@WebAppConfiguration
@ActiveProfiles("account-security-test")
@ContextConfiguration(classes = [AccountSecurityChainTest.Config::class])
class AccountSecurityChainTest {
    @Autowired private lateinit var context: WebApplicationContext
    @Autowired private lateinit var access: AccountAccessService
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setup() {
        clearMocks(access)
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity()).build()
    }

    @Test
    fun `real security chain rejects inactive OIDC principal`() {
        every { access.isActive("user") } returns false
        mvc.perform(get("/protected-test").with(oidcLogin().idToken { it.claim("nickname", "user") }))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `real security chain allows active OIDC principal`() {
        every { access.isActive("user") } returns true
        mvc.perform(get("/protected-test").with(oidcLogin().idToken { it.claim("nickname", "user") }))
            .andExpect(status().isOk)
    }

    @Test
    fun `public endpoints remain anonymous and protected endpoints require login`() {
        mvc.perform(get("/cities")).andExpect(status().isOk)
        mvc.perform(get("/protected-test")).andExpect(status().isUnauthorized)
    }

    @TestConfiguration
    @EnableWebMvc
    @Import(SecurityConfig::class, Endpoints::class)
    class Config {
        @Bean fun accountService(): AccountService = mockk(relaxed = true)
        @Bean fun sessionService(): SessionService = mockk(relaxed = true)
        @Bean fun accountAccessService(): AccountAccessService = mockk()
        @Bean fun appProperties() = AppProperties("http://localhost:3000")
        @Bean fun jwtDecoder(): JwtDecoder = mockk()
        @Bean fun clientRegistrationRepository(): ClientRegistrationRepository = InMemoryClientRegistrationRepository(
            ClientRegistration.withRegistrationId("authentik")
                .clientId("client").clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/oauth2/code").scope("openid", "profile")
                .authorizationUri("https://id.example.com/authorize")
                .tokenUri("https://id.example.com/token")
                .jwkSetUri("https://id.example.com/jwks")
                .userInfoUri("https://id.example.com/userinfo")
                .userNameAttributeName("nickname").build()
        )
    }

    @RestController
    @Profile("account-security-test")
    class Endpoints {
        @GetMapping("/protected-test", "/cities") fun endpoint() = "ok"
    }
}
