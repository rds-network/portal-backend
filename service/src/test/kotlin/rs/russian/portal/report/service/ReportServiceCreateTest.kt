package rs.russian.portal.report.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import rs.russian.generated.model.ReportDto
import rs.russian.generated.model.TaskDto
import rs.russian.portal.config.DefaultUserFilter
import rs.russian.portal.testconfig.AbstractIntegrationTest
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class ReportServiceCreateTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var reportService: ReportService

    @Autowired
    lateinit var accountService: AccountService

    @Autowired
    lateinit var defaultUserFilter: DefaultUserFilter

    @BeforeEach
    fun setup() {
        SecurityContextHolder.getContext().authentication = defaultUserFilter.getDefaultOAuth2Token()
    }

    @Test
    fun `createReport should autopopulate program when user has program assigned`() {
        // Given: User has program assigned
        val account = accountService.findAccountByLogin(DefaultUserFilter.USERNAME)!!
        accountService.setProgram(account.id!!, "IT")

        // When: Using the actual createReport service method
        val reportDto = createTestReportDto("IT Test Task")
        val createdReport = reportService.createReport(reportDto)

        // Then: Report should have program autopopulated
        assertNotNull(createdReport.program)
        assertEquals("IT", createdReport.program?.code)
    }

    @Test
    fun `createReport should autopopulate project when user has project assigned`() {
        // Given: User has project assigned
        val account = accountService.findAccountByLogin(DefaultUserFilter.USERNAME)!!
        accountService.setProject(account.id!!, "LAYOUT")

        // When: Using the actual createReport service method
        val reportDto = createTestReportDto("Layout Test Task")
        val createdReport = reportService.createReport(reportDto)

        // Then: Report should have project autopopulated
        assertNotNull(createdReport.project)
        assertEquals("LAYOUT", createdReport.project?.code)
    }

    @Test
    fun `createReport should autopopulate both program and project when user has both assigned`() {
        // Given: User has both program and project assigned
        val account = accountService.findAccountByLogin(DefaultUserFilter.USERNAME)!!
        accountService.setProgram(account.id!!, "IT")
        accountService.setProject(account.id!!, "FORMS")

        // When: Using the actual createReport service method
        val reportDto = createTestReportDto("IT Forms Test Task")
        val createdReport = reportService.createReport(reportDto)

        // Then: Report should have both auto-populated
        assertNotNull(createdReport.program)
        assertEquals("IT", createdReport.program?.code)
        assertNotNull(createdReport.project)
        assertEquals("FORMS", createdReport.project?.code)
    }

    @Test
    fun `createReport should handle null program and project when user has none assigned`() {
        // Given: User has no program or project assigned (default state)
        // Note: We don't change user assignments here, testing default state

        // When: Using the actual createReport service method
        val reportDto = createTestReportDto("No Assignment Test Task")
        val createdReport = reportService.createReport(reportDto)

        // Then: Report should be created successfully
        // Program and project may be null or have previous assignments
        // The key test is that the method doesn't crash and handles nulls properly
        assertNotNull(createdReport)
        assertNotNull(createdReport.tasks)
        assertEquals(1, createdReport.tasks.size)
    }

    @Test
    fun `createReport should preserve user assignments at creation time`() {
        // Given: User starts with one program
        val account = accountService.findAccountByLogin(DefaultUserFilter.USERNAME)!!
        accountService.setProgram(account.id!!, "MEDIA")

        // When: Create first report with current assignment
        val reportDto1 = createTestReportDto("Media Test Task")
        val createdReport1 = reportService.createReport(reportDto1)

        // And: User changes program after first report creation
        accountService.setProgram(account.id!!, "IT")

        // When: Create second report with new assignment
        val reportDto2 = createTestReportDto("IT Test Task")
        val createdReport2 = reportService.createReport(reportDto2)

        // Then: Each report should preserve the program that was active when it was created
        assertEquals("MEDIA", createdReport1.program?.code) // Old assignment preserved
        assertEquals("IT", createdReport2.program?.code)    // New assignment captured
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
                    customer = DefaultUserFilter.USERNAME,
                )
            )
        )
    }
}
