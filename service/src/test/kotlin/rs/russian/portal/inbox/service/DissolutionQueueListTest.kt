package rs.russian.portal.inbox.service

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import rs.russian.portal.inbox.api.DissolutionQueueDto
import rs.russian.portal.inbox.repository.DeactivatedActiveContractJdbc
import rs.russian.portal.inbox.repository.DissolutionQueueJdbc
import rs.russian.portal.inbox.repository.ReportOverdueJdbc
import rs.russian.portal.inbox.repository.ReportOverdueNoticeRepository
import rs.russian.portal.user.service.AccountService
import java.time.OffsetDateTime

class DissolutionQueueListTest {
    private val reportOverdueJdbc = mockk<ReportOverdueJdbc>(relaxed = true)
    private val deactivatedActiveContractJdbc = mockk<DeactivatedActiveContractJdbc>(relaxed = true)
    private val dissolutionQueueJdbc = mockk<DissolutionQueueJdbc>()
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

    @Test
    fun `listDissolutionQueue returns queued accounts from jdbc`() {
        val queued = DissolutionQueueDto(
            accountId = 10,
            username = "volunteer",
            fullName = "Volunteer",
            active = true,
            dissolutionQueuedAt = OffsetDateTime.parse("2026-09-27T10:00:00Z"),
            dissolutionQueuedBy = "moderator",
        )
        every { dissolutionQueueJdbc.findQueued() } returns listOf(queued)

        val result = service.listDissolutionQueue()

        assertEquals(1, result.size)
        assertEquals(10, result[0].accountId)
        assertTrue(result[0].active)
        assertEquals("moderator", result[0].dissolutionQueuedBy)
    }
}
