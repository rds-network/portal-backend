package rs.russian.portal.report.service

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import rs.russian.generated.model.ReportDto
import rs.russian.generated.model.TaskDto
import rs.russian.portal.file.service.FileService
import rs.russian.portal.inbox.service.InboxService
import rs.russian.portal.note.service.NoteService
import rs.russian.portal.program.repository.ProgramRepository
import rs.russian.portal.program.repository.ProjectRepository
import rs.russian.portal.program.service.ProgramCuratorService
import rs.russian.portal.report.mapper.ReportMapper
import rs.russian.portal.report.repository.ReportRepository
import rs.russian.portal.shared.ai.service.TextTranslationService
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.service.AccountService
import rs.russian.portal.workassignment.service.WorkAssignmentService
import java.time.LocalDate
import java.util.UUID

class ReportServiceCustomerTest {
    private val accountService = mockk<AccountService>()
    private val programCuratorService = mockk<ProgramCuratorService>()
    private val service = ReportService(
        accountService = accountService,
        fileService = mockk(relaxed = true),
        reportMapper = mockk(relaxed = true),
        noteService = mockk(relaxed = true),
        reportRepository = mockk(relaxed = true),
        entityManager = mockk(relaxed = true),
        textTranslationService = mockk(relaxed = true),
        workAssignmentService = mockk(relaxed = true),
        inboxService = mockk(relaxed = true),
        programCuratorService = programCuratorService,
        programRepository = mockk(relaxed = true),
        projectRepository = mockk(relaxed = true),
    )

    @Test
    fun `createReport rejects author as customer`() {
        val author = Account(
            id = 1,
            username = "volunteer",
            email = "volunteer@example.com",
            fullName = "Volunteer",
            groups = setOf(UserGroup.VOLUNTEER),
        )
        every { accountService.getCurrentAccount() } returns author

        val error = assertThrows<InvalidRequestException> {
            service.createReport(
                ReportDto(
                    id = UUID.randomUUID(),
                    tasks = mutableListOf(
                        TaskDto(
                            id = UUID.randomUUID(),
                            date = LocalDate.now(),
                            name = "Task",
                            description = "Desc",
                            timeSpent = 60,
                            customer = "Volunteer",
                        )
                    ),
                )
            )
        }
        assertTrue(error.message!!.contains("себя заказчиком", ignoreCase = true))
    }

    @Test
    fun `createReport rejects random volunteer as customer when not curator`() {
        val author = Account(
            id = 1,
            username = "volunteer",
            email = "volunteer@example.com",
            fullName = "Volunteer",
            groups = setOf(UserGroup.VOLUNTEER),
        )
        val peer = Account(
            id = 2,
            username = "peer",
            email = "peer@example.com",
            fullName = "Peer",
            groups = setOf(UserGroup.VOLUNTEER),
        )
        every { accountService.getCurrentAccount() } returns author
        every { accountService.findAccountByLogin("peer") } returns peer
        every { programCuratorService.isAllowedCustomer("peer") } returns false

        val error = assertThrows<InvalidRequestException> {
            service.createReport(
                ReportDto(
                    id = UUID.randomUUID(),
                    tasks = mutableListOf(
                        TaskDto(
                            id = UUID.randomUUID(),
                            date = LocalDate.now(),
                            name = "Task",
                            description = "Desc",
                            timeSpent = 60,
                            customer = "peer",
                        )
                    ),
                )
            )
        }
        assertTrue(error.message!!.contains("куратор", ignoreCase = true))
    }
}
