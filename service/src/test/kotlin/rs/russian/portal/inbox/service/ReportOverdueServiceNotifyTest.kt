package rs.russian.portal.inbox.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import rs.russian.portal.inbox.repository.ReportOverdueJdbc
import rs.russian.portal.inbox.repository.ReportOverdueNoticeRepository
import rs.russian.portal.user.service.AccountService

class ReportOverdueServiceNotifyTest {
    private val reportOverdueJdbc = mockk<ReportOverdueJdbc>()
    private val deactivatedActiveContractJdbc = mockk<rs.russian.portal.inbox.repository.DeactivatedActiveContractJdbc>(relaxed = true)
    private val dissolutionQueueJdbc = mockk<rs.russian.portal.inbox.repository.DissolutionQueueJdbc>(relaxed = true)
    private val noticeRepository = mockk<ReportOverdueNoticeRepository>(relaxed = true)
    private val inboxService = mockk<InboxService>(relaxed = true)
    private val accountService = mockk<AccountService>(relaxed = true)

    private val service = ReportOverdueService(
        reportOverdueJdbc,
        deactivatedActiveContractJdbc,
        dissolutionQueueJdbc,
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
        verify(exactly = 1) {
            inboxService.notifyOverdue("volunteer", 2, any(), match { it.contains("Пропуск отчётов") }, any())
        }
        verify(exactly = 1) { noticeRepository.save(match { it.level == 2 && it.periodKey.startsWith("MANUAL-") }) }
    }

    @Test
    fun `issueWarning creates first reprimand when none active`() {
        val account = rs.russian.portal.user.domain.Account(
            id = 5,
            username = "volunteer",
            email = "volunteer@example.com",
            fullName = "Volunteer",
            active = true,
            groups = emptySet(),
        )
        every { accountService.findAccountByLogin("volunteer") } returns account
        every { noticeRepository.countByUsernameAndLevelLessThanAndCancelledAtIsNull("volunteer", ReportOverdueService.MUP_LEVEL) } returns 0

        val result = service.issueWarning("volunteer", null)

        assertEquals(1, result.warningCount)
        verify(exactly = 1) { inboxService.notifyOverdue("volunteer", 1, any(), any(), any()) }
        verify(exactly = 1) { noticeRepository.save(match { it.level == 1 && it.periodKey.startsWith("MANUAL-") }) }
    }
}
