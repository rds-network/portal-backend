package rs.russian.portal.user.service

import io.authentik.model.User
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityNotFoundException
import java.util.*
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.generated.model.*
import rs.russian.portal.file.service.FileService
import rs.russian.portal.program.repository.ProgramRepository
import rs.russian.portal.program.repository.ProjectRepository
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.jpa.convert
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.ResidencePermit
import rs.russian.portal.user.domain.UserInfo
import rs.russian.portal.user.domain.enums.DepersonalizationStatus
import rs.russian.portal.user.domain.specification.searchSpecification
import rs.russian.portal.user.mapper.ContractMapper
import rs.russian.portal.user.mapper.ResidencePermitMapper
import rs.russian.portal.user.mapper.UserMapper
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.authentik.AuthentikService

@Service
class AccountService(
    private val userMapper: UserMapper,
    private val programRepository: ProgramRepository,
    private val residencePermitMapper: ResidencePermitMapper,
    private val projectRepository: ProjectRepository,
    private val fileService: FileService,
    private val contractMapper: ContractMapper,
    private val accountRepository: AccountRepository,
    private val authentikUserService: AuthentikService,
    private val entityManager: EntityManager,
    private val sessionService: SessionService,
) {

    @Transactional(readOnly = true)
    fun getAccount(id: Int): Account = accountRepository.findById(id).orElseThrow()

    @Transactional(readOnly = true)
    fun getAccountByLogin(login: String): Account = accountRepository.findByUsername(login)
        .orElseThrow { EntityNotFoundException("Account with login $login not found") }

    @Transactional(readOnly = true)
    fun findAccountByLogin(login: String?): Account? {
        if (login.isNullOrBlank()) return null
        return accountRepository.findByUsername(login).orElse(null)
    }

    @Transactional(readOnly = true)
    fun findAccountByEmail(email: String?): Account? {
        if (email.isNullOrBlank()) return null
        return accountRepository.findByEmail(email).orElse(null)
    }

    @Transactional(readOnly = true)
    fun getCurrentAccount(): Account = getAccountByLogin(currentUserLogin() ?: throw NotAuthorizedException())

    @Transactional
    fun save(account: Account): Account {
        val saved = accountRepository.saveAndFlush(account)
        if (!saved.active) sessionService.invalidate(saved.username)
        return saved
    }

    @Transactional
    fun create(request: UserCreateRequest): Account {
        val ssoUser = authentikUserService.createUser(request.username, request.fullName, request.email)
        var account = userMapper.map(ssoUser)
        account.info = UserInfo.default(account)
        account.contracts = mutableSetOf(userMapper.map(request.contract, account))
        account = accountRepository.saveAndFlush(account)
        return account
    }

    @Transactional
    fun create(email: String, fullName: String): Account {
        val username = email.split("@")[0].lowercase()
        val ssoUser = authentikUserService.createUser(username, fullName, email)
        var account = userMapper.map(ssoUser)
        account.info = UserInfo.default(account)
        account = accountRepository.saveAndFlush(account)
        return account
    }

    @Transactional
    fun createOrUpdateAccount(oidcUser: OidcUser) {
        val email = oidcUser.userInfo.email
        val ssoUser = email?.takeIf { it.isNotBlank() }?.let(authentikUserService::getUser)
        if (ssoUser?.isActive != true || ssoUser.username != oidcUser.userInfo.nickName) {
            throw OAuth2AuthenticationException(OAuth2Error("access_denied"))
        }
        val id = ssoUser.pk
        accountRepository.findById(id).ifPresentOrElse({
            if (!it.active || isDepersonalized(it)) {
                throw OAuth2AuthenticationException(OAuth2Error("access_denied"))
            }
            userMapper.update(oidcUser.userInfo, it)
            it.info = it.info ?: UserInfo.default(it)
            accountRepository.saveAndFlush(it)
        }, {
            val account = userMapper.map(oidcUser.userInfo)
            account.id = id
            account.info = UserInfo.default(account)
            accountRepository.saveAndFlush(account)
        })
    }

    @Transactional
    fun createOrUpdateAccount(ssoUser: User): Account {
        accountRepository.findById(ssoUser.pk).ifPresentOrElse({
            if (isDepersonalized(it)) return@ifPresentOrElse
            userMapper.update(ssoUser, it)
            it.info = it.info ?: UserInfo.default(it)
            accountRepository.saveAndFlush(it)
        }, {
            val account = userMapper.map(ssoUser)
            account.info = UserInfo.default(account)
            accountRepository.saveAndFlush(account)
        })
        return getAccount(ssoUser.pk)
    }

    /**
     * Depersonalization scrubs the local account but not the Authentik record. If that record is later
     * re-enabled, an SSO sync or OIDC login would otherwise re-run [UserMapper.update] and overwrite the
     * sentinels with the real email/name/groups still held in Authentik — resurrecting the erased identity
     * in place. A DEPERSONALIZED account is terminal, so we never let SSO write back over it.
     */
    private fun isDepersonalized(account: Account): Boolean {
        if (account.depersonalizationStatus == DepersonalizationStatus.DEPERSONALIZED) {
            log.warn("Skipping SSO write-back for depersonalized account {}", account.username)
            return true
        }
        return false
    }

    @Transactional(readOnly = true)
    fun search(query: String, pageRequest: PageRequest, filter: UserSearchFilter?): Page<Account> {
        val specification = searchSpecification(query, filter)
        return findAllFull(specification, convert(pageRequest))
    }

    @Transactional(readOnly = true)
    fun resolve(usernames: List<String>): List<Account> {
        return accountRepository.findAllByUsernameIn(usernames)
    }

    @Transactional
    fun setAvatar(id: Int, fileId: String): Account {
        val account = getAccount(id)
        val file = fileService.getFile(fileId)
        account.info?.avatar = file
        return account
    }

    @Transactional
    fun switchActiveState(id: Int, isActive: Boolean): Account {
        val account = getAccount(id)
        if (account.active == isActive) {
            if (!isActive) sessionService.invalidate(account.username)
            return account
        }
        account.active = isActive
        if (isActive) {
            resetPendingDepersonalization(account)
        }
        authentikUserService.switchActiveState(account, isActive)
        if (!isActive) sessionService.invalidate(account.username)
        return account
    }

    @Transactional
    fun updateContracts(id: Int, contractList: Set<ContractDto>): Account {
        val account = getAccount(id)
        account.contracts.clear()
        account.contracts.addAll(contractList.map { contractMapper.map(it, account) })
        resetPendingDepersonalization(account)
        return account
    }

    /**
     * A returning volunteer (reactivated or given a new contract) is no longer pending depersonalization, so
     * the retention clock restarts from NONE and the warning is re-sent when it next comes due. A WARNED
     * account is always inactive, so this never fights the scheduler; a DEPERSONALIZED account is terminal
     * and is left untouched.
     */
    private fun resetPendingDepersonalization(account: Account) {
        if (account.depersonalizationStatus == DepersonalizationStatus.WARNED) {
            account.depersonalizationStatus = DepersonalizationStatus.NONE
        }
    }

    @Transactional
    fun updateResidencePermits(id: Int, permits: List<ResidencePermitDto>): Account {
        val account = getAccount(id)

        // Validate dates
        permits.forEach { permit ->
            if (permit.validUntil <= permit.issuingDate) {
                throw InvalidRequestException("Valid until date must be after issuing date")
            }
        }

        val existingPermitsMap: Map<String, ResidencePermit> = account.residencePermits
            .filter { it.id != null }
            .associateBy { it.id.toString() }
        val updatedPermits = permits.map { dto ->
            val existingEntity = existingPermitsMap[dto.id.toString()]
            if (existingEntity != null) {
                residencePermitMapper.update(dto, existingEntity)
                existingEntity
            } else {
                residencePermitMapper.toEntity(dto, account)
            }
        }.toMutableSet()
        account.residencePermits.clear()
        account.residencePermits.addAll(updatedPermits)
        return accountRepository.save(account)
    }

    @Transactional
    fun updateInfo(accountId: Int, newInfo: UserInfo) {
        val account = getAccount(accountId)
        newInfo.id = account.username
        newInfo.account = account
        newInfo.version = account.info?.version
        account.info = newInfo
        accountRepository.save(account)
    }

    @Transactional
    fun partialUpdateInfo(id: Int, userInfoUpdateRequest: UserInfoUpdateRequest): Account {
        val account = getAccount(id)
        val userInfo = account.info ?: UserInfo.default(account)
        userMapper.updateInfo(userInfo, userInfoUpdateRequest)
        account.info = userInfo
        return account
    }

    @Transactional
    fun setProgram(id: Int, code: String): Account {
        val account = getAccount(id)
        val userInfo = account.info ?: UserInfo.default(account)

        val program = programRepository.findByCode(code)
            ?: throw IllegalArgumentException("Program with code: $code not found!")

        userInfo.program = program

        userInfo.project?.let { project ->
            if (project.program.code != program.code) {
                userInfo.project = null
            }
        }

        account.info = userInfo
        return account
    }

    @Transactional
    fun setProject(id: Int, code: String): Account {
        val account = getAccount(id)
        val userInfo = account.info ?: UserInfo.default(account)

        val project = projectRepository.findByCode(code)
            ?: throw IllegalArgumentException("Project with code: $code not found!")

        userInfo.project = project
        userInfo.program = project.program
        account.info = userInfo
        return account
    }

    @Transactional
    fun deleteResidencePermit(accountId: Int, permitId: UUID): Account {
        val account = getAccount(accountId)
        account.residencePermits.removeIf { it.id == permitId }
        return accountRepository.save(account)
    }

    /**
     * Получить список пользователей с использованием EntityGraph
     * Решает проблему, при которой невозможно одновременная работа Pageable и EntityGraph:
     * HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
     * entityManager.detach(...) - необходим потому что findAll загружает "легковесные" объекты и
     * сохраняет их в контекст, а findAllByIdIn уже загрузит полные объекты, однако если не очистить
     * контекст, то они не будут перезаписаны в контексте, что повлечет дополнительные SQL запросы
     */
    private fun findAllFull(specification: Specification<Account>, pageable: Pageable): Page<Account> {
        val accounts = accountRepository.findAll(specification, pageable)
        accounts.forEach { account -> entityManager.detach(account) }
        val accountsFull = accountRepository.findAllByIdIn(accounts.mapNotNull { it.id }, pageable.sort)
        return PageImpl(accountsFull, accounts.pageable, accounts.totalElements)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AccountService::class.java)
    }
}
