package rs.russian.portal.shared.security

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import rs.russian.portal.config.AppProperties
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.AccountRepository

/**
 * Shared gate for high-impact ops (impersonation, portal maintenance).
 * Allowed: ADMIN_SSO role (real JWT/OIDC groups) and/or configured leave approver
 * (username, email local-part, or matching Account email/username).
 */
object PrivilegedOps {
    @Volatile
    var approverUsername: String = "legkov777"

    @Volatile
    var accountLookup: ((String) -> Account?)? = null

    fun isAllowed(realLogin: String?, roles: Set<UserGroup>?, account: Account? = null): Boolean {
        if (roles?.contains(UserGroup.ADMIN_SSO) == true) return true
        if (realLogin.isNullOrBlank()) return false
        val approver = approverUsername.trim()
        if (approver.isEmpty()) return false

        val login = realLogin.trim()
        if (login.equals(approver, ignoreCase = true)) return true
        if (loginContainsApprover(login, approver)) return true

        val resolved = account ?: lookupAccount(login)
        return accountMatchesApprover(resolved, approver)
    }

    fun currentActorAllowed(): Boolean =
        isAllowed(realUserLogin(), currentUserRoles())

    private fun lookupAccount(login: String): Account? = accountLookup?.invoke(login)

    private fun loginContainsApprover(login: String, approver: String): Boolean {
        val at = login.indexOf('@')
        if (at > 0 && login.substring(0, at).equals(approver, ignoreCase = true)) return true
        return login.contains(approver, ignoreCase = true)
    }

    private fun accountMatchesApprover(account: Account?, approver: String): Boolean {
        if (account == null) return false
        if (account.username.equals(approver, ignoreCase = true)) return true
        val email = account.email.trim()
        if (email.isEmpty()) return false
        if (email.equals(approver, ignoreCase = true)) return true
        if (email.startsWith(approver, ignoreCase = true)) return true
        val at = email.indexOf('@')
        if (at > 0 && email.substring(0, at).equals(approver, ignoreCase = true)) return true
        return false
    }
}

@Component
class PrivilegedOpsConfigurer(
    private val appProperties: AppProperties,
    private val accountRepository: AccountRepository,
) {
    @PostConstruct
    fun init() {
        PrivilegedOps.approverUsername = appProperties.leave.approverUsername
        PrivilegedOps.accountLookup = { login ->
            accountRepository.findByUsername(login).orElse(null)
                ?: accountRepository.findByEmail(login).orElse(null)
        }
    }
}
