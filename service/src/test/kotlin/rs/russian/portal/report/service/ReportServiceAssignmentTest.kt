package rs.russian.portal.report.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import rs.russian.generated.model.ReportDto
import rs.russian.generated.model.TaskDto
import rs.russian.portal.config.DefaultUserFilter
import rs.russian.portal.config.DefaultUserFilter.Companion.USERNAME
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.report.repository.ReportRepository
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.testconfig.AbstractIntegrationTest
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class ReportServiceAssignmentTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var reportService: ReportService

    @Autowired
    lateinit var reportRepository: ReportRepository

    @Autowired
    lateinit var accountService: AccountService

    @Autowired
    lateinit var defaultUserFilter: DefaultUserFilter

    @BeforeEach
    fun setup() {
        reportRepository.deleteAll()
        SecurityContextHolder.getContext().authentication = defaultUserFilter.getDefaultOAuth2Token()
    }

    @Test
    fun `updateReport takes program from the profile when the snapshot went stale`() {
        val report = createReportWithProgram("MEDIA")
        setProgram("IT")

        val updated = reportService.updateReport(reportDto(report.id!!, "Edited task"))

        assertEquals("IT", updated.program?.code)
    }

    @Test
    fun `updateReport keeps the program chosen by the moderator`() {
        val report = createReportWithProgram("MEDIA")
        setProgram("MEDIA")

        val updated = reportService.updateReport(
            reportDto(report.id!!, "Edited task").apply { program = "IT" }
        )

        assertEquals("IT", updated.program?.code)
    }

    @Test
    fun `updateAssignment fixes the program of an accepted report without reopening it`() {
        val report = createReportWithProgram("MEDIA")
        reportService.changeStatus(report.id!!, ReportStatus.ACCEPTED)

        val updated = reportService.updateAssignment(report.id!!, "IT", "FORMS")

        assertEquals("IT", updated.program?.code)
        assertEquals("FORMS", updated.project?.code)
        assertEquals(ReportStatus.ACCEPTED, updated.status)
    }

    @Test
    fun `updateAssignment clears the program together with the project`() {
        val report = createReportWithProgram("IT")

        val updated = reportService.updateAssignment(report.id!!, null, null)

        assertNull(updated.program)
        assertNull(updated.project)
    }

    @Test
    fun `updateAssignment rejects an unknown program`() {
        val report = createReportWithProgram("IT")

        assertFailsWith<InvalidRequestException> {
            reportService.updateAssignment(report.id!!, "NO_SUCH_PROGRAM", null)
        }
    }

    private fun createReportWithProgram(programCode: String) =
        setProgram(programCode).let { reportService.createReport(reportDto(UUID.randomUUID(), "Initial task")) }

    private fun setProgram(programCode: String) {
        val account = accountService.findAccountByLogin(USERNAME)!!
        accountService.setProgram(account.id!!, programCode)
    }

    private fun reportDto(id: UUID, taskName: String) = ReportDto(
        id = id,
        tasks = mutableListOf(
            TaskDto(
                id = UUID.randomUUID(),
                date = LocalDate.now(),
                name = taskName,
                description = "Test description for $taskName",
                timeSpent = 600,
                customer = USERNAME,
            )
        )
    )
}
