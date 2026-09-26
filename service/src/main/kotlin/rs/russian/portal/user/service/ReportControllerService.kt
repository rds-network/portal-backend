package rs.russian.portal.user.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.program.service.ProgramCuratorService
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup

/**
 * Принудительный контроль сдачи отчётов: волонтера ведёт человек из другой программы, поэтому обычной
 * приёмки по куратору программы недостаточно. Пока контроль стоит, волонтер обязан ставить контролёра
 * заказчиком во всех задачах, а принять отчёт может только сам контролёр или его делегат по приёмке.
 *
 * Живёт отдельно от [AccountService] по той же причине, что и [ReportBlockService]: проверка прав
 * требует [ProgramCuratorService], который сам зависит от [AccountService].
 */
@Service
class ReportControllerService(
    private val accountService: AccountService,
    private val programCuratorService: ProgramCuratorService,
) {

    @Transactional
    fun setController(id: Int, username: String): Account {
        val target = accountService.getAccount(id)
        val current = accountService.getCurrentAccount()
        assertCanManage(current, target)

        val controller = accountService.findAccountByLogin(username.trim())
            ?: throw InvalidRequestException("Пользователь '${username.trim()}' не найден")
        if (!controller.active) {
            throw InvalidRequestException("Нельзя назначить контроль на отключённого пользователя")
        }
        if (controller.username.equals(target.username, ignoreCase = true)) {
            throw InvalidRequestException("Нельзя назначить волонтера контролёром самому себе")
        }

        target.reportControllerUsername = controller.username
        return target
    }

    @Transactional
    fun clearController(id: Int): Account {
        val target = accountService.getAccount(id)
        val current = accountService.getCurrentAccount()
        if (!target.reportControllerUsername.equals(current.username, ignoreCase = true)) {
            assertCanManage(current, target)
        }

        target.reportControllerUsername = null
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
