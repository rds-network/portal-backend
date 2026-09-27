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
        val target = accountService.getAccount(id)
        assertCanManage()
        val current = accountService.getCurrentAccount()
        target.dissolutionQueuedAt = OffsetDateTime.now()
        target.dissolutionQueuedBy = current.username
        target.dissolutionQueueReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        return target
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
