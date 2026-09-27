package rs.russian.portal.user.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.program.repository.ProgramRepository
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.user.domain.UserSecondaryProgram
import rs.russian.portal.user.repository.UserSecondaryProgramRepository

/**
 * Дополнительные программы волонтёра. Основная остаётся в [rs.russian.portal.user.domain.UserInfo.program]
 * и по-прежнему используется для отпусков, приёмки и кураторов.
 */
@Service
class SecondaryProgramService(
    private val accountService: AccountService,
    private val programRepository: ProgramRepository,
    private val secondaryProgramRepository: UserSecondaryProgramRepository,
) {

    @Transactional(readOnly = true)
    fun list(accountId: Int): List<String> {
        accountService.getAccount(accountId)
        return secondaryProgramRepository.findAllByAccountIdOrderByProgramCodeAsc(accountId)
            .map { it.programCode }
    }

    /**
     * Полная замена списка дополнительных программ. Код основной программы из списка отбрасывается.
     * Права как у setProgram: достаточно быть авторизованным (ограничения — на UI).
     */
    @Transactional
    fun replace(accountId: Int, programCodes: List<String>): List<String> {
        val account = accountService.getAccount(accountId)
        val primary = account.info?.program?.code?.uppercase()

        val normalized = programCodes
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.uppercase() }
            .filter { primary == null || !it.equals(primary, ignoreCase = true) }

        val resolved = normalized.map { code ->
            programRepository.findByCode(code)
                ?: throw InvalidRequestException("Program with code: $code not found!")
            code
        }

        secondaryProgramRepository.deleteAllByAccountId(accountId)
        resolved.forEach { code ->
            secondaryProgramRepository.save(
                UserSecondaryProgram(accountId = accountId, programCode = code)
            )
        }
        return list(accountId)
    }
}
