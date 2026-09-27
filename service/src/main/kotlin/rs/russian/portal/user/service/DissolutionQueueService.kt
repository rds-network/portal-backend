package rs.russian.portal.user.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup
import java.time.OffsetDateTime

@Service
class DissolutionQueueService(
    private val accountService: AccountService,
) {

    @Transactional
    fun enqueue(id: Int, reason: String?): Account {
        assertCanManage()
        val target = accountService.getAccount(id)
        val current = accountService.getCurrentAccount()
        return enqueueAccount(target, current.username, reason)
    }

    /**
     * Puts [account] on the dissolution queue without manager-role checks.
     * Callers must already have authorized the action (e.g. curator accept).
     */
    fun enqueueAccount(account: Account, byUsername: String, reason: String?): Account {
        account.dissolutionQueuedAt = OffsetDateTime.now()
        account.dissolutionQueuedBy = byUsername
        account.dissolutionQueueReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        return account
    }

    @Transactional
    fun dequeue(id: Int): Account {
        val target = accountService.getAccount(id)
        assertCanManage()
        target.dissolutionQueuedAt = null
        target.dissolutionQueuedBy = null
        target.dissolutionQueueReason = null
        return target
    }

    private fun assertCanManage() {
        val current = accountService.getCurrentAccount()
        if (current.groups.none { it in MANAGERS }) {
            throw NotAuthorizedException()
        }
    }

    companion object {
        private val MANAGERS = setOf(
            UserGroup.ADMIN,
            UserGroup.ADMIN_VOLUNTEER,
            UserGroup.ADMIN_SSO,
            UserGroup.MAIN_VOLUNTEER,
        )
    }
}
