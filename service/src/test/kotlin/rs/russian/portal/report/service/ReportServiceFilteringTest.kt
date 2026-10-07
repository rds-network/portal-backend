package rs.russian.portal.report.service

import org.junit.jupiter.api.BeforeEach
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class ReportServiceFilteringTest : AbstractIntegrationTest() {

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

    @BeforeEach
    fun setup() {
        SecurityContextHolder.getContext().authentication = defaultUserFilter.getDefaultOAuth2Token()

        val account = accountService.findAccountByLogin(DefaultUserFilter.USERNAME)!!
        accountLogin = account.username

        reportCustomerDecisionRepository.deleteAll()
        reportRepository.deleteAll()
    }

    @Test
    fun `getReports should filter by program correctly`() {
        // Given: Create reports with different programs
        createReportWithProgram("IT", "IT Report")
        createReportWithProgram("MEDIA", "Media Report")
        createReportWithoutProgramProject("No Program Report")

        // When: Filter by IT program
        val filter = ReportFilter().apply {
            program = "IT"
            login = accountLogin
        }
        val results = reportService.getReports(filter, PageRequest.of(0, 10))

        // Then: Only IT report should be found
        assertEquals(1, results.totalElements)
        assertEquals("IT Report", results.content.first().tasks.first().name)
    }

    @Test
    fun `getReports should filter by project correctly`() {
        // Given: Create reports with different projects
        createReportWithProject("LAYOUT", "Layout Report")
        createReportWithProject("FORMS", "Forms Report")
        createReportWithoutProgramProject("No Project Report")

        // When: Filter by LAYOUT project
        val filter = ReportFilter().apply {
            project = "LAYOUT"
            login = accountLogin
        }
        val results = reportService.getReports(filter, PageRequest.of(0, 10))

        // Then: Only Layout report should be found
        assertEquals(1, results.totalElements)
        assertEquals("Layout Report", results.content.first().tasks.first().name)
    }

    @Test
    fun `getReports should filter by empty string program to find reports without program`() {
        // Given: Create reports with and without program
        createReportWithProgram("IT", "With Program")
        createReportWithoutProgramProject("Without Program 1")
        createReportWithoutProgramProject("Without Program 2")

        // When: Filter by empty string (should find reports without program)
        val filter = ReportFilter().apply {
            program = ""
            login = accountLogin
        }
        val results = reportService.getReports(filter, PageRequest.of(0, 10))

        // Then: Only reports without program should be found
        assertEquals(2, results.totalElements)
        assertTrue(results.content.all { it.tasks.first().name.contains("Without Program") })
    }

    @Test
    fun `getReports should filter by empty string project to find reports without project`() {
        // Given: Create reports with and without project
        createReportWithProject("LAYOUT", "With Project")
        createReportWithoutProgramProject("Without Project 1")
        createReportWithoutProgramProject("Without Project 2")

        // When: Filter by empty string (should find reports without project)
        val filter = ReportFilter().apply {
            project = ""
            login = accountLogin
        }
        val results = reportService.getReports(filter, PageRequest.of(0, 10))

        // Then: Only reports without project should be found
        assertEquals(2, results.totalElements)
        assertTrue(results.content.all { it.tasks.first().name.contains("Without Project") })
    }

    @Test
    fun `getReports should combine program and project filters correctly`() {
        // Given: Create reports with different program/project combinations
        // Note: Projects belong to specific programs (LAYOUT/FORMS belong to IT, ARTICLES belongs to MEDIA)
        // setProject() also sets the program to the project's parent program
        createReportWithProgramAndProject("IT", "LAYOUT", "IT+Layout")
        createReportWithProgramAndProject("IT", "FORMS", "IT+Forms")
        createReportWithProgramAndProject("MEDIA", "ARTICLES", "Media+Articles")
        createReportWithoutProgramProject("No Program/Project")

        // When: Filter by both IT program and LAYOUT project
        val filter = ReportFilter().apply {
            program = "IT"
            project = "LAYOUT"
            login = accountLogin
        }
        val results = reportService.getReports(filter, PageRequest.of(0, 10))

        // Then: Only IT+Layout report should be found
        assertEquals(1, results.totalElements)
        assertEquals("IT+Layout", results.content.first().tasks.first().name)
    }

    @Test
    fun `getReports should return empty results when no reports match filter criteria`() {
        // Given: Create reports with specific programs
        createReportWithProgram("IT", "IT Report")

        // When: Filter by non-existent program
        val filter = ReportFilter().apply {
            program = "NONEXISTENT"
            login = accountLogin
        }
        val results = reportService.getReports(filter, PageRequest.of(0, 10))

        // Then: No reports should be found
        assertEquals(0, results.totalElements)
        assertTrue(results.content.isEmpty())
    }

    @Test
    fun `getReports should work with login filter combined with program filter`() {
        // Given: Create report for current user with IT program
        createReportWithProgram("IT", "IT Report")

        // When: Filter by both login and program
        val filter = ReportFilter().apply {
            program = "IT"
            login = accountLogin
        }
        val results = reportService.getReports(filter, PageRequest.of(0, 10))

        // Then: Report should be found
        assertEquals(1, results.totalElements)
        assertEquals("IT Report", results.content.first().tasks.first().name)
        assertEquals(accountLogin, results.content.first().account.username)
    }

    private fun createReportWithProgram(programCode: String, taskName: String) {
        val account = accountService.findAccountByLogin(DefaultUserFilter.USERNAME)!!
        accountService.setProgram(account.id!!, programCode)
        val updatedAccount = accountService.findAccountByLogin(DefaultUserFilter.USERNAME)!!

        val report = Report(
            account = updatedAccount,
            status = ReportStatus.CREATED,
            program = updatedAccount.info?.program,
            project = null
        )
        addTaskToReport(report, taskName)
        reportService.save(report)
    }

    private fun createReportWithProject(projectCode: String, taskName: String) {
        val account = accountService.findAccountByLogin(DefaultUserFilter.USERNAME)!!
        accountService.setProject(account.id!!, projectCode)
        val updatedAccount = accountService.findAccountByLogin(DefaultUserFilter.USERNAME)!!

        val report = Report(
            account = updatedAccount,
            status = ReportStatus.CREATED,
            program = null,
            project = updatedAccount.info?.project
        )
        addTaskToReport(report, taskName)
        reportService.save(report)
    }

    private fun createReportWithProgramAndProject(programCode: String, projectCode: String, taskName: String) {
        val account = accountService.findAccountByLogin(DefaultUserFilter.USERNAME)!!
        accountService.setProgram(account.id!!, programCode)
        accountService.setProject(account.id!!, projectCode)
        val updatedAccount = accountService.findAccountByLogin(DefaultUserFilter.USERNAME)!!

        val report = Report(
            account = updatedAccount,
            status = ReportStatus.CREATED,
            program = updatedAccount.info?.program,
            project = updatedAccount.info?.project
        )
        addTaskToReport(report, taskName)
        reportService.save(report)
    }

    private fun createReportWithoutProgramProject(taskName: String) {
        val account = accountService.findAccountByLogin(DefaultUserFilter.USERNAME)!!

        val report = Report(
            account = account,
            status = ReportStatus.CREATED,
            program = null,
            project = null
        )
        addTaskToReport(report, taskName)
        reportService.save(report)
    }

    private fun addTaskToReport(report: Report, taskName: String) {
        report.tasks.add(
            Task(
                date = LocalDate.now(),
                name = taskName,
                description = "Test task for $taskName",
                timeSpent = 60,
                report = report
            )
        )
    }
}
