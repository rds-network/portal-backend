package rs.russian.portal.report.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import rs.russian.generated.model.ReportDto
import rs.russian.generated.model.TaskDto
import rs.russian.portal.config.DefaultUserFilter
import rs.russian.portal.program.domain.ProgramCurator
import rs.russian.portal.program.repository.ProgramCuratorRepository
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.testconfig.AbstractIntegrationTest
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class ReportServiceCreateTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var reportService: ReportService

    @Autowired
    lateinit var accountService: AccountService

    @Autowired
    lateinit var programCuratorRepository: ProgramCuratorRepository

    @Autowired
    lateinit var defaultUserFilter: DefaultUserFilter

    private lateinit var customerLogin: String

    @BeforeEach
    fun setup() {
        SecurityContextHolder.getContext().authentication = defaultUserFilter.getDefaultOAuth2Token()
        customerLogin = ensureApprover()
    }

    @Test
    fun `createReport should autopopulate program when user has program assigned`() {
        val account = accountService.findAccountByLogin(DefaultUserFilter.USERNAME)!!
        accountService.setProgram(account.id!!, "IT")

        val reportDto = createTestReportDto("IT Test Task")
        val createdReport = reportService.createReport(reportDto)

        assertNotNull(createdReport.program)
        assertEquals("IT", createdReport.program?.code)
    }

    @Test
    fun `createReport should autopopulate project when user has project assigned`() {
        val account = accountService.findAccountByLogin(DefaultUserFilter.USERNAME)!!
        accountService.setProject(account.id!!, "LAYOUT")

        val reportDto = createTestReportDto("Layout Test Task")
        val createdReport = reportService.createReport(reportDto)

        assertNotNull(createdReport.project)
        assertEquals("LAYOUT", createdReport.project?.code)
    }

    @Test
    fun `createReport should autopopulate both program and project when user has both assigned`() {
        val account = accountService.findAccountByLogin(DefaultUserFilter.USERNAME)!!
        accountService.setProgram(account.id!!, "IT")
        accountService.setProject(account.id!!, "FORMS")

        val reportDto = createTestReportDto("IT Forms Test Task")
        val createdReport = reportService.createReport(reportDto)

        assertNotNull(createdReport.program)
        assertEquals("IT", createdReport.program?.code)
        assertNotNull(createdReport.project)
        assertEquals("FORMS", createdReport.project?.code)
    }

    @Test
    fun `createReport should handle null program and project when user has none assigned`() {
        val reportDto = createTestReportDto("No Assignment Test Task")
        val createdReport = reportService.createReport(reportDto)

        assertNotNull(createdReport)
        assertNotNull(createdReport.tasks)
        assertEquals(1, createdReport.tasks.size)
    }

    @Test
    fun `createReport should preserve user assignments at creation time`() {
        val account = accountService.findAccountByLogin(DefaultUserFilter.USERNAME)!!
        accountService.setProgram(account.id!!, "MEDIA")

        val reportDto1 = createTestReportDto("Media Test Task")
        val createdReport1 = reportService.createReport(reportDto1)

        accountService.setProgram(account.id!!, "IT")

        val reportDto2 = createTestReportDto("IT Test Task")
        val createdReport2 = reportService.createReport(reportDto2)

        assertEquals("MEDIA", createdReport1.program?.code)
        assertEquals("IT", createdReport2.program?.code)
    }

    @Test
    fun `createReport rejects self as customer`() {
        val dto = createTestReportDto("Self customer").also { report ->
            report.tasks[0].customer = DefaultUserFilter.USERNAME
        }
        assertFailsWith<InvalidRequestException> {
            reportService.createReport(dto)
        }
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

    private fun createTestReportDto(taskName: String): ReportDto {
        return ReportDto(
            id = UUID.randomUUID(),
            tasks = mutableListOf(
                TaskDto(
                    id = UUID.randomUUID(),
                    date = LocalDate.now(),
                    name = taskName,
                    description = "Test description for $taskName",
                    timeSpent = 60,
                    result = "Test result",
                    customer = customerLogin,
                )
            )
        )
    }
}
