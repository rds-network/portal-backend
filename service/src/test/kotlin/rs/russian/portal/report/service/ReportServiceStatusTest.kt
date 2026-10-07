package rs.russian.portal.report.service

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import rs.russian.generated.model.ReportDto
import rs.russian.generated.model.TaskDto
import rs.russian.portal.config.DefaultUserFilter
import rs.russian.portal.config.DefaultUserFilter.Companion.USERNAME
import rs.russian.portal.program.domain.ProgramCurator
import rs.russian.portal.program.repository.ProgramCuratorRepository
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.report.repository.ReportCustomerDecisionRepository
import rs.russian.portal.report.repository.ReportRepository
import rs.russian.portal.testconfig.AbstractIntegrationTest
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportServiceStatusTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var reportService: ReportService

    @Autowired
    lateinit var reportRepository: ReportRepository

    @Autowired
    lateinit var reportCustomerDecisionRepository: ReportCustomerDecisionRepository

    @Autowired
    lateinit var accountService: AccountService

    @Autowired
    lateinit var programCuratorRepository: ProgramCuratorRepository

    @Autowired
    lateinit var defaultUserFilter: DefaultUserFilter

    private lateinit var reportId: UUID

    @BeforeAll
    fun setup() {
        reportCustomerDecisionRepository.deleteAll()
        reportRepository.deleteAll()

        SecurityContextHolder.getContext().authentication = defaultUserFilter.getDefaultOAuth2Token()
        val customer = ensureApprover()

        val report = reportService.createReport(
            ReportDto(
                id = UUID.randomUUID(),
                tasks = mutableListOf(
                    TaskDto(
                        id = UUID.randomUUID(),
                        date = LocalDate.now(),
                        name = "IT Test Task",
                        description = "Test description for IT Test Task",
                        timeSpent = 600,
                        customer = customer,
                    )
                )
            )
        )

        reportId = report.id!!
    }

    @Test
    fun `moderator should be assigned when report status is changed`() {
        reportService.changeStatus(reportId, ReportStatus.CREATED)

        val updatedReport = reportService.getReport(reportId)
        assert(updatedReport.moderator != null)
        assert(updatedReport.moderator!!.username == USERNAME)
    }

    private fun ensureApprover(): String {
        val login = "report_approver"
        if (accountService.findAccountByLogin(login) == null) {
            accountService.save(
                Account(
                    id = 91001,
                    username = login,
                    email = "report_approver@example.com",
                    fullName = "Report Approver",
                    groups = setOf(UserGroup.VOLUNTEER),
                )
            )
        }
        if (!programCuratorRepository.existsByUsernameIgnoreCase(login)) {
            programCuratorRepository.save(ProgramCurator(programCode = "IT", username = login))
        }
        return login
    }
}
