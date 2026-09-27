package rs.russian.portal.user.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import rs.russian.generated.api.UserApi
import rs.russian.generated.model.*
import rs.russian.portal.accountstatus.service.AccountStatusService
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.jpa.convert
import rs.russian.portal.shared.security.Authorized
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_SSO
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import rs.russian.portal.user.mapper.UserMapper
import rs.russian.portal.user.service.AccountService
import rs.russian.portal.user.service.DissolutionQueueService
import rs.russian.portal.user.service.ReportBlockService
import rs.russian.portal.user.service.ReportControllerService
import rs.russian.portal.user.service.SessionService
import java.util.*
import org.springframework.http.HttpStatus

@RestController
class UserController(
    private val accountService: AccountService,
    private val accountStatusService: AccountStatusService,
    private val reportBlockService: ReportBlockService,
    private val reportControllerService: ReportControllerService,
    private val dissolutionQueueService: DissolutionQueueService,
    private val sessionService: SessionService,
    private val userMapper: UserMapper,
) : UserApi {

    override fun getCurrentAccount(): ResponseEntity<UserInfoDto> {
        val account = accountService.getCurrentAccount()
        return ResponseEntity.ok(userMapper.map(account.info))
    }

    override fun getInfo(login: String): ResponseEntity<UserInfoDto> {
        val account = accountService.getAccountByLogin(login)
        val dto = userMapper.map(account.info)
        val currentAccount = accountService.getCurrentAccount()
        if (currentAccount.id != account.id && !currentAccount.groups.contains(ADMIN_VOLUNTEER)) {
            // Filter residence permits: allowed only for self or ADMIN_VOLUNTEER
            dto.residencePermits = mutableListOf()
        }
        return ResponseEntity.ok(dto)
    }

    override fun logout(all: Boolean): ResponseEntity<Unit> {
        sessionService.invalidate(all)
        return ResponseEntity.ok(null)
    }

    override fun resolveUsers(requestBody: List<String>): ResponseEntity<List<UserInfoDto>> {
        val accounts = accountService.resolve(requestBody)
        return ResponseEntity.ok(accounts.map { userMapper.map(it.info) })
    }

    override fun searchUsers(
        searchQuery: String,
        pageRequest: PageRequest,
        userSearchFilter: UserSearchFilter?,
    ): ResponseEntity<UserPageResponse> {
        val page = accountService.search(searchQuery, pageRequest, userSearchFilter)
        return ResponseEntity.ok(
            UserPageResponse(
                page = convert(page),
                content = page.map { userMapper.map(it.info) }.toMutableList()
            )
        )
    }

    override fun setAvatar(avatarId: String): ResponseEntity<UserInfoDto> {
        val currentUser = accountService.getCurrentAccount()
        return ResponseEntity.ok(userMapper.map(accountService.setAvatar(currentUser.id!!, avatarId).info))
    }

    override fun setProgram(id: Int, code: String): ResponseEntity<UserInfoDto> {
        return ResponseEntity.ok(userMapper.map(accountService.setProgram(id, code).info))
    }

    override fun clearProgram(id: Int): ResponseEntity<UserInfoDto> {
        return ResponseEntity.ok(userMapper.map(accountService.clearProgram(id).info))
    }

    override fun setProject(id: Int, code: String): ResponseEntity<UserInfoDto> {
        return ResponseEntity.ok(userMapper.map(accountService.setProject(id, code).info))
    }

    override fun clearProject(id: Int): ResponseEntity<UserInfoDto> {
        return ResponseEntity.ok(userMapper.map(accountService.clearProject(id).info))
    }

    override fun setReportBlock(id: Int, reportBlockRequest: ReportBlockRequest?): ResponseEntity<UserInfoDto> {
        return ResponseEntity.ok(userMapper.map(reportBlockService.block(id, reportBlockRequest?.reason).info))
    }

    override fun clearReportBlock(id: Int): ResponseEntity<UserInfoDto> {
        return ResponseEntity.ok(userMapper.map(reportBlockService.unblock(id).info))
    }

    override fun setReportController(
        id: Int,
        reportControllerRequest: ReportControllerRequest,
    ): ResponseEntity<UserInfoDto> {
        val account = reportControllerService.setController(
            id,
            reportControllerRequest.username,
            reportControllerRequest.reason,
        )
        return ResponseEntity.ok(userMapper.map(account.info))
    }

    override fun clearReportController(id: Int): ResponseEntity<UserInfoDto> {
        return ResponseEntity.ok(userMapper.map(reportControllerService.clearController(id).info))
    }

    override fun enqueueDissolution(
        id: Int,
        dissolutionQueueRequest: DissolutionQueueRequest?,
    ): ResponseEntity<UserInfoDto> {
        return ResponseEntity.ok(
            userMapper.map(dissolutionQueueService.enqueue(id, dissolutionQueueRequest?.reason).info)
        )
    }

    override fun dequeueDissolution(id: Int): ResponseEntity<UserInfoDto> {
        return ResponseEntity.ok(userMapper.map(dissolutionQueueService.dequeue(id).info))
    }

    @Authorized(allowed = [ADMIN_SSO, ADMIN_VOLUNTEER])
    override fun createUser(userCreateRequest: UserCreateRequest): ResponseEntity<UserInfoDto> {
        val account = accountService.create(userCreateRequest)
        return ResponseEntity.ok(userMapper.map(account.info))
    }

    @Authorized(allowed = [ADMIN_SSO, ADMIN_VOLUNTEER])
    override fun activateAccount(id: Int): ResponseEntity<UserInfoDto> {
        val result = accountStatusService.requestOrApply(id, true)
        val account = accountService.getAccount(id)
        val status = if (result.pending) HttpStatus.ACCEPTED else HttpStatus.OK
        return ResponseEntity.status(status).body(userMapper.map(account.info))
    }

    @Authorized(allowed = [ADMIN_SSO, ADMIN_VOLUNTEER])
    override fun deactivateAccount(id: Int): ResponseEntity<UserInfoDto> {
        val result = accountStatusService.requestOrApply(id, false)
        val account = accountService.getAccount(id)
        val status = if (result.pending) HttpStatus.ACCEPTED else HttpStatus.OK
        return ResponseEntity.status(status).body(userMapper.map(account.info))
    }

    @Authorized(allowed = [ADMIN_VOLUNTEER])
    override fun updateContracts(id: Int, contractDto: List<ContractDto>): ResponseEntity<UserInfoDto> {
        return ResponseEntity.ok(userMapper.map(accountService.updateContracts(id, HashSet(contractDto)).info))
    }

    override fun updateInfo(login: String, userInfoUpdateRequest: UserInfoUpdateRequest): ResponseEntity<UserInfoDto> {
        val targetAccount = accountService.getAccountByLogin(login)
        val currentAccount = accountService.getCurrentAccount()

        if (!(currentAccount.groups.contains(ADMIN_SSO) || currentAccount.groups.contains(ADMIN_VOLUNTEER))) {
            if (currentAccount.username != login) {
                throw NotAuthorizedException()
            }
        }

        return ResponseEntity.ok(
            userMapper.map(
                accountService.partialUpdateInfo(
                    targetAccount.id!!,
                    userInfoUpdateRequest
                ).info
            )
        )
    }

    override fun updateResidencePermits(
        id: Int,
        residencePermitDto: List<ResidencePermitDto>,
    ): ResponseEntity<UserInfoDto> {
        val currentAccount = accountService.getCurrentAccount()
        if (currentAccount.id != id && !currentAccount.groups.contains(ADMIN_VOLUNTEER)) {
            throw NotAuthorizedException()
        }
        val account = accountService.updateResidencePermits(id, residencePermitDto)
        return ResponseEntity.ok(userMapper.map(account.info))
    }

    @Authorized(allowed = [ADMIN_VOLUNTEER])
    override fun deleteResidencePermit(id: Int, permitId: UUID): ResponseEntity<UserInfoDto> {
        val account = accountService.deleteResidencePermit(id, permitId)
        return ResponseEntity.ok(userMapper.map(account.info))
    }
}
