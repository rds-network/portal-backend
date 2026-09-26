package rs.russian.portal.user.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.program.service.ProgramCuratorService
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup
import java.time.OffsetDateTime

/**
 * Стоп на сдачу отчётов: модератор принимает отчёты, но куратор при этом может потерять волонтера из виду.
 * Стоп заставляет волонтера выйти на связь с тем, кто его поставил, прежде чем сдавать отчёты дальше.
 *
 * Живёт отдельно от [AccountService], потому что проверка прав требует [ProgramCuratorService],
 * который сам зависит от [AccountService].
 */
@Service
class ReportBlockService(
    private val accountService: AccountService,
    private val programCuratorService: ProgramCuratorService,
) {

    @Transactional
    fun block(id: Int, reason: String?): Account {
        val target = accountService.getAccount(id)
        val current = accountService.getCurrentAccount()
        assertCanManage(current, target)

        target.reportBlocked = true
        target.reportBlockedAt = OffsetDateTime.now()
        target.reportBlockedBy = current.username
        target.reportBlockedReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        return target
    }

    @Transactional
    fun unblock(id: Int): Account {
        val target = accountService.getAccount(id)
        val current = accountService.getCurrentAccount()
        if (!target.reportBlockedBy.equals(current.username, ignoreCase = true)) {
            assertCanManage(current, target)
        }

        target.reportBlocked = false
        target.reportBlockedAt = null
        target.reportBlockedBy = null
        target.reportBlockedReason = null
        return target
    }

    private fun assertCanManage(current: Account, target: Account) {
        if (current.groups.any { it in MANAGERS }) return
        val programCode = target.info?.program?.code
        if (!programCode.isNullOrBlank() &&
            programCuratorService.programCodesOf(current.username).any { it.equals(programCode, ignoreCase = true) }
        ) {
            return
        }
        throw NotAuthorizedException()
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
