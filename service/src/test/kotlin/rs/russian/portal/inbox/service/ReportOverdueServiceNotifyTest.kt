package rs.russian.portal.inbox.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import rs.russian.portal.inbox.api.ReportOverdueDto
import rs.russian.portal.inbox.domain.ReportOverdueNotice
import rs.russian.portal.inbox.repository.ReportOverdueJdbc
import rs.russian.portal.inbox.repository.ReportOverdueNoticeRepository
import rs.russian.portal.inbox.repository.WarningCountProjection
import rs.russian.portal.mup.service.MupLetterService
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate

class ReportOverdueServiceNotifyTest {
    private val reportOverdueJdbc = mockk<ReportOverdueJdbc>()
    private val noticeRepository = mockk<ReportOverdueNoticeRepository>(relaxed = true)
    private val inboxService = mockk<InboxService>(relaxed = true)
    private val mupLetterService = mockk<MupLetterService>(relaxed = true)
    private val accountService = mockk<AccountService>(relaxed = true)

    private val service = ReportOverdueService(
        reportOverdueJdbc,
        noticeRepository,
        inboxService,
        mupLetterService,
        accountService,
    )

    @BeforeEach
    fun setup() {
        every { noticeRepository.countGrouped() } returns emptyList()
        every { noticeRepository.existsByUsernameAndLevelAndPeriodKey(any(), any(), any()) } returns false
        every { noticeRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `notifyDue twice in the same week increments warning count to 2`() {
        val overdue = overdue("volunteer", weeksMissed = 1)
        every { reportOverdueJdbc.findOverdue() } returns listOf(overdue)
        every { noticeRepository.countGrouped() } returnsMany listOf(
            emptyList(),
            listOf(count("volunteer", 1)),
        )

        val first = service.notifyDue()
        assertEquals(1, first.sent)
        assertEquals(1, first.recipients.single().warningCount)

        val second = service.notifyDue()
        assertEquals(1, second.sent)
        assertEquals(2, second.recipients.single().warningCount)

        verify(exactly = 2) { inboxService.notifyOverdue("volunteer", any(), any(), any()) }
        verify(exactly = 2) { noticeRepository.save(match { it is ReportOverdueNotice }) }
    }

    @Test
    fun `notifyDue skips when warning count is already 3`() {
        val overdue = overdue("volunteer", weeksMissed = 3)
        every { reportOverdueJdbc.findOverdue() } returns listOf(overdue)
        every { noticeRepository.countGrouped() } returns listOf(count("volunteer", 3))

        val result = service.notifyDue()

        assertEquals(0, result.sent)
        verify(exactly = 0) { inboxService.notifyOverdue(any(), any(), any(), any()) }
    }

    private fun overdue(username: String, weeksMissed: Int) = ReportOverdueDto(
        username = username,
        fullName = username.replaceFirstChar { it.uppercase() },
        program = "IT",
        weeksMissed = weeksMissed,
        hoursShort = 20,
        hoursWorked = 0,
        hoursRequired = 20,
        level = "WEEK_$weeksMissed",
        lastReportWeek = LocalDate.now().minusWeeks(weeksMissed.toLong()),
    )

    private fun count(username: String, cnt: Long) = object : WarningCountProjection {
        override val username = username
        override val cnt = cnt
    }
}
