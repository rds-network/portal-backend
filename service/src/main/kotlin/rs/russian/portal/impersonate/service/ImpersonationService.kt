package rs.russian.portal.impersonate.service

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.impersonate.ImpersonationKeys
import rs.russian.portal.impersonate.api.ImpersonationStartRequest
import rs.russian.portal.impersonate.api.ImpersonationStatusDto
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.PrivilegedOps
import rs.russian.portal.shared.security.currentHttpRequest
import rs.russian.portal.shared.security.realUserLogin
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.AccountRepository

@Service
class ImpersonationService(
    private val accountRepository: AccountRepository,
) {

    @Transactional(readOnly = true)
    fun status(request: HttpServletRequest? = currentHttpRequest()): ImpersonationStatusDto {
        val realLogin = realUserLogin()
        val realAccount = resolveAccount(realLogin)
        val canImpersonate = PrivilegedOps.isAllowed(
            realLogin,
            rs.russian.portal.shared.security.currentUserRoles(),
            realAccount,
        )
        val target = resolveTarget(request)?.takeIf { canImpersonate }
        val targetAccount = target?.let { accountRepository.findByUsername(it).orElse(null) }
        return ImpersonationStatusDto(
            active = targetAccount != null,
            canImpersonate = canImpersonate,
            targetUsername = targetAccount?.username,
            targetFullName = targetAccount?.fullName,
            realUsername = realAccount?.username ?: realLogin,
            realFullName = realAccount?.fullName,
        )
    }

    @Transactional(readOnly = true)
    fun start(body: ImpersonationStartRequest, request: HttpServletRequest): ImpersonationStatusDto {
        assertCanImpersonate()
        val username = body.username.trim()
        if (username.isBlank()) throw InvalidRequestException("username is required")
        val real = realUserLogin() ?: throw NotAuthorizedException()
        val realAccount = resolveAccount(real)
        if (username.equals(real, ignoreCase = true) ||
            (realAccount != null && username.equals(realAccount.username, ignoreCase = true))
        ) {
            throw InvalidRequestException("Cannot impersonate yourself")
        }
        val target = accountRepository.findByUsername(username).orElseThrow {
            InvalidRequestException("Account not found: $username")
        }
        if (target.groups.contains(UserGroup.ADMIN_SSO)) {
            throw InvalidRequestException("Cannot impersonate ADMIN_SSO")
        }
        request.getSession(true).setAttribute(ImpersonationKeys.SESSION_USERNAME, target.username)
        log.info("IMPERSONATION_START actor={} target={}", realAccount?.username ?: real, target.username)
        return status(request)
    }

    fun stop(request: HttpServletRequest): ImpersonationStatusDto {
        val real = realUserLogin()
        val previous = request.getSession(false)?.getAttribute(ImpersonationKeys.SESSION_USERNAME) as? String
        request.getSession(false)?.removeAttribute(ImpersonationKeys.SESSION_USERNAME)
        if (previous != null) {
            log.info("IMPERSONATION_STOP actor={} target={}", real, previous)
        }
        return status(request)
    }

    fun assertCanImpersonate() {
        val realLogin = realUserLogin()
        if (!PrivilegedOps.isAllowed(
                realLogin,
                rs.russian.portal.shared.security.currentUserRoles(),
                resolveAccount(realLogin),
            )
        ) {
            throw NotAuthorizedException()
        }
    }

    private fun resolveAccount(login: String?) =
        login?.trim()?.takeIf { it.isNotEmpty() }?.let { key ->
            accountRepository.findByUsername(key).orElse(null)
                ?: accountRepository.findByEmail(key).orElse(null)
        }

    companion object {
        private val log = LoggerFactory.getLogger(ImpersonationService::class.java)

        fun resolveTarget(request: HttpServletRequest?): String? {
            if (request == null) return null
            val header = request.getHeader(ImpersonationKeys.HEADER_USERNAME)?.trim()?.takeIf { it.isNotEmpty() }
            if (header != null) return header
            val session = request.getSession(false) ?: return null
            return (session.getAttribute(ImpersonationKeys.SESSION_USERNAME) as? String)?.trim()?.takeIf { it.isNotEmpty() }
        }
    }
}
