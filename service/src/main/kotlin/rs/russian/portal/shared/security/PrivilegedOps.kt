package rs.russian.portal.shared.security

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import rs.russian.portal.config.AppProperties
import rs.russian.portal.user.domain.enums.UserGroup

/**
 * Shared gate for high-impact ops (impersonation, portal maintenance).
 * Allowed: ADMIN_SSO role (real JWT/OIDC groups) and/or configured leave approver username.
 */
object PrivilegedOps {
    @Volatile
    var approverUsername: String = "legkov777"

    fun isAllowed(realLogin: String?, roles: Set<UserGroup>?): Boolean {
        if (roles?.contains(UserGroup.ADMIN_SSO) == true) return true
        if (realLogin.isNullOrBlank()) return false
        return realLogin.equals(approverUsername, ignoreCase = true)
    }

    fun currentActorAllowed(): Boolean =
        isAllowed(realUserLogin(), currentUserRoles())
}

@Component
class PrivilegedOpsConfigurer(
    private val appProperties: AppProperties,
) {
    @PostConstruct
    fun init() {
        PrivilegedOps.approverUsername = appProperties.leave.approverUsername
    }
}
