package rs.russian.portal.workassignment.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.report.domain.Report
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_SSO
import rs.russian.portal.user.domain.enums.UserGroup.MAIN_VOLUNTEER
import org.slf4j.LoggerFactory
import rs.russian.portal.inbox.service.InboxService
import rs.russian.portal.program.service.ProgramCuratorService
import rs.russian.portal.user.service.AccountService
import rs.russian.portal.workassignment.api.WorkAssignmentCreateRequest
import rs.russian.portal.workassignment.api.WorkAssignmentDto
import rs.russian.portal.workassignment.api.WorkAssignmentPatchRequest
import rs.russian.portal.workassignment.domain.WorkAssignment
import rs.russian.portal.workassignment.domain.enums.WorkAssignmentStatus
import rs.russian.portal.workassignment.repository.WorkAssignmentRepository
import java.util.UUID

@Service
class WorkAssignmentService(
    private val workAssignmentRepository: WorkAssignmentRepository,
    private val accountService: AccountService,
    private val inboxService: InboxService,
    private val programCuratorService: ProgramCuratorService,
) {

    @Transactional(readOnly = true)
    fun list(): List<WorkAssignmentDto> {
        val account = accountService.getCurrentAccount()
        val items = if (canManage()) {
            workAssignmentRepository.findAllByOrderByCreateTimeDesc()
        } else {
            workAssignmentRepository.findByAssigneeOrderByCreateTimeDesc(account.username)
        }
        return items.filter { it.status != WorkAssignmentStatus.ARCHIVED }.map(::toDto)
    }

    @Transactional
    fun create(request: WorkAssignmentCreateRequest): WorkAssignmentDto {
        if (!canManage()) throw NotAuthorizedException()
        val title = request.title.trim()
        if (title.length < 3) {
            throw InvalidRequestException("title must be at least 3 characters")
        }
        val createdBy = currentUserLogin() ?: throw NotAuthorizedException()
        val assigneeLogin = request.assignee?.trim()?.takeIf { it.isNotEmpty() }
        val assigneeAccount = assigneeLogin?.let { login ->
            accountService.findAccountByLogin(login)
                ?: throw InvalidRequestException("Assignee '$login' not found")
        }
        val assigneeName = assigneeAccount?.fullName
        val body = request.body?.trim()?.takeIf { it.isNotEmpty() }
        val saved = workAssignmentRepository.save(
            WorkAssignment(
                createdBy = createdBy,
                title = title,
                body = body,
                assignee = assigneeLogin,
                assigneeName = assigneeName,
                dueDate = request.dueDate,
            )
        )
        if (assigneeLogin != null) {
            try {
                inboxService.notifyAssigned(assigneeLogin, title, body, createdBy)
            } catch (ex: Exception) {
                log.warn("Could not notify assignee {} about task {}", assigneeLogin, saved.id, ex)
            }
        }
        return toDto(saved)
    }

    @Transactional
    fun patch(id: UUID, request: WorkAssignmentPatchRequest): WorkAssignmentDto {
        val account = accountService.getCurrentAccount()
        val item = workAssignmentRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Work assignment $id not found") }
        val manager = canManage()
        if (!manager && !item.assignee.equals(account.username, ignoreCase = true)) {
            throw NotAuthorizedException()
        }
        val previousAssignee = item.assignee
        if (manager) {
            request.title?.trim()?.takeIf { it.isNotEmpty() }?.let { item.title = it }
            request.body?.let { item.body = it.trim().takeIf { text -> text.isNotEmpty() } }
            request.dueDate?.let { item.dueDate = it }
            request.assignee?.let { raw ->
                val login = raw.trim().takeIf { it.isNotEmpty() }
                item.assignee = login
                item.assigneeName = login?.let { found ->
                    accountService.findAccountByLogin(found)?.fullName
                        ?: throw InvalidRequestException("Assignee '$found' not found")
                }
            }
        }
        request.status?.let { raw ->
            val next = parseStatus(raw)
            if (!manager) {
                val allowed = next == WorkAssignmentStatus.TODO || next == WorkAssignmentStatus.DOING
                if (!allowed) {
                    throw NotAuthorizedException()
                }
            }
            item.status = next
        }
        val nextAssignee = item.assignee
        if (manager && !nextAssignee.isNullOrBlank() && !nextAssignee.equals(previousAssignee, ignoreCase = true)) {
            try {
                inboxService.notifyAssigned(nextAssignee, item.title, item.body, account.username)
            } catch (ex: Exception) {
                log.warn("Could not notify assignee {} about task {}", nextAssignee, item.id, ex)
            }
        }
        return toDto(item)
    }

    @Transactional
    fun archive(id: UUID): WorkAssignmentDto {
        if (!canManage()) throw NotAuthorizedException()
        val item = workAssignmentRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Work assignment $id not found") }
        item.status = WorkAssignmentStatus.ARCHIVED
        return toDto(item)
    }

    @Transactional
    fun delete(id: UUID) {
        if (!canManage()) throw NotAuthorizedException()
        if (!workAssignmentRepository.existsById(id)) {
            throw EntityNotFoundException("Work assignment $id not found")
        }
        workAssignmentRepository.deleteById(id)
    }

    @Transactional
    fun markFromReport(username: String, names: Collection<String>, reportId: UUID?, reportStatus: ReportStatus) {
        if (names.isEmpty()) return
        val assignments = workAssignmentRepository.findByAssigneeOrderByCreateTimeDesc(username)
        for (assignment in assignments) {
            if (assignment.status == WorkAssignmentStatus.DONE || assignment.status == WorkAssignmentStatus.ARCHIVED) continue
            if (names.none { matches(it, assignment.title) }) continue
            assignment.reportId = reportId ?: assignment.reportId
            assignment.status = when (reportStatus) {
                ReportStatus.ACCEPTED -> WorkAssignmentStatus.DONE
                ReportStatus.REJECTED -> WorkAssignmentStatus.REDO
                ReportStatus.CREATED -> WorkAssignmentStatus.REVIEW
            }
        }
    }

    @Transactional
    fun markFromReport(report: Report) {
        val login = report.account.username
        val names = report.tasks.map { it.name }
        markFromReport(login, names, report.id, report.status)
    }

    private fun parseStatus(raw: String): WorkAssignmentStatus =
        try {
            WorkAssignmentStatus.valueOf(raw.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            throw InvalidRequestException("Unknown status '$raw'")
        }

    private fun canManage(): Boolean {
        val account = accountService.getCurrentAccount()
        if (account.groups.any { it == ADMIN || it == ADMIN_SSO || it == MAIN_VOLUNTEER }) {
            return true
        }
        return programCuratorService.isCurrentCurator()
    }

    private fun toDto(item: WorkAssignment) = WorkAssignmentDto(
        id = item.id!!,
        createTime = item.createTime,
        createdBy = item.createdBy,
        title = item.title,
        body = item.body,
        assignee = item.assignee,
        assigneeName = item.assigneeName,
        status = item.status.name,
        dueDate = item.dueDate,
        reportId = item.reportId,
    )

    companion object {
        private val log = LoggerFactory.getLogger(WorkAssignmentService::class.java)

        fun matches(taskName: String?, title: String?): Boolean {
            val left = normalize(taskName)
            val right = normalize(title)
            if (left.isEmpty() || right.isEmpty()) return false
            if (left == right) return true
            return left.length >= 8 && right.length >= 8 && (left.contains(right) || right.contains(left))
        }

        private fun normalize(value: String?): String =
            value.orEmpty().lowercase().replace(Regex("\\s+"), " ").trim()
    }
}
