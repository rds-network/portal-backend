package rs.russian.portal.inbox.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import rs.russian.portal.inbox.api.ReportOverdueDto
import rs.russian.portal.inbox.domain.ReportOverdueNotice
import rs.russian.portal.inbox.repository.ReportOverdueJdbc
import rs.russian.portal.inbox.repository.ReportOverdueNoticeRepository
import rs.russian.portal.inbox.repository.WarningCountProjection
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate

class ReportOverdueServiceNotifyTest {
    private val reportOverdueJdbc = mockk<ReportOverdueJdbc>()
    private val deactivatedActiveContractJdbc = mockk<rs.russian.portal.inbox.repository.DeactivatedActiveContractJdbc>(relaxed = true)
    private val noticeRepository = mockk<ReportOverdueNoticeRepository>(relaxed = true)
    private val inboxService = mockk<InboxService>(relaxed = true)
    private val accountService = mockk<AccountService>(relaxed = true)

    private val service = ReportOverdueService(
        reportOverdueJdbc,
        deactivatedActiveContractJdbc,
        noticeRepository,
        inboxService,
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
        verify(exactly = 2) { noticeRepository.save(any()) }
    }

    @Test
    fun `third warning only sends an overdue notice without a MUP record or deactivation`() {
        every { reportOverdueJdbc.findOverdue() } returns listOf(overdue("volunteer", weeksMissed = 3))
        every { noticeRepository.countGrouped() } returns listOf(count("volunteer", 2))

        val result = service.notifyDue()

        assertEquals(1, result.sent)
        assertEquals(3, result.recipients.single().warningCount)
        assertFalse(result.recipients.single().mupSent)
        verify(exactly = 1) {
            inboxService.notifyOverdue("volunteer", 3, any(), match { !it.contains("МУП") })
        }
        verify(exactly = 1) { noticeRepository.save(match { it.level == 3 }) }
        verify(exactly = 0) { noticeRepository.save(match { it.level == ReportOverdueService.MUP_LEVEL }) }
        verify(exactly = 0) { accountService.switchActiveState(any(), any()) }
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

    @Test
    fun `issueWarning advances level from 1 to 2`() {
        val account = rs.russian.portal.user.domain.Account(
            id = 5,
            username = "volunteer",
            email = "volunteer@example.com",
            fullName = "Volunteer",
            active = true,
            groups = emptySet(),
        )
        every { accountService.findAccountByLogin("volunteer") } returns account
        every { noticeRepository.countByUsernameAndLevelLessThanAndCancelledAtIsNull("volunteer", ReportOverdueService.MUP_LEVEL) } returns 1
        every { noticeRepository.existsByUsernameAndLevelAndPeriodKey(any(), any(), any()) } returns false

        val result = service.issueWarning("volunteer", "Пропуск отчётов")

        assertEquals(2, result.warningCount)
        verify(exactly = 1) { inboxService.notifyOverdue("volunteer", 2, any(), match { it.contains("Пропуск отчётов") }) }
        verify(exactly = 1) { noticeRepository.save(match { it.level == 2 && it.periodKey.startsWith("MANUAL-") }) }
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
