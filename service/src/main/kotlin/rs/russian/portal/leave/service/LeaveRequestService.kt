package rs.russian.portal.leave.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.inbox.service.InboxService
import rs.russian.portal.leave.api.LeaveRequestCreateRequest
import rs.russian.portal.leave.api.LeaveRequestDto
import rs.russian.portal.leave.api.LeaveRequestRejectRequest
import rs.russian.portal.leave.domain.LeaveRequest
import rs.russian.portal.leave.domain.enums.LeaveRequestStatus
import rs.russian.portal.leave.repository.LeaveRequestRepository
import rs.russian.portal.program.repository.ProgramCuratorRepository
import rs.russian.portal.program.service.ProgramCuratorService
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Service
class LeaveRequestService(
    private val leaveRequestRepository: LeaveRequestRepository,
    private val accountRepository: AccountRepository,
    private val accountService: AccountService,
    private val programCuratorService: ProgramCuratorService,
    private val programCuratorRepository: ProgramCuratorRepository,
    private val inboxService: InboxService,
) {

    @Transactional
    fun create(request: LeaveRequestCreateRequest): LeaveRequestDto {
        val actor = accountService.getCurrentAccount()
        val targetUsername = request.username?.trim()?.takeIf { it.isNotEmpty() } ?: actor.username
        if (!targetUsername.equals(actor.username, ignoreCase = true) && !isManager(actor.groups)) {
            throw NotAuthorizedException()
        }
        val target = accountRepository.findByUsername(targetUsername).orElse(null)
            ?: throw InvalidRequestException("Unknown user $targetUsername")
        if (request.endDate.isBefore(request.startDate)) {
            throw InvalidRequestException("endDate must be on or after startDate")
        }
        val saved = leaveRequestRepository.save(
            LeaveRequest(
                username = target.username,
                startDate = request.startDate,
                endDate = request.endDate,
                reason = request.reason?.trim()?.takeIf { it.isNotEmpty() },
                status = LeaveRequestStatus.PENDING,
            )
        )
        notifyNewLeave(saved, target.fullName)
        return toDto(saved, target.fullName, target.info?.program?.code)
    }

    @Transactional(readOnly = true)
    fun mine(): List<LeaveRequestDto> {
        val login = currentUserLogin() ?: throw NotAuthorizedException()
        return leaveRequestRepository.findAllByUsernameIgnoreCaseOrderByCreatedAtDesc(login).map(::toDto)
    }

    @Transactional(readOnly = true)
    fun pending(): List<LeaveRequestDto> {
        val actor = accountService.getCurrentAccount()
        if (isManager(actor.groups)) {
            return leaveRequestRepository.findAllByStatusOrderByCreatedAtAsc(LeaveRequestStatus.PENDING)
                .map(::toDto)
        }
        if (!programCuratorService.isCurator(actor.username)) {
            throw NotAuthorizedException()
        }
        val programCodes = programCuratorService.programCodesOf(actor.username)
        if (programCodes.isEmpty()) return emptyList()
        val candidates = leaveRequestRepository.findAllByStatusOrderByCreatedAtAsc(LeaveRequestStatus.PENDING)
        return candidates.mapNotNull { leave ->
            // Curators themselves are decided only by managers — skip them here.
            if (programCuratorService.isCurator(leave.username)) return@mapNotNull null
            val account = accountRepository.findByUsername(leave.username).orElse(null) ?: return@mapNotNull null
            val programCode = account.info?.program?.code ?: return@mapNotNull null
            if (programCodes.none { it.equals(programCode, ignoreCase = true) }) return@mapNotNull null
            toDto(leave, account.fullName, programCode)
        }
    }

    @Transactional
    fun accept(id: UUID): LeaveRequestDto {
        val leave = leaveRequestRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Leave request $id not found") }
        assertCanDecide(leave)
        if (leave.status != LeaveRequestStatus.PENDING) {
            throw InvalidRequestException("Leave request is not pending")
        }
        val actor = currentUserLogin() ?: throw NotAuthorizedException()
        leave.status = LeaveRequestStatus.ACCEPTED
        leave.decidedAt = OffsetDateTime.now()
        leave.decidedBy = actor
        notifyDecision(leave, accepted = true)
        return toDto(leave)
    }

    @Transactional
    fun reject(id: UUID, request: LeaveRequestRejectRequest?): LeaveRequestDto {
        val leave = leaveRequestRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Leave request $id not found") }
        assertCanDecide(leave)
        if (leave.status != LeaveRequestStatus.PENDING) {
            throw InvalidRequestException("Leave request is not pending")
        }
        val actor = currentUserLogin() ?: throw NotAuthorizedException()
        leave.status = LeaveRequestStatus.REJECTED
        leave.decidedAt = OffsetDateTime.now()
        leave.decidedBy = actor
        val rejectReason = request?.reason?.trim()?.takeIf { it.isNotEmpty() }
        notifyDecision(leave, accepted = false, rejectReason = rejectReason)
        return toDto(leave)
    }

    @Transactional(readOnly = true)
    fun active(username: String?, from: LocalDate?, to: LocalDate?): List<LeaveRequestDto> {
        val actor = accountService.getCurrentAccount()
        val target = username?.trim()?.takeIf { it.isNotEmpty() } ?: actor.username
        if (!target.equals(actor.username, ignoreCase = true) && !isManager(actor.groups) &&
            !programCuratorService.isCurator(actor.username)
        ) {
            throw NotAuthorizedException()
        }
        val rangeFrom = from ?: LocalDate.now().withDayOfYear(1)
        val rangeTo = to ?: LocalDate.now().withMonth(12).withDayOfMonth(31)
        return leaveRequestRepository
            .findAcceptedOverlapping(target, rangeFrom, rangeTo, LeaveRequestStatus.ACCEPTED)
            .map(::toDto)
    }

    private fun assertCanDecide(leave: LeaveRequest) {
        val actor = accountService.getCurrentAccount()
        if (isManager(actor.groups)) return

        // Requester is a curator (or has no program curator path) → only managers may decide.
        if (programCuratorService.isCurator(leave.username)) {
            throw NotAuthorizedException()
        }

        if (!programCuratorService.isCurator(actor.username)) {
            throw NotAuthorizedException()
        }
        val account = accountRepository.findByUsername(leave.username).orElse(null)
            ?: throw NotAuthorizedException()
        val programCode = account.info?.program?.code
            ?: throw NotAuthorizedException()
        val programs = programCuratorService.programCodesOf(actor.username)
        if (programs.none { it.equals(programCode, ignoreCase = true) }) {
            throw NotAuthorizedException()
        }
    }

    private fun notifyNewLeave(leave: LeaveRequest, fullName: String) {
        val actor = currentUserLogin() ?: return
        val period = "${leave.startDate} — ${leave.endDate}"
        val reason = leave.reason?.let { "\nПричина: $it" } ?: ""
        val subject = "Запрос на отпуск: $fullName"
        val body = "Запрос на отпуск от $fullName (${leave.username}).\nПериод: $period.$reason\n\nОткройте раздел «Отпуск» для принятия решения."
        // Curators (and users without a program) → senior admins only.
        // Regular volunteers → program curators only. Never blast ADMIN_VOLUNTEER / all managers.
        val candidates = mutableSetOf<String>()
        if (programCuratorService.isCurator(leave.username)) {
            candidates += seniorManagerUsernames()
        } else {
            val programCode = accountRepository.findByUsername(leave.username).orElse(null)?.info?.program?.code
            if (programCode.isNullOrBlank()) {
                candidates += seniorManagerUsernames()
            } else {
                candidates += programCuratorRepository.findAllByProgramCodeIgnoreCase(programCode).map { it.username }
            }
        }
        candidates.removeIf { it.equals(leave.username, ignoreCase = true) }
        activeUsernames(candidates).forEach { recipient ->
            inboxService.notifyLeaveRequest(
                recipient = recipient,
                subject = subject,
                body = body,
                createdBy = actor,
            )
        }
    }

    private fun notifyDecision(leave: LeaveRequest, accepted: Boolean, rejectReason: String? = null) {
        val actor = currentUserLogin() ?: return
        val period = "${leave.startDate} — ${leave.endDate}"
        val subject = if (accepted) "Отпуск согласован" else "Отпуск отклонён"
        val extra = rejectReason?.let { "\nПричина отказа: $it" } ?: ""
        val body = if (accepted) {
            "Ваш запрос на отпуск ($period) согласован."
        } else {
            "Ваш запрос на отпуск ($period) отклонён.$extra"
        }
        inboxService.notifyLeaveDecision(
            username = leave.username,
            subject = subject,
            body = body,
            createdBy = actor,
        )
    }

    /** MAIN_VOLUNTEER + ADMIN only — decides leave for curators / users without a program. */
    private fun seniorManagerUsernames(): List<String> =
        listOf(UserGroup.MAIN_VOLUNTEER, UserGroup.ADMIN)
            .flatMap { accountRepository.findAllActiveUsernamesByGroup(it.name) }
            .distinct()

    private fun activeUsernames(logins: Collection<String>): List<String> {
        if (logins.isEmpty()) return emptyList()
        return accountRepository.findAllByUsernameIn(logins.toList())
            .filter { it.active }
            .map { it.username }
            .distinct()
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

    private fun toDto(leave: LeaveRequest): LeaveRequestDto {
        val account = accountRepository.findByUsername(leave.username).orElse(null)
        return toDto(leave, account?.fullName, account?.info?.program?.code)
    }

    private fun toDto(leave: LeaveRequest, fullName: String?, programCode: String?) = LeaveRequestDto(
        id = leave.id!!,
        username = leave.username,
        fullName = fullName,
        programCode = programCode,
        startDate = leave.startDate,
        endDate = leave.endDate,
        status = leave.status,
        reason = leave.reason,
        createdAt = leave.createdAt,
        decidedAt = leave.decidedAt,
        decidedBy = leave.decidedBy,
    )
}
