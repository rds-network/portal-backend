package rs.russian.portal.report.service

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.security.core.context.SecurityContextHolder
import rs.russian.generated.model.ReportFilter
import rs.russian.portal.config.DefaultUserFilter
import rs.russian.portal.report.domain.Report
import rs.russian.portal.report.domain.Task
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.report.repository.ReportCustomerDecisionRepository
import rs.russian.portal.report.repository.ReportRepository
import rs.russian.portal.testconfig.AbstractIntegrationTest
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportServiceTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var reportService: ReportService

    @Autowired
    lateinit var reportRepository: ReportRepository

    @Autowired
    lateinit var reportCustomerDecisionRepository: ReportCustomerDecisionRepository

    @Autowired
    lateinit var accountService: AccountService

    @Autowired
    lateinit var defaultUserFilter: DefaultUserFilter

    private lateinit var accountLogin: String

    @BeforeAll
    fun setup() {
        reportCustomerDecisionRepository.deleteAll()
        reportRepository.deleteAll()

        SecurityContextHolder.getContext().authentication =
            defaultUserFilter.getDefaultOAuth2Token()

        var account = accountService.findAccountByLogin(DefaultUserFilter.USERNAME)
            ?: throw IllegalStateException("Default user not created")
        accountLogin = account.username

        account = accountService.setProgram(account.id!!, "IT")

        val report = Report(
            account = account,
            status = ReportStatus.CREATED,
            program = account.info?.program,
            project = account.info?.project
        )
        report.tasks.add(
            Task(
                date = LocalDate.now(),
                name = "Test task 1",
                description = "First",
                timeSpent = 10,
                report = report
            )
        )
        report.tasks.add(
            Task(
                date = LocalDate.now(),
                name = "Test task 2",
                description = "Second",
                timeSpent = 20,
                report = report
            )
        )
        reportService.save(report)
    }

    @Test
    fun `service returns correct page of ReportDto`() {
        val filter = ReportFilter().apply {
            program = "IT"
            login = accountLogin
        }
        val page = reportService.getReports(filter, PageRequest.of(0, 10))

        assert(page.totalElements == 1L)
        val dto = page.content.first()
        assert(dto.tasks.size == 2)
        assert(dto.status.name == "CREATED")
        assert(dto.moderator == null)
    }
}
