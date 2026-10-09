package rs.russian.portal.report.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.generated.model.CreateReportRequest
import rs.russian.portal.program.repository.ProgramCuratorRepository
import rs.russian.portal.program.repository.ProgramRepository
import rs.russian.portal.program.repository.ProjectRepository
import rs.russian.portal.report.domain.Report
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.report.mapper.ReportMapper
import rs.russian.portal.report.repository.ReportRepository
import rs.russian.portal.shared.ai.domain.AiProfileCode.SERBIAN_TRANSLATOR
import rs.russian.portal.shared.ai.service.TextTranslationService
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate
import java.time.OffsetDateTime

@Service
class ReportExternalService(
    private val reportMapper: ReportMapper,
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
        // username / email / EVO-{ekomapaUserId} — как при линке EVO
        val account = accountService.resolveAccountForExternalApi(request.user)
        val now = OffsetDateTime.now()
        // Ekomapa Clean City → программа Урбанизм / проект Чистый город
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
        val tasks = request.tasks.map { createTaskRequest ->
            if (createTaskRequest.date.isAfter(LocalDate.now())) {
                throw InvalidRequestException("Task date (${createTaskRequest.date}) must be in the past")
            }
            reportMapper.map(createTaskRequest, report).also { task ->
                task.customer = customer
                if (task.nameSr.isNullOrBlank()) {
                    task.nameSr = textTranslationService.translate(task.name, SERBIAN_TRANSLATOR)
                }
                if (task.descriptionSr.isNullOrBlank()) {
                    task.descriptionSr = textTranslationService.translate(task.description, SERBIAN_TRANSLATOR)
                }
            }
        }
        return reportRepository.save(report.also { it.tasks = tasks.toMutableSet() })
    }

    /**
     * Заказчик = куратор программы Урбанизм (Чистый город), не автор отчёта.
     */
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
    }
}
