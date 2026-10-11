package rs.russian.portal.report.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.generated.model.CreateReportRequest
import rs.russian.portal.program.repository.ProgramCuratorRepository
import rs.russian.portal.program.repository.ProgramRepository
import rs.russian.portal.program.repository.ProjectRepository
import rs.russian.portal.report.api.ExtAppendReportTasksRequest
import rs.russian.portal.report.api.ExtCreateTaskRequest
import rs.russian.portal.report.domain.Report
import rs.russian.portal.report.domain.Task
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.report.repository.ReportRepository
import rs.russian.portal.shared.ai.domain.AiProfileCode.SERBIAN_TRANSLATOR
import rs.russian.portal.shared.ai.service.TextTranslationService
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Service
class ReportExternalService(
    private val accountService: AccountService,
    private val reportRepository: ReportRepository,
    private val textTranslationService: TextTranslationService,
    private val programRepository: ProgramRepository,
    private val projectRepository: ProjectRepository,
    private val programCuratorRepository: ProgramCuratorRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createReport(request: CreateReportRequest): Report {
        val account = accountService.resolveAccountForExternalApi(request.user)
        val tasks = request.tasks.map { t ->
            ExtCreateTaskRequest(
                date = t.date,
                name = t.name,
                description = t.description,
                timeSpent = t.timeSpent,
                result = t.result,
                nameSr = t.nameSr,
                descriptionSr = t.descriptionSr,
            )
        }
        return createReportWithTasks(account, tasks)
    }

    /**
     * Append tasks to an open Clean City auto-report (CREATED + isAuto), or create a new one.
     * Caps at [MAX_TASKS_PER_REPORT]; skips tasks whose result URL already exists on the report.
     */
    @Transactional
    fun appendOrCreate(request: ExtAppendReportTasksRequest): Report {
        val user = request.user.trim()
        if (user.isEmpty()) throw InvalidRequestException("user is required")
        if (request.tasks.isEmpty()) throw InvalidRequestException("tasks must not be empty")
        if (request.tasks.size > MAX_TASKS_PER_REPORT) {
            throw InvalidRequestException("tasks must contain at most $MAX_TASKS_PER_REPORT items")
        }

        val account = accountService.resolveAccountForExternalApi(user)
        val open = findOpenAutoReport(account, request.reportId)
        val incoming = request.tasks

        if (open == null) {
            return createReportWithTasks(account, incoming)
        }

        val existingResults = open.tasks.mapNotNull { it.result?.trim()?.lowercase() }.filter { it.isNotEmpty() }.toSet()
        val toAdd = incoming.filter { task ->
            val result = task.result?.trim()?.lowercase().orEmpty()
            result.isEmpty() || result !in existingResults
        }

        if (toAdd.isEmpty()) {
            log.info("All task results already present on report {} — skipping append", open.id)
            return open
        }

        if (open.tasks.size + toAdd.size > MAX_TASKS_PER_REPORT) {
            log.info(
                "Open report {} has {} tasks; adding {} would exceed {} — creating new report",
                open.id,
                open.tasks.size,
                toAdd.size,
                MAX_TASKS_PER_REPORT,
            )
            return createReportWithTasks(account, incoming)
        }

        val customer = resolveCleanCityCustomer(account)
        val newTasks = toAdd.map { buildTask(it, open, customer) }
        open.tasks.addAll(newTasks)
        open.submittedAt = OffsetDateTime.now()
        return reportRepository.save(open)
    }

    private fun createReportWithTasks(account: Account, taskRequests: List<ExtCreateTaskRequest>): Report {
        if (taskRequests.isEmpty()) throw InvalidRequestException("tasks must not be empty")
        val now = OffsetDateTime.now()
        val program = programRepository.findByCode(CLEAN_CITY_PROGRAM) ?: account.info?.program
        val project = projectRepository.findByCode(CLEAN_CITY_PROJECT)?.takeIf {
            program == null || it.program.code.equals(program.code, ignoreCase = true)
        } ?: account.info?.project
        val customer = resolveCleanCityCustomer(account)

        val report = Report(
            isAuto = true,
            account = account,
            status = ReportStatus.CREATED,
            createTime = now,
            submittedAt = now,
            program = program,
            project = project,
        )
        val tasks = taskRequests.map { buildTask(it, report, customer) }
        return reportRepository.save(report.also { it.tasks = tasks.toMutableSet() })
    }

    private fun findOpenAutoReport(account: Account, reportId: UUID?): Report? {
        if (reportId != null) {
            val report = reportRepository.findById(reportId).orElse(null) ?: return null
            if (!report.isAuto) return null
            if (report.status != ReportStatus.CREATED) return null
            if (!report.account.username.equals(account.username, ignoreCase = true)) {
                throw InvalidRequestException("reportId does not belong to user")
            }
            return report
        }

        return reportRepository
            .findTopByAccountUsernameAndStatusAndIsAutoTrueAndProgramCodeIgnoreCaseOrderByCreateTimeDesc(
                account.username,
                ReportStatus.CREATED,
                CLEAN_CITY_PROGRAM,
            )
            ?: reportRepository.findTopByAccountUsernameAndStatusAndIsAutoTrueOrderByCreateTimeDesc(
                account.username,
                ReportStatus.CREATED,
            )
    }

    private fun buildTask(req: ExtCreateTaskRequest, report: Report, customer: Account?): Task {
        if (req.date.isAfter(LocalDate.now())) {
            throw InvalidRequestException("Task date (${req.date}) must be in the past")
        }
        if (req.name.isBlank() || req.name.length < 10) {
            throw InvalidRequestException("Task name must be at least 10 characters")
        }
        if (req.description.isBlank() || req.description.length < 10) {
            throw InvalidRequestException("Task description must be at least 10 characters")
        }
        if (req.timeSpent < 60 || req.timeSpent > 600) {
            throw InvalidRequestException("Task timeSpent must be between 60 and 600")
        }

        val task = Task(
            date = req.date,
            name = req.name.trim(),
            description = req.description.trim(),
            timeSpent = req.timeSpent,
            result = req.result?.trim()?.takeIf { it.isNotEmpty() },
            nameSr = req.nameSr?.trim()?.takeIf { it.isNotEmpty() },
            descriptionSr = req.descriptionSr?.trim()?.takeIf { it.isNotEmpty() },
            report = report,
            customer = customer,
        )
        if (task.nameSr.isNullOrBlank()) {
            task.nameSr = textTranslationService.translate(task.name, SERBIAN_TRANSLATOR)
        }
        if (task.descriptionSr.isNullOrBlank()) {
            task.descriptionSr = textTranslationService.translate(task.description, SERBIAN_TRANSLATOR)
        }
        return task
    }

    /** Заказчик = куратор программы Урбанизм (Чистый город), не автор отчёта. */
    private fun resolveCleanCityCustomer(author: Account): Account? {
        val curators = programCuratorRepository.findAllByProgramCodeIgnoreCase(CLEAN_CITY_PROGRAM)
        if (curators.isEmpty()) {
            log.warn("No program curator for {} — external report created without customer", CLEAN_CITY_PROGRAM)
            return null
        }
        val login = curators
            .map { it.username }
            .sortedBy { it.lowercase() }
            .firstOrNull { !it.equals(author.username, ignoreCase = true) }
            ?: curators.minByOrNull { it.username.lowercase() }?.username
            ?: return null

        val customer = accountService.findAccountByLogin(login)
        if (customer == null) {
            log.warn("Clean City curator login={} not found in accounts", login)
        }
        return customer
    }

    companion object {
        const val CLEAN_CITY_PROGRAM = "URBANISM"
        const val CLEAN_CITY_PROJECT = "CLEAN_CITY"
        const val MAX_TASKS_PER_REPORT = 10
    }
}
