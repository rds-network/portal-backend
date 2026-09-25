package rs.russian.portal.program.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.program.api.ProgramCuratorDto
import rs.russian.portal.program.api.ProgramCuratorWriteRequest
import rs.russian.portal.program.domain.ProgramCurator
import rs.russian.portal.program.repository.ProgramCuratorRepository
import rs.russian.portal.program.repository.ProgramRepository
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.user.repository.AccountRepository

@Service
class ProgramCuratorService(
    private val curatorRepository: ProgramCuratorRepository,
    private val programRepository: ProgramRepository,
    private val accountRepository: AccountRepository,
) {

    @Transactional(readOnly = true)
    fun list(): List<ProgramCuratorDto> {
        val rows = curatorRepository.findAllByOrderByProgramCodeAscUsernameAsc()
        val programs = programRepository.findAll().associateBy { it.code }
        val accounts = accountRepository.findAllByUsernameIn(rows.map { it.username }.distinct())
            .associateBy { it.username.lowercase() }
        return rows.mapNotNull { row ->
            val program = programs[row.programCode] ?: return@mapNotNull null
            val account = accounts[row.username.lowercase()] ?: return@mapNotNull null
            ProgramCuratorDto(
                programCode = program.code,
                programNameRu = program.nameRu,
                programNameEn = program.nameEn,
                programNameSr = program.nameSr,
                username = account.username,
                fullName = account.fullName,
            )
        }
    }

    @Transactional(readOnly = true)
    fun isCurator(username: String): Boolean = curatorRepository.existsByUsernameIgnoreCase(username)

    @Transactional(readOnly = true)
    fun isCurrentCurator(): Boolean {
        val login = currentUserLogin() ?: return false
        return isCurator(login)
    }

    @Transactional(readOnly = true)
    fun programCodesOf(username: String): List<String> =
        curatorRepository.findAllByUsernameIgnoreCase(username).map { it.programCode }

    @Transactional(readOnly = true)
    fun programCodesOfCurrentUser(): List<String> {
        val login = currentUserLogin() ?: return emptyList()
        return programCodesOf(login)
    }

    @Transactional(readOnly = true)
    fun hasAny(): Boolean = curatorRepository.count() > 0

    @Transactional
    fun assign(request: ProgramCuratorWriteRequest): ProgramCuratorDto {
        val programCode = request.programCode.trim().uppercase()
        val username = request.username.trim()
        val program = programRepository.findByCode(programCode)
            ?: throw InvalidRequestException("Unknown program $programCode")
        val account = accountRepository.findByUsername(username).orElse(null)
            ?: throw InvalidRequestException("Unknown user $username")
        if (!curatorRepository.existsByProgramCodeAndUsernameIgnoreCase(program.code, account.username)) {
            curatorRepository.save(ProgramCurator(programCode = program.code, username = account.username))
        }
        return ProgramCuratorDto(
            programCode = program.code,
            programNameRu = program.nameRu,
            programNameEn = program.nameEn,
            programNameSr = program.nameSr,
            username = account.username,
            fullName = account.fullName,
        )
    }

    @Transactional
    fun remove(programCode: String, username: String) {
        curatorRepository.deleteByProgramCodeAndUsernameIgnoreCase(programCode.trim(), username.trim())
    }
}
