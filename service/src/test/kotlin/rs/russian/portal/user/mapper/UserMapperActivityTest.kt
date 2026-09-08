package rs.russian.portal.user.mapper

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.oidc.OidcUserInfo
import rs.russian.portal.user.domain.Account

class UserMapperActivityTest {
    @Test
    fun `OIDC profile update preserves disabled status`() {
        val account = Account(id = 42, username = "user", email = "user@example.com",
            fullName = "User", active = false)
        val info = OidcUserInfo(mapOf("sub" to "user", "nickname" to "user",
            "email" to "user@example.com", "name" to "Updated User"))

        UserMapperImpl().update(info, account)

        assertFalse(account.active)
    }
}
