package rs.russian.portal.program.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.program.api.PortalModeratorDto
import rs.russian.portal.program.api.ProgramCuratorDelegateDto
import rs.russian.portal.program.api.ProgramCuratorDelegateWriteRequest
import rs.russian.portal.program.api.ProgramCuratorDto
import rs.russian.portal.program.api.ProgramCuratorWriteRequest
import rs.russian.portal.program.api.ReportApproverDto
import rs.russian.portal.program.domain.ProgramCurator
import rs.russian.portal.program.domain.ProgramCuratorDelegate
import rs.russian.portal.program.repository.ProgramCuratorDelegateRepository
import rs.russian.portal.program.repository.ProgramCuratorRepository
import rs.russian.portal.program.repository.ProgramRepository
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.AccountService

@Service
class ProgramCuratorService(
    private val curatorRepository: ProgramCuratorRepository,
    private val delegateRepository: ProgramCuratorDelegateRepository,
    private val programRepository: ProgramRepository,
    private val accountRepository: AccountRepository,
    private val accountService: AccountService,
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
    fun listDelegates(): List<ProgramCuratorDelegateDto> {
        val rows = delegateRepository.findAllByOrderByProgramCodeAscCuratorUsernameAscDelegateUsernameAsc()
        val programs = programRepository.findAll().associateBy { it.code }
        val logins = (rows.map { it.curatorUsername } + rows.map { it.delegateUsername }).distinct()
        val accounts = accountRepository.findAllByUsernameIn(logins).associateBy { it.username.lowercase() }
        return rows.mapNotNull { row ->
            val program = programs[row.programCode] ?: return@mapNotNull null
            val curator = accounts[row.curatorUsername.lowercase()] ?: return@mapNotNull null
            val delegate = accounts[row.delegateUsername.lowercase()] ?: return@mapNotNull null
            ProgramCuratorDelegateDto(
                programCode = program.code,
                programNameRu = program.nameRu,
                programNameEn = program.nameEn,
                programNameSr = program.nameSr,
                curatorUsername = curator.username,
                curatorFullName = curator.fullName,
                delegateUsername = delegate.username,
                delegateFullName = delegate.fullName,
            )
        }
    }

    @Transactional(readOnly = true)
    fun listModerators(): List<PortalModeratorDto> =
        accountRepository.findAllActiveByGroup(UserGroup.ADMIN_VOLUNTEER.name)
            .sortedBy { it.fullName.lowercase() }
            .map { PortalModeratorDto(username = it.username, fullName = it.fullName) }

    @Transactional(readOnly = true)
    fun listApprovers(): List<ReportApproverDto> {
        val programs = programRepository.findAll().associateBy { it.code }
        val curators = curatorRepository.findAllByOrderByProgramCodeAscUsernameAsc()
        val delegates = delegateRepository.findAllByOrderByProgramCodeAscCuratorUsernameAscDelegateUsernameAsc()
        val logins = (
            curators.map { it.username } +
                delegates.map { it.curatorUsername } +
                delegates.map { it.delegateUsername }
            ).distinct()
        val accounts = accountRepository.findAllByUsernameIn(logins).associateBy { it.username.lowercase() }
        val result = mutableListOf<ReportApproverDto>()
        curators.forEach { row ->
            val program = programs[row.programCode] ?: return@forEach
            val account = accounts[row.username.lowercase()] ?: return@forEach
            result += ReportApproverDto(
                username = account.username,
                fullName = account.fullName,
                programCode = program.code,
                programNameRu = program.nameRu,
                programNameEn = program.nameEn,
                programNameSr = program.nameSr,
                role = "CURATOR",
            )
        }
        delegates.forEach { row ->
            val program = programs[row.programCode] ?: return@forEach
            val curator = accounts[row.curatorUsername.lowercase()] ?: return@forEach
            val delegate = accounts[row.delegateUsername.lowercase()] ?: return@forEach
            result += ReportApproverDto(
                username = delegate.username,
                fullName = delegate.fullName,
                programCode = program.code,
                programNameRu = program.nameRu,
                programNameEn = program.nameEn,
                programNameSr = program.nameSr,
                role = "DELEGATE",
                curatorUsername = curator.username,
                curatorFullName = curator.fullName,
            )
        }
        return result.sortedWith(compareBy({ it.programCode }, { it.role }, { it.fullName.lowercase() }))
    }

    @Transactional(readOnly = true)
    fun isCurator(username: String): Boolean = curatorRepository.existsByUsernameIgnoreCase(username)

    @Transactional(readOnly = true)
    fun isCurrentCurator(): Boolean {
        val login = currentUserLogin() ?: return false
        return isCurator(login)
    }

    @Transactional(readOnly = true)
    fun isAllowedCustomer(username: String): Boolean {
        if (curatorRepository.existsByUsernameIgnoreCase(username)) return true
        return delegateRepository.findAllByDelegateUsernameIgnoreCase(username).isNotEmpty()
    }

    @Transactional(readOnly = true)
    fun canAcceptAsDelegate(login: String, customerUsername: String, programCode: String?): Boolean {
        val rows = delegateRepository.findAllByDelegateUsernameIgnoreCase(login)
        if (rows.isEmpty()) return false
        return rows.any { row ->
            row.curatorUsername.equals(customerUsername, ignoreCase = true) &&
                (programCode.isNullOrBlank() || row.programCode.equals(programCode, ignoreCase = true))
        }
    }

    @Transactional(readOnly = true)
    fun curatorUsernamesDelegatedTo(login: String): List<String> =
        delegateRepository.findAllByDelegateUsernameIgnoreCase(login)
            .map { it.curatorUsername }
            .distinct()

    @Transactional(readOnly = true)
    fun delegateUsernamesOf(curatorUsername: String, programCode: String?): List<String> {
        val rows = if (programCode.isNullOrBlank()) {
            delegateRepository.findAllByOrderByProgramCodeAscCuratorUsernameAscDelegateUsernameAsc()
                .filter { it.curatorUsername.equals(curatorUsername, ignoreCase = true) }
        } else {
            delegateRepository.findAllByCuratorUsernameIgnoreCaseAndProgramCode(curatorUsername, programCode)
        }
        return rows.map { it.delegateUsername }.distinct()
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
        assertManager()
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
        assertManager()
        val code = programCode.trim()
        val login = username.trim()
        curatorRepository.deleteByProgramCodeAndUsernameIgnoreCase(code, login)
        delegateRepository.deleteByProgramCodeAndCuratorUsernameIgnoreCase(code, login)
    }

    @Transactional
    fun assignDelegate(request: ProgramCuratorDelegateWriteRequest): ProgramCuratorDelegateDto {
        val programCode = request.programCode.trim().uppercase()
        val curatorLogin = request.curatorUsername.trim()
        val delegateLogin = request.delegateUsername.trim()
        if (curatorLogin.equals(delegateLogin, ignoreCase = true)) {
            throw InvalidRequestException("Нельзя делегировать приёмку самому себе")
        }
        val program = programRepository.findByCode(programCode)
            ?: throw InvalidRequestException("Unknown program $programCode")
        if (!curatorRepository.existsByProgramCodeAndUsernameIgnoreCase(program.code, curatorLogin)) {
            throw InvalidRequestException("Куратор не назначен на программу")
        }
        assertCanManageDelegates(program.code, curatorLogin)
        val curator = accountRepository.findByUsername(curatorLogin).orElse(null)
            ?: throw InvalidRequestException("Unknown curator $curatorLogin")
        val delegate = accountRepository.findByUsername(delegateLogin).orElse(null)
            ?: throw InvalidRequestException("Unknown user $delegateLogin")
        if (!delegateRepository.existsByProgramCodeAndCuratorUsernameIgnoreCaseAndDelegateUsernameIgnoreCase(
                program.code,
                curator.username,
                delegate.username,
            )
        ) {
            delegateRepository.save(
                ProgramCuratorDelegate(
                    programCode = program.code,
                    curatorUsername = curator.username,
                    delegateUsername = delegate.username,
                    createdBy = currentUserLogin(),
                )
            )
        }
        return ProgramCuratorDelegateDto(
            programCode = program.code,
            programNameRu = program.nameRu,
            programNameEn = program.nameEn,
            programNameSr = program.nameSr,
            curatorUsername = curator.username,
            curatorFullName = curator.fullName,
            delegateUsername = delegate.username,
            delegateFullName = delegate.fullName,
        )
    }

    @Transactional
    fun removeDelegate(programCode: String, curatorUsername: String, delegateUsername: String) {
        val code = programCode.trim()
        val curator = curatorUsername.trim()
        assertCanManageDelegates(code, curator)
        delegateRepository.deleteByProgramCodeAndCuratorUsernameIgnoreCaseAndDelegateUsernameIgnoreCase(
            code,
            curator,
            delegateUsername.trim(),
        )
    }

    private fun assertManager() {
        val account = accountService.getCurrentAccount()
        val managers = setOf(
            UserGroup.ADMIN,
            UserGroup.ADMIN_VOLUNTEER,
            UserGroup.ADMIN_SSO,
            UserGroup.MAIN_VOLUNTEER,
        )
        if (account.groups.none { it in managers }) {
            throw NotAuthorizedException()
        }
    }

    private fun assertCanManageDelegates(programCode: String, curatorUsername: String) {
        val account = accountService.getCurrentAccount()
        val managers = setOf(
            UserGroup.ADMIN,
            UserGroup.ADMIN_VOLUNTEER,
            UserGroup.ADMIN_SSO,
            UserGroup.MAIN_VOLUNTEER,
        )
        if (account.groups.any { it in managers }) return
        if (account.username.equals(curatorUsername, ignoreCase = true) &&
            curatorRepository.existsByProgramCodeAndUsernameIgnoreCase(programCode, account.username)
        ) {
            return
        }
        throw NotAuthorizedException()
    }
}
