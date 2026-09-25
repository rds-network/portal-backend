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
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.report.repository.ReportRepository
import rs.russian.portal.testconfig.AbstractIntegrationTest
import java.time.LocalDate
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportServiceStatusTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var reportService: ReportService

    @Autowired
    lateinit var reportRepository: ReportRepository

    @Autowired
    lateinit var defaultUserFilter: DefaultUserFilter

    private lateinit var reportId: UUID

    @BeforeAll
    fun setup() {
        reportRepository.deleteAll()

        SecurityContextHolder.getContext().authentication = defaultUserFilter.getDefaultOAuth2Token()

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
                        customer = USERNAME,
                    )
                )
            )
        )

        reportId = report.id!!
    }

    @Test
    fun `moderator should be assigned when report status is changed`() {
        // act
        reportService.changeStatus(reportId, ReportStatus.CREATED)

        // assert
        val updatedReport = reportService.getReport(reportId)
        assert(updatedReport.moderator != null)
        assert(updatedReport.moderator!!.username == USERNAME)
    }
}
