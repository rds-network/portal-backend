package rs.russian.portal.dissolution.service

import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.config.AppProperties
import rs.russian.portal.dissolution.api.DissolutionRequestCreateRequest
import rs.russian.portal.dissolution.api.DissolutionRequestDto
import rs.russian.portal.dissolution.api.DissolutionRequestMetaDto
import rs.russian.portal.dissolution.api.DissolutionRequestRejectRequest
import rs.russian.portal.dissolution.domain.DissolutionRequest
import rs.russian.portal.dissolution.domain.enums.DissolutionRequestStatus
import rs.russian.portal.dissolution.repository.DissolutionRequestRepository
import rs.russian.portal.inbox.service.InboxService
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.PrivilegedOps
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.shared.security.currentUserRoles
import rs.russian.portal.shared.security.realUserLogin
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.AccountService
import rs.russian.portal.user.service.DissolutionQueueService
import java.time.OffsetDateTime
import java.util.UUID

@Service
class DissolutionRequestService(
    private val appProperties: AppProperties,
    private val dissolutionRequestRepository: DissolutionRequestRepository,
    private val accountRepository: AccountRepository,
    private val accountService: AccountService,
    private val inboxService: InboxService,
    private val dissolutionQueueService: DissolutionQueueService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun meta(): DissolutionRequestMetaDto {
        val real = realUserLogin() ?: throw NotAuthorizedException()
        return DissolutionRequestMetaDto(
            approverUsername = approverUsername(),
            isDissolutionApprover = isDissolutionApprover(real),
        )
    }

    /**
     * Same gate as leave: configured approver (username / email / Account) or ADMIN_SSO.
     * Uses the real OIDC principal so impersonation does not hide the queue.
     */
    fun isDissolutionApprover(login: String? = null, account: Account? = null): Boolean {
        val real = login ?: realUserLogin()
        val resolved = account ?: resolveAccount(real)
        return PrivilegedOps.isAllowed(real, currentUserRoles(), resolved)
    }

    fun approverUsername(): String = appProperties.leave.approverUsername.trim()

    private fun resolveAccount(login: String?): Account? {
        if (login.isNullOrBlank()) return null
        return accountRepository.findByUsername(login).orElse(null)
            ?: accountRepository.findByEmail(login).orElse(null)
    }

    @Transactional
    fun create(request: DissolutionRequestCreateRequest): DissolutionRequestDto {
        val actor = accountService.getCurrentAccount()
        val targetUsername = request.username?.trim()?.takeIf { it.isNotEmpty() } ?: actor.username
        if (!targetUsername.equals(actor.username, ignoreCase = true) && !isManager(actor.groups)) {
            throw NotAuthorizedException()
        }
        val target = accountRepository.findByUsername(targetUsername).orElse(null)
            ?: throw InvalidRequestException("Unknown user $targetUsername")
        if (dissolutionRequestRepository.existsByUsernameIgnoreCaseAndStatus(target.username, DissolutionRequestStatus.PENDING)) {
            throw InvalidRequestException("Уже есть ожидающее заявление на расторжение")
        }
        val reason = request.reason?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw InvalidRequestException("Укажите причину расторжения")
        val saved = dissolutionRequestRepository.save(
            DissolutionRequest(
                username = target.username,
                fromDate = request.fromDate,
                reason = reason,
                status = DissolutionRequestStatus.PENDING,
            )
        )
        notifyNewRequest(saved, target.fullName)
        return toDto(saved, target.fullName, target.info?.program?.code, target.mupLetterSentAt)
    }

    @Transactional(readOnly = true)
    fun mine(): List<DissolutionRequestDto> {
        val login = currentUserLogin() ?: throw NotAuthorizedException()
        return dissolutionRequestRepository.findAllByUsernameIgnoreCaseOrderByCreatedAtDesc(login).map(::toDto)
    }

    @Transactional(readOnly = true)
    fun pending(): List<DissolutionRequestDto> {
        if (!isDissolutionApprover()) {
            return emptyList()
        }
        return dissolutionRequestRepository.findAllByStatusOrderByCreatedAtAsc(DissolutionRequestStatus.PENDING)
            .map(::toDto)
    }

    /**
     * Decided requests: after accept/reject, pending empties — approver still needs history.
     * Approver sees all; everyone else sees only their own decided requests.
     */
    @Transactional(readOnly = true)
    fun history(): List<DissolutionRequestDto> {
        val actor = accountService.getCurrentAccount()
        val decided = dissolutionRequestRepository.findDecided(
            DissolutionRequestStatus.PENDING,
            PageRequest.of(0, HISTORY_LIMIT),
        )
        if (isDissolutionApprover()) {
            return decided.map(::toDto)
        }
        return decided
            .filter { it.username.equals(actor.username, ignoreCase = true) }
            .map(::toDto)
    }

    @Transactional
    fun accept(id: UUID): DissolutionRequestDto {
        val item = dissolutionRequestRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Dissolution request $id not found") }
        assertCanDecide()
        if (item.status != DissolutionRequestStatus.PENDING) {
            throw InvalidRequestException("Dissolution request is not pending")
        }
        val actor = currentUserLogin() ?: throw NotAuthorizedException()
        item.status = DissolutionRequestStatus.ACCEPTED
        item.decidedAt = OffsetDateTime.now()
        item.decidedBy = actor
        val account = accountRepository.findByUsername(item.username).orElse(null)
            ?: throw InvalidRequestException("Unknown user ${item.username}")
        val queueReason = item.reason?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Заявление участника с ${item.fromDate}"
        dissolutionQueueService.enqueueAccount(account, actor, queueReason)
        notifyDecision(item, accepted = true)
        return toDto(item)
    }

    @Transactional
    fun reject(id: UUID, request: DissolutionRequestRejectRequest?): DissolutionRequestDto {
        val item = dissolutionRequestRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Dissolution request $id not found") }
        assertCanDecide()
        if (item.status != DissolutionRequestStatus.PENDING) {
            throw InvalidRequestException("Dissolution request is not pending")
        }
        val actor = currentUserLogin() ?: throw NotAuthorizedException()
        val rejectReason = request?.reason?.trim()?.takeIf { it.isNotEmpty() }
        item.status = DissolutionRequestStatus.REJECTED
        item.decidedAt = OffsetDateTime.now()
        item.decidedBy = actor
        item.decisionReason = rejectReason
        notifyDecision(item, accepted = false, rejectReason = rejectReason)
        return toDto(item)
    }

    @Transactional
    fun cancel(id: UUID): DissolutionRequestDto {
        val item = dissolutionRequestRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Dissolution request $id not found") }
        val actor = accountService.getCurrentAccount()
        if (!item.username.equals(actor.username, ignoreCase = true) && !isManager(actor.groups)) {
            throw NotAuthorizedException()
        }
        if (item.status != DissolutionRequestStatus.PENDING) {
            throw InvalidRequestException("Dissolution request is not pending")
        }
        item.status = DissolutionRequestStatus.CANCELLED
        item.decidedAt = OffsetDateTime.now()
        item.decidedBy = actor.username
        return toDto(item)
    }

    private fun assertCanDecide() {
        if (!isDissolutionApprover()) {
            throw NotAuthorizedException()
        }
    }

    private fun notifyNewRequest(item: DissolutionRequest, fullName: String) {
        val actor = currentUserLogin() ?: return
        val approver = resolveApprover() ?: return
        if (approver.username.equals(item.username, ignoreCase = true)) {
            return
        }
        val reason = item.reason?.let { "\nПричина: $it" } ?: ""
        val subject = "Заявление на расторжение: $fullName"
        val body = "Заявление на расторжение договора от $fullName (${item.username}).\n" +
            "С даты: ${item.fromDate}.$reason\n\n" +
            "Откройте раздел «Расторжение» или «Заявления на расторжение» для решения. Письмо в МУП отправляется вручную."
        inboxService.notifyDissolutionRequest(
            recipient = approver.username,
            subject = subject,
            body = body,
            createdBy = actor,
        )
    }

    private fun resolveApprover(): Account? {
        val username = approverUsername()
        if (username.isBlank()) {
            log.warn("Dissolution approver username is blank")
            return null
        }
        val account = accountRepository.findByUsername(username).orElse(null)
        if (account == null) {
            log.warn("Dissolution approver '{}' not found", username)
            return null
        }
        if (!account.active) {
            log.warn("Dissolution approver '{}' is inactive", username)
            return null
        }
        return account
    }

    private fun notifyDecision(item: DissolutionRequest, accepted: Boolean, rejectReason: String? = null) {
        val actor = currentUserLogin() ?: return
        val subject = if (accepted) "Заявление на расторжение согласовано" else "Заявление на расторжение отклонено"
        val extra = rejectReason?.let { "\nПричина отказа: $it" } ?: ""
        val body = if (accepted) {
            "Ваше заявление на расторжение договора (с ${item.fromDate}) согласовано. " +
                "Письмо в МУП отправит администратор отдельно."
        } else {
            "Ваше заявление на расторжение договора (с ${item.fromDate}) отклонено.$extra"
        }
        inboxService.notifyDissolutionDecision(
            username = item.username,
            subject = subject,
            body = body,
            createdBy = actor,
        )
    }

    private fun isManager(groups: Set<UserGroup>): Boolean {
        val managers = setOf(
            UserGroup.ADMIN,
            UserGroup.ADMIN_VOLUNTEER,
            UserGroup.ADMIN_SSO,
            UserGroup.MAIN_VOLUNTEER,
        )
        return groups.any { it in managers }
    }

    private fun toDto(item: DissolutionRequest): DissolutionRequestDto {
        val account = accountRepository.findByUsername(item.username).orElse(null)
        return toDto(item, account?.fullName, account?.info?.program?.code, account?.mupLetterSentAt)
    }

    private fun toDto(
        item: DissolutionRequest,
        fullName: String?,
        programCode: String?,
        mupLetterSentAt: OffsetDateTime?,
    ) = DissolutionRequestDto(
        id = item.id!!,
        username = item.username,
        fullName = fullName,
        programCode = programCode,
        fromDate = item.fromDate,
        status = item.status,
        reason = item.reason,
        createdAt = item.createdAt,
        decidedAt = item.decidedAt,
        decidedBy = item.decidedBy,
        decisionReason = item.decisionReason,
        mupLetterSentAt = mupLetterSentAt,
    )

    companion object {
        private const val HISTORY_LIMIT = 200
    }
}
