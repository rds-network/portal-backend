package rs.russian.portal.accountstatus.service

import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.accountstatus.api.AccountStatusChangeResultDto
import rs.russian.portal.accountstatus.api.AccountStatusCreateRequest
import rs.russian.portal.accountstatus.api.AccountStatusDecisionRequest
import rs.russian.portal.accountstatus.api.AccountStatusEventDto
import rs.russian.portal.accountstatus.api.AccountStatusMetaDto
import rs.russian.portal.accountstatus.api.AccountStatusRequestDto
import rs.russian.portal.accountstatus.domain.AccountStatusEvent
import rs.russian.portal.accountstatus.domain.AccountStatusRequest
import rs.russian.portal.accountstatus.domain.enums.AccountStatusEventSource
import rs.russian.portal.accountstatus.domain.enums.AccountStatusRequestStatus
import rs.russian.portal.accountstatus.repository.AccountStatusEventRepository
import rs.russian.portal.accountstatus.repository.AccountStatusRequestRepository
import rs.russian.portal.config.AppProperties
import rs.russian.portal.inbox.service.InboxService
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.AccountService
import java.time.OffsetDateTime
import java.util.UUID

@Service
class AccountStatusService(
    private val appProperties: AppProperties,
    private val accountService: AccountService,
    private val accountRepository: AccountRepository,
    private val requestRepository: AccountStatusRequestRepository,
    private val eventRepository: AccountStatusEventRepository,
    private val inboxService: InboxService,
) {

    @Transactional(readOnly = true)
    fun meta(): AccountStatusMetaDto {
        val login = currentUserLogin() ?: throw NotAuthorizedException()
        return AccountStatusMetaDto(
            approverUsername = approverUsername(),
            isAccountStatusApprover = isApprover(login),
        )
    }

    fun isApprover(username: String?): Boolean {
        if (username.isNullOrBlank()) return false
        return username.equals(approverUsername(), ignoreCase = true)
    }

    fun approverUsername(): String = appProperties.accountStatus.approverUsername.trim()

    @Transactional(readOnly = true)
    fun resolveApprover(): Account? {
        val username = approverUsername()
        if (username.isBlank()) {
            log.warn("Account status approver username is blank")
            return null
        }
        val account = accountRepository.findByUsername(username).orElse(null)
        if (account == null) {
            log.warn("Account status approver '{}' not found", username)
        }
        return account
    }

    /**
     * Invoked from activate/deactivate endpoints.
     * Approver flips immediately; everyone else creates a PENDING request.
     */
    @Transactional
    fun requestOrApply(accountId: Int, requestedActive: Boolean, reason: String? = null): AccountStatusChangeResultDto {
        assertCanManageStatus()
        val actor = accountService.getCurrentAccount()
        return if (isApprover(actor.username)) {
            val account = applyImmediate(
                accountId = accountId,
                activeTo = requestedActive,
                source = AccountStatusEventSource.DIRECT,
                actorUsername = actor.username,
                reason = reason?.trim()?.takeIf { it.isNotEmpty() },
                request = null,
                notifyApprover = false,
            )
            AccountStatusChangeResultDto(
                pending = false,
                accountId = account.id!!,
                username = account.username,
                active = account.active,
            )
        } else {
            val request = createPending(accountId, requestedActive, reason, actor.username)
            AccountStatusChangeResultDto(
                pending = true,
                request = toDto(request),
                accountId = request.targetAccount.id!!,
                username = request.targetAccount.username,
                active = request.targetAccount.active,
            )
        }
    }

    @Transactional
    fun create(request: AccountStatusCreateRequest): AccountStatusChangeResultDto =
        requestOrApply(request.accountId, request.requestedActive, request.reason)

    @Transactional(readOnly = true)
    fun pending(): List<AccountStatusRequestDto> {
        assertCanView()
        return requestRepository.findAllByStatusOrderByCreatedAtAsc(AccountStatusRequestStatus.PENDING)
            .map(::toDto)
    }

    @Transactional(readOnly = true)
    fun events(): List<AccountStatusEventDto> {
        assertCanView()
        return eventRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, EVENTS_LIMIT))
            .map(::toEventDto)
    }

    @Transactional
    fun approve(id: UUID, decision: AccountStatusDecisionRequest?): AccountStatusRequestDto {
        assertIsApprover()
        val request = requestRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Account status request $id not found") }
        if (request.status != AccountStatusRequestStatus.PENDING) {
            throw InvalidRequestException("Account status request is not pending")
        }
        val actor = currentUserLogin() ?: throw NotAuthorizedException()
        val decisionReason = decision?.reason?.trim()?.takeIf { it.isNotEmpty() }
        applyImmediate(
            accountId = request.targetAccount.id!!,
            activeTo = request.requestedActive,
            source = AccountStatusEventSource.REQUEST,
            actorUsername = actor,
            reason = decisionReason ?: request.reason,
            request = request,
            notifyApprover = false,
        )
        request.status = AccountStatusRequestStatus.APPROVED
        request.decidedBy = actor
        request.decidedAt = OffsetDateTime.now()
        request.decisionReason = decisionReason
        notifyRequesterDecision(request, approved = true)
        return toDto(request)
    }

    @Transactional
    fun reject(id: UUID, decision: AccountStatusDecisionRequest?): AccountStatusRequestDto {
        assertIsApprover()
        val request = requestRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Account status request $id not found") }
        if (request.status != AccountStatusRequestStatus.PENDING) {
            throw InvalidRequestException("Account status request is not pending")
        }
        val actor = currentUserLogin() ?: throw NotAuthorizedException()
        val decisionReason = decision?.reason?.trim()?.takeIf { it.isNotEmpty() }
        request.status = AccountStatusRequestStatus.REJECTED
        request.decidedBy = actor
        request.decidedAt = OffsetDateTime.now()
        request.decisionReason = decisionReason
        notifyRequesterDecision(request, approved = false)
        return toDto(request)
    }

    /**
     * System paths (scheduler / sync / application) that flip state immediately.
     */
    @Transactional
    fun applyImmediate(
        accountId: Int,
        activeTo: Boolean,
        source: AccountStatusEventSource,
        actorUsername: String?,
        reason: String?,
        request: AccountStatusRequest? = null,
        notifyApprover: Boolean = true,
    ): Account {
        val before = accountService.getAccount(accountId)
        val changed = before.active != activeTo
        val account = accountService.switchActiveState(accountId, activeTo)
        if (changed) {
            recordEvent(account, activeTo, source, actorUsername, reason, request)
            if (notifyApprover) {
                notifyApproverOfChange(account, activeTo, source, actorUsername, reason)
            }
        }
        return account
    }

    /**
     * When active was flipped outside [AccountService.switchActiveState] (e.g. user sync).
     */
    @Transactional
    fun recordExternalChange(
        account: Account,
        activeTo: Boolean,
        source: AccountStatusEventSource,
        actorUsername: String? = null,
        reason: String? = null,
        notifyApprover: Boolean = true,
    ) {
        recordEvent(account, activeTo, source, actorUsername, reason, null)
        if (notifyApprover) {
            notifyApproverOfChange(account, activeTo, source, actorUsername, reason)
        }
    }

    private fun createPending(
        accountId: Int,
        requestedActive: Boolean,
        reason: String?,
        createdBy: String,
    ): AccountStatusRequest {
        val target = accountService.getAccount(accountId)
        if (target.active == requestedActive) {
            throw InvalidRequestException(
                if (requestedActive) "Account is already active" else "Account is already inactive"
            )
        }
        if (requestRepository.existsByTargetAccountIdAndRequestedActiveAndStatus(
                target.id!!,
                requestedActive,
                AccountStatusRequestStatus.PENDING,
            )
        ) {
            throw InvalidRequestException("A pending request for this account and direction already exists")
        }
        val saved = requestRepository.save(
            AccountStatusRequest(
                targetAccount = target,
                requestedActive = requestedActive,
                status = AccountStatusRequestStatus.PENDING,
                reason = reason?.trim()?.takeIf { it.isNotEmpty() },
                createdBy = createdBy,
            )
        )
        notifyApproverOfRequest(saved)
        return saved
    }

    private fun recordEvent(
        account: Account,
        activeTo: Boolean,
        source: AccountStatusEventSource,
        actorUsername: String?,
        reason: String?,
        request: AccountStatusRequest?,
    ) {
        eventRepository.save(
            AccountStatusEvent(
                account = account,
                activeTo = activeTo,
                source = source,
                actorUsername = actorUsername,
                reason = reason,
                request = request,
            )
        )
    }

    private fun notifyApproverOfRequest(request: AccountStatusRequest) {
        val approver = resolveApprover() ?: return
        if (approver.username.equals(request.createdBy, ignoreCase = true)) return
        val action = if (request.requestedActive) "активацию" else "деактивацию"
        val reason = request.reason?.let { "\nПричина: $it" } ?: ""
        inboxService.notifyAccountStatusRequest(
            recipient = approver.username,
            subject = "Запрос на $action: ${request.targetAccount.fullName}",
            body = "Запрос на $action учётной записи ${request.targetAccount.fullName} " +
                "(${request.targetAccount.username}) от ${request.createdBy}.$reason\n\n" +
                "Откройте раздел «Активации» для принятия решения.",
            createdBy = request.createdBy,
        )
    }

    private fun notifyRequesterDecision(request: AccountStatusRequest, approved: Boolean) {
        val actor = currentUserLogin() ?: return
        if (request.createdBy.equals(actor, ignoreCase = true)) return
        val action = if (request.requestedActive) "активация" else "деактивация"
        val subject = if (approved) {
            "Согласована $action: ${request.targetAccount.fullName}"
        } else {
            "Отклонена $action: ${request.targetAccount.fullName}"
        }
        val extra = request.decisionReason?.let { "\nПричина: $it" } ?: ""
        val body = if (approved) {
            "Ваш запрос на $action учётной записи ${request.targetAccount.fullName} " +
                "(${request.targetAccount.username}) согласован.$extra"
        } else {
            "Ваш запрос на $action учётной записи ${request.targetAccount.fullName} " +
                "(${request.targetAccount.username}) отклонён.$extra"
        }
        inboxService.notifyAccountStatusDecision(
            username = request.createdBy,
            subject = subject,
            body = body,
            createdBy = actor,
        )
    }

    private fun notifyApproverOfChange(
        account: Account,
        activeTo: Boolean,
        source: AccountStatusEventSource,
        actorUsername: String?,
        reason: String?,
    ) {
        val approver = resolveApprover() ?: return
        if (actorUsername != null && approver.username.equals(actorUsername, ignoreCase = true)) return
        val action = if (activeTo) "активирована" else "деактивирована"
        val sourceLabel = when (source) {
            AccountStatusEventSource.SCHEDULER -> "планировщиком (окончание контракта)"
            AccountStatusEventSource.SYNC -> "синхронизацией пользователей"
            AccountStatusEventSource.REQUEST -> "по согласованной заявке"
            AccountStatusEventSource.DIRECT -> "напрямую"
        }
        val byWhom = actorUsername?.let { " ($it)" } ?: ""
        val why = reason?.let { "\nПричина: $it" } ?: ""
        inboxService.notifyAccountStatusChanged(
            recipient = approver.username,
            subject = "Учётная запись $action: ${account.fullName}",
            body = "Учётная запись ${account.fullName} (${account.username}) была $action $sourceLabel$byWhom.$why",
            createdBy = actorUsername,
        )
    }

    private fun assertCanManageStatus() {
        val actor = accountService.getCurrentAccount()
        if (!actor.groups.any { it == UserGroup.ADMIN_SSO || it == UserGroup.ADMIN_VOLUNTEER }) {
            throw NotAuthorizedException()
        }
    }

    private fun assertCanView() {
        val actor = accountService.getCurrentAccount()
        if (isApprover(actor.username)) return
        if (!actor.groups.any { it == UserGroup.ADMIN_SSO || it == UserGroup.ADMIN_VOLUNTEER }) {
            throw NotAuthorizedException()
        }
    }

    private fun assertIsApprover() {
        val login = currentUserLogin() ?: throw NotAuthorizedException()
        if (!isApprover(login)) throw NotAuthorizedException()
    }

    private fun toDto(request: AccountStatusRequest) = AccountStatusRequestDto(
        id = request.id!!,
        targetAccountId = request.targetAccount.id!!,
        targetUsername = request.targetAccount.username,
        targetFullName = request.targetAccount.fullName,
        requestedActive = request.requestedActive,
        status = request.status,
        reason = request.reason,
        createdBy = request.createdBy,
        createdAt = request.createdAt,
        decidedBy = request.decidedBy,
        decidedAt = request.decidedAt,
        decisionReason = request.decisionReason,
    )

    private fun toEventDto(event: AccountStatusEvent) = AccountStatusEventDto(
        id = event.id!!,
        accountId = event.account.id!!,
        accountUsername = event.account.username,
        accountFullName = event.account.fullName,
        activeTo = event.activeTo,
        source = event.source,
        actorUsername = event.actorUsername,
        reason = event.reason,
        createdAt = event.createdAt,
        requestId = event.request?.id,
    )

    companion object {
        private val log = LoggerFactory.getLogger(AccountStatusService::class.java)
        private const val EVENTS_LIMIT = 200
    }
}
