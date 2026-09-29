package rs.russian.portal.report.service

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionalEventListener
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import rs.russian.portal.mail.service.EmailService
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.report.event.ReportUpdatedEvent
import java.time.format.DateTimeFormatter

@Component
class ReportEventListener(
    private val emailService: EmailService,
    private val reportService: ReportService,
    private val templateEngine: TemplateEngine
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(fallbackExecution = true)
    fun handleReportRejectedEvent(event: ReportUpdatedEvent) {
        val report = reportService.getReport(event.id)
        if (report.status == ReportStatus.REJECTED) {
            val remark = report.notes
                .maxByOrNull { it.createTime }
                ?.text
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            val message = templateEngine.process("report_rejected",
                Context().also {
                    it.setVariables(
                        mapOf(
                            "id" to report.id,
                            "date" to report.createTime.toLocalDate().format(DateTimeFormatter.ISO_DATE),
                            "remark" to remark,
                        )
                    )
                })
            emailService.sendCommonEmail(report.account, "Ваш отчет не принят", message)
        }
    }
}
