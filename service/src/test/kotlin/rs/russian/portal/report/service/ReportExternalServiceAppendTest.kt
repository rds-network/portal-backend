package rs.russian.portal.report.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import rs.russian.portal.config.DefaultUserFilter
import rs.russian.portal.program.domain.ProgramCurator
import rs.russian.portal.program.repository.ProgramCuratorRepository
import rs.russian.portal.report.api.ExtAppendReportTasksRequest
import rs.russian.portal.report.api.ExtCreateTaskRequest
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.testconfig.AbstractIntegrationTest
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class ReportExternalServiceAppendTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var reportExternalService: ReportExternalService

    @Autowired
    lateinit var accountService: AccountService

    @Autowired
    lateinit var programCuratorRepository: ProgramCuratorRepository

    @Autowired
    lateinit var defaultUserFilter: DefaultUserFilter

    @BeforeEach
    fun setup() {
        SecurityContextHolder.getContext().authentication = defaultUserFilter.getDefaultOAuth2Token()
        ensureUrbanismCurator()
    }

    @Test
    fun `appendOrCreate creates report when none open`() {
        val login = DefaultUserFilter.USERNAME
        val report = reportExternalService.appendOrCreate(
            ExtAppendReportTasksRequest(
                user = login,
                tasks = listOf(task("First cleanup task name")),
            ),
        )

        assertNotNull(report.id)
        assertTrue(report.isAuto)
        assertEquals(ReportStatus.CREATED, report.status)
        assertEquals(1, report.tasks.size)
    }

    @Test
    fun `appendOrCreate appends to existing CREATED auto report`() {
        val login = DefaultUserFilter.USERNAME
        val first = reportExternalService.appendOrCreate(
            ExtAppendReportTasksRequest(
                user = login,
                tasks = listOf(task("First cleanup task name", result = "https://ekomapa.rs/map?route=1")),
            ),
        )
        val second = reportExternalService.appendOrCreate(
            ExtAppendReportTasksRequest(
                user = login,
                tasks = listOf(task("Second cleanup task name", result = "https://ekomapa.rs/map?route=2")),
            ),
        )

        assertEquals(first.id, second.id)
        assertEquals(2, second.tasks.size)
    }

    @Test
    fun `appendOrCreate skips duplicate result URL`() {
        val login = DefaultUserFilter.USERNAME
        val url = "https://ekomapa.rs/map?route=dup"
        val first = reportExternalService.appendOrCreate(
            ExtAppendReportTasksRequest(
                user = login,
                tasks = listOf(task("First cleanup task name", result = url)),
            ),
        )
        val second = reportExternalService.appendOrCreate(
            ExtAppendReportTasksRequest(
                user = login,
                tasks = listOf(task("Duplicate cleanup task", result = url)),
            ),
        )

        assertEquals(first.id, second.id)
        assertEquals(1, second.tasks.size)
    }

    @Test
    fun `appendOrCreate opens new report when existing has 10 tasks`() {
        val login = DefaultUserFilter.USERNAME
        val first = reportExternalService.appendOrCreate(
            ExtAppendReportTasksRequest(
                user = login,
                tasks = (1..10).map { task("Cleanup task number $it xx", result = "https://ekomapa.rs/map?route=$it") },
            ),
        )
        assertEquals(10, first.tasks.size)

        val second = reportExternalService.appendOrCreate(
            ExtAppendReportTasksRequest(
                user = login,
                tasks = listOf(task("Eleventh cleanup task", result = "https://ekomapa.rs/map?route=11")),
            ),
        )

        assertNotEquals(first.id, second.id)
        assertEquals(1, second.tasks.size)
    }

    private fun ensureUrbanismCurator() {
        val login = "urbanism_curator_ext"
        if (accountService.findAccountByLogin(login) == null) {
            accountService.save(
                Account(
                    id = 92001,
                    username = login,
                    email = "urbanism_curator_ext@example.com",
                    fullName = "Urbanism Curator",
                    groups = setOf(UserGroup.VOLUNTEER),
                ),
            )
        }
        if (!programCuratorRepository.existsByProgramCodeAndUsernameIgnoreCase("URBANISM", login)) {
            programCuratorRepository.save(ProgramCurator(programCode = "URBANISM", username = login))
        }
    }

    private fun task(name: String, result: String? = null) = ExtCreateTaskRequest(
        date = LocalDate.now().minusDays(1),
        name = name.padEnd(10, 'x'),
        description = "Description long enough for portal validation rules",
        timeSpent = 120,
        result = result,
        nameSr = "Srpski naziv zadatka dovoljno dug",
        descriptionSr = "Srpski opis zadatka dovoljno dug za validaciju",
    )
}
