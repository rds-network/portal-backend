package rs.russian.portal.report.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.generated.model.CreateReportRequest
import rs.russian.portal.report.domain.Report
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.report.mapper.ReportMapper
import rs.russian.portal.report.repository.ReportRepository
import rs.russian.portal.shared.ai.domain.AiProfileCode.SERBIAN_TRANSLATOR
import rs.russian.portal.shared.ai.service.TextTranslationService
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate
import java.time.OffsetDateTime

@Service
class ReportExternalService(
    private val reportMapper: ReportMapper,
    private val accountService: AccountService,
    private val reportRepository: ReportRepository,
    private val textTranslationService: TextTranslationService,
) {

    @Transactional
    fun createReport(request: CreateReportRequest): Report {
        // username / email / EVO-{ekomapaUserId} — как при линке EVO
        val account = accountService.resolveAccountForExternalApi(request.user)
        val now = OffsetDateTime.now()
        val report = Report(
            isAuto = true,
            account = account,
            status = ReportStatus.CREATED,
            createTime = now,
            submittedAt = now,
            program = account.info?.program,
            project = account.info?.project
        )
        val tasks = request.tasks.map { createTaskRequest ->
            if (createTaskRequest.date.isAfter(LocalDate.now())) {
                throw InvalidRequestException("Task date (${createTaskRequest.date}) must be in the past")
            }
            reportMapper.map(createTaskRequest, report).also { task ->
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
}

