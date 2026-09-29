package rs.russian.portal.report.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import rs.russian.portal.inbox.service.InboxService
import rs.russian.portal.note.domain.Note
import rs.russian.portal.note.service.NoteService
import rs.russian.portal.report.domain.Report
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.report.repository.ReportRepository
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.service.AccountService
import rs.russian.portal.workassignment.service.WorkAssignmentService
import java.util.Optional
import java.util.UUID

class ReportServiceDecisionNotifyTest {

    private val accountService = mockk<AccountService>()
    private val noteService = mockk<NoteService>(relaxed = true)
    private val reportRepository = mockk<ReportRepository>()
    private val workAssignmentService = mockk<WorkAssignmentService>(relaxed = true)
    private val inboxService = mockk<InboxService>(relaxed = true)

    private val service = ReportService(
        accountService = accountService,
        fileService = mockk(relaxed = true),
        reportMapper = mockk(relaxed = true),
        noteService = noteService,
        reportRepository = reportRepository,
        entityManager = mockk(relaxed = true),
        textTranslationService = mockk(relaxed = true),
        workAssignmentService = workAssignmentService,
        inboxService = inboxService,
        programCuratorService = mockk(relaxed = true),
        programRepository = mockk(relaxed = true),
        projectRepository = mockk(relaxed = true),
    )

    private val volunteer = Account(
        id = 1,
        username = "volunteer",
        email = "volunteer@example.com",
        fullName = "Volunteer",
        groups = setOf(UserGroup.VOLUNTEER),
    )
    private val moderator = Account(
        id = 2,
        username = "moderator",
        email = "moderator@example.com",
        fullName = "Moderator",
        groups = setOf(UserGroup.ADMIN_VOLUNTEER),
    )
    private val reportId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        mockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
        every { currentUserLogin() } returns moderator.username
        every { accountService.getAccountByLogin(moderator.username) } returns moderator
        every { noteService.save(any()) } answers { firstArg() }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
    }

    @Test
    fun `changeStatus rejected with note notifies volunteer with remark and ack`() {
        val report = Report(id = reportId, account = volunteer, status = ReportStatus.CREATED)
        every { reportRepository.findById(reportId) } returns Optional.of(report)

        service.changeStatus(reportId, ReportStatus.REJECTED, "Нужны детали по задаче")

        verify {
            inboxService.notifyReportDecision(
                username = "volunteer",
                subject = match { it.startsWith("Отчёт отклонён") },
                body = match {
                    it.contains("Замечание модератора: Нужны детали по задаче") &&
                        it.contains("/report/$reportId")
                },
                createdBy = "moderator",
                needsAck = true,
            )
        }
    }

    @Test
    fun `changeStatus accepted with note notifies volunteer with remark and ack`() {
        val report = Report(id = reportId, account = volunteer, status = ReportStatus.CREATED)
        every { reportRepository.findById(reportId) } returns Optional.of(report)

        service.changeStatus(reportId, ReportStatus.ACCEPTED, "Отличная работа")

        verify {
            inboxService.notifyReportDecision(
                username = "volunteer",
                subject = match { it.startsWith("Отчёт принят") },
                body = match {
                    it.contains("Замечание модератора: Отличная работа") &&
                        it.contains("/report/$reportId")
                },
                createdBy = "moderator",
                needsAck = true,
            )
        }
        verify { noteService.save(any<Note>()) }
    }

    @Test
    fun `changeStatus accepted without note notifies without ack`() {
        val report = Report(id = reportId, account = volunteer, status = ReportStatus.CREATED)
        every { reportRepository.findById(reportId) } returns Optional.of(report)

        service.changeStatus(reportId, ReportStatus.ACCEPTED, null)

        verify {
            inboxService.notifyReportDecision(
                username = "volunteer",
                subject = match { it.startsWith("Отчёт принят") },
                body = match { it.contains("/report/$reportId") && !it.contains("Замечание модератора") },
                createdBy = "moderator",
                needsAck = false,
            )
        }
    }
}
