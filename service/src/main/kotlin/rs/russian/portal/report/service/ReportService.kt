package rs.russian.portal.report.service

import jakarta.persistence.EntityManager
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.generated.model.NoteDto
import rs.russian.generated.model.ReportDto
import rs.russian.generated.model.ReportFilter
import rs.russian.portal.file.service.FileService
import rs.russian.portal.note.domain.Note
import rs.russian.portal.note.domain.enums.EntityType
import rs.russian.portal.note.service.NoteService
import rs.russian.portal.report.domain.Report
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.report.domain.specification.from
import rs.russian.portal.report.mapper.ReportMapper
import rs.russian.portal.report.repository.ReportRepository
import rs.russian.portal.shared.ai.domain.AiProfileCode.SERBIAN_TRANSLATOR
import rs.russian.portal.shared.ai.service.TextTranslationService
import rs.russian.portal.inbox.service.InboxService
import rs.russian.portal.program.service.ProgramCuratorService
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.service.AccountService
import rs.russian.portal.workassignment.service.WorkAssignmentService
import java.util.*

@Service
class ReportService(
    private val accountService: AccountService,
    private val fileService: FileService,
    private val reportMapper: ReportMapper,
    private val noteService: NoteService,
    private val reportRepository: ReportRepository,
    private val entityManager: EntityManager,
    private val textTranslationService: TextTranslationService,
    private val workAssignmentService: WorkAssignmentService,
    private val inboxService: InboxService,
    private val programCuratorService: ProgramCuratorService,
) {

    @Transactional(readOnly = true)
    fun getReport(reportId: UUID): Report {
        return reportRepository.findById(reportId).orElseThrow()
    }

    @Transactional
    fun createReport(reportDto: ReportDto): Report {
        val currentAccount = accountService.getCurrentAccount()
        val report = Report(
            account = currentAccount,
            status = ReportStatus.CREATED,
            program = currentAccount.info?.program,
            project = currentAccount.info?.project
        )
        requireCustomers(reportDto)
        val tasks = reportDto.tasks.map { taskDto ->
            reportMapper.map(taskDto, report).also { task ->
                task.customer = accountService.findAccountByLogin(taskDto.customer)
                task.files = fileService.findAllByIds(taskDto.files?.map { it.id }?.toMutableSet())
                if (task.nameSr.isNullOrBlank()) {
                    task.nameSr = textTranslationService.translate(task.name, SERBIAN_TRANSLATOR)
                }
                if (task.descriptionSr.isNullOrBlank()) {
                    task.descriptionSr = textTranslationService.translate(task.description, SERBIAN_TRANSLATOR)
                }
            }
        }
        val saved = reportRepository.save(report.also { it.tasks = tasks.toMutableSet() })
        workAssignmentService.markFromReport(saved)
        notifyCustomers(saved)
        return saved
    }

    @Transactional
    fun deleteReport(reportId: UUID) {
        val report = getReport(reportId)
        reportRepository.save(report.also { it.deleted = true })
    }

    @Transactional
    fun updateReport(reportDto: ReportDto): Report {
        val report = getReport(reportDto.id)
        requireCustomers(reportDto)
        val existingTasksById = report.tasks.associateBy { it.id }
        val tasks = reportDto.tasks.map { taskDto ->
            val existingTask = taskDto.id?.let(existingTasksById::get)
            reportMapper.map(taskDto, report).also { task ->
                task.customer = accountService.findAccountByLogin(taskDto.customer)
                task.files = fileService.findAllByIds(taskDto.files?.map { it.id }?.toSet())
                if (task.nameSr.isNullOrBlank() || existingTask?.name != task.name) {
                    task.nameSr = textTranslationService.translate(task.name, SERBIAN_TRANSLATOR)
                }
                if (task.descriptionSr.isNullOrBlank() || existingTask?.description != task.description) {
                    task.descriptionSr = textTranslationService.translate(task.description, SERBIAN_TRANSLATOR)
                }
            }
        }
        report.status = ReportStatus.CREATED
        report.tasks.clear()
        val saved = reportRepository.save(report.also { it.tasks.addAll(tasks) })
        workAssignmentService.markFromReport(saved)
        notifyCustomers(saved)
        return saved
    }

    @Transactional(readOnly = true)
    fun getReports(reportFilter: ReportFilter, pageable: Pageable): Page<Report> {
        return findAllFull(from(reportFilter), pageable)
    }

    @Transactional(readOnly = true)
    fun getReportsForCustomer(status: ReportStatus?, pageable: Pageable): Page<Report> {
        val login = currentUserLogin() ?: throw NotAuthorizedException()
        val logins = customerLoginsFor(login)
        val ids = reportRepository.findIdsByCustomers(logins, status, pageable)
        if (ids.content.isEmpty()) {
            return PageImpl(emptyList(), ids.pageable, ids.totalElements)
        }
        val reports = reportRepository.findAllByIdIn(ids.content, Sort.by(Sort.Direction.DESC, "createTime"))
        return PageImpl(reports, ids.pageable, ids.totalElements)
    }

    @Transactional(readOnly = true)
    fun pendingCountForCustomer(): Long {
        val login = currentUserLogin() ?: throw NotAuthorizedException()
        return reportRepository.countByCustomers(customerLoginsFor(login), ReportStatus.CREATED)
    }

    @Transactional
    fun save(report: Report): Report {
        return reportRepository.save(report)
    }

    @Transactional
    fun addNote(reportId: UUID, noteDto: NoteDto): Note {
        val report = getReport(reportId)
        val currentAccount = accountService.getAccountByLogin(currentUserLogin() ?: throw NotAuthorizedException())
        val note = noteService.save(
            Note(
                createdBy = currentAccount.username,
                entityId = reportId,
                entityType = EntityType.REPORT,
                text = noteDto.text
            )
        )
        report.notes.add(note)
        return note
    }

    @Transactional
    fun changeStatus(reportId: UUID, status: ReportStatus, noteText: String? = null) {
        val report = getReport(reportId)
        val moderator = accountService.getAccountByLogin(currentUserLogin() ?: throw NotAuthorizedException())
        if (!canModerate(report, moderator.username, moderator.groups)) {
            throw NotAuthorizedException()
        }
        if (!noteText.isNullOrEmpty()) {
            val note = noteService.save(
                Note(
                    createdBy = moderator.username,
                    entityId = reportId,
                    entityType = EntityType.REPORT,
                    text = noteText
                )
            )
            report.notes.add(note)
        }
        report.status = status
        report.moderator = moderator
        workAssignmentService.markFromReport(report)
    }

    private fun requireCustomers(reportDto: ReportDto) {
        if (reportDto.tasks.any { it.customer.isNullOrBlank() }) {
            throw InvalidRequestException("Укажите заказчика задачи")
        }
        if (!programCuratorService.hasAny()) {
            reportDto.tasks.mapNotNull { it.customer }.distinct().forEach { login ->
                accountService.findAccountByLogin(login)
                    ?: throw InvalidRequestException("Заказчик '$login' не найден")
            }
            return
        }
        reportDto.tasks.mapNotNull { it.customer }.distinct().forEach { login ->
            if (!programCuratorService.isAllowedCustomer(login)) {
                throw InvalidRequestException("Заказчик должен быть куратором или его делегатом по приёмке")
            }
        }
    }

    private fun notifyCustomers(report: Report) {
        val id = report.id?.toString() ?: return
        val volunteer = report.account.fullName
        val programCode = report.program?.code
        val recipients = linkedSetOf<String>()
        report.tasks.mapNotNull { it.customer?.username }.distinct().forEach { customer ->
            recipients += customer
            programCuratorService.delegateUsernamesOf(customer, programCode).forEach { recipients += it }
        }
        recipients.forEach { login ->
            if (!login.equals(report.account.username, ignoreCase = true)) {
                inboxService.notifyReportCustomer(login, volunteer, id)
            }
        }
    }

    private fun canModerate(report: Report, login: String, groups: Set<UserGroup>): Boolean {
        if (groups.any { it in MODERATORS }) return true
        val programCode = report.program?.code
        return report.tasks.any { task ->
            val customer = task.customer?.username ?: return@any false
            customer.equals(login, ignoreCase = true) ||
                programCuratorService.canAcceptAsDelegate(login, customer, programCode)
        }
    }

    private fun customerLoginsFor(login: String): List<String> {
        val logins = linkedSetOf(login.lowercase())
        programCuratorService.curatorUsernamesDelegatedTo(login).forEach { logins += it.lowercase() }
        return logins.toList()
    }

    /**
     * Получить список отчетов с использованием EntityGraph
     * Решает проблему, при которой невозможно одновременная работа Pageable и EntityGraph:
     * HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
     * entityManager.detach(...) - необходим потому что findAll загружает "легковесные" объекты и
     * сохраняет их в контекст, а findAllByIdIn уже загрузит полные объекты, однако если не очистить
     * контекст, то они не будут перезаписаны в контексте, что повлечет дополнительные SQL запросы
     */
    private fun findAllFull(specification: Specification<Report>, pageable: Pageable): Page<Report> {
        val reports = reportRepository.findAll(specification, pageable)
        reports.forEach { report -> entityManager.detach(report) }
        val reportsFull = reportRepository.findAllByIdIn(reports.mapNotNull { it.id }, pageable.sort)
        return PageImpl(reportsFull, reports.pageable, reports.totalElements)
    }

    companion object {
        private val MODERATORS = setOf(UserGroup.ADMIN, UserGroup.ADMIN_VOLUNTEER, UserGroup.MAIN_VOLUNTEER)
    }
}
