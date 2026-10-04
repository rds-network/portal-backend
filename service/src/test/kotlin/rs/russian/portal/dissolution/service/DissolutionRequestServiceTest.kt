package rs.russian.portal.dissolution.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import rs.russian.portal.config.AppProperties
import rs.russian.portal.config.LeaveProperties
import rs.russian.portal.dissolution.api.DissolutionRequestCreateRequest
import rs.russian.portal.dissolution.domain.DissolutionRequest
import rs.russian.portal.dissolution.domain.enums.DissolutionRequestStatus
import rs.russian.portal.dissolution.repository.DissolutionRequestRepository
import rs.russian.portal.inbox.service.InboxService
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.AccountService
import rs.russian.portal.user.service.DissolutionQueueService
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class DissolutionRequestServiceTest {

    private val appProperties = AppProperties(
        frontendUri = "http://localhost:3000",
        leave = LeaveProperties(approverUsername = "legkov777"),
    )
    private val repository = mockk<DissolutionRequestRepository>()
    private val accountRepository = mockk<AccountRepository>(relaxed = true)
    private val accountService = mockk<AccountService>()
    private val inboxService = mockk<InboxService>(relaxed = true)
    private val dissolutionQueueService = DissolutionQueueService(accountService)

    private val service = DissolutionRequestService(
        appProperties,
        repository,
        accountRepository,
        accountService,
        inboxService,
        dissolutionQueueService,
    )

    private val approver = Account(
        id = 1,
        username = "legkov777",
        email = "approver@example.com",
        fullName = "Leonid",
        active = true,
        groups = setOf(UserGroup.ADMIN_VOLUNTEER),
    )
    private val volunteer = Account(
        id = 10,
        username = "volunteer",
        email = "volunteer@example.com",
        fullName = "Volunteer",
        active = true,
        groups = emptySet(),
    )
    private val curator = Account(
        id = 3,
        username = "curator_it",
        email = "curator@example.com",
        fullName = "Curator IT",
        active = true,
        groups = emptySet(),
    )
    private val manager = Account(
        id = 4,
        username = "admin_user",
        email = "admin@example.com",
        fullName = "Admin",
        active = true,
        groups = setOf(UserGroup.ADMIN_VOLUNTEER),
    )

    @BeforeEach
    fun setUp() {
        volunteer.info = null
        mockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
        every { repository.save(any()) } answers { firstArg() }
        every { accountRepository.findByUsername("legkov777") } returns Optional.of(approver)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
    }

    @Test
    fun `create notifies only the configured leave dissolution approver`() {
        every { currentUserLogin() } returns volunteer.username
        every { accountService.getCurrentAccount() } returns volunteer
        every { accountRepository.findByUsername("volunteer") } returns Optional.of(volunteer)
        every { repository.existsByUsernameIgnoreCaseAndStatus("volunteer", DissolutionRequestStatus.PENDING) } returns false

        val dto = service.create(
            DissolutionRequestCreateRequest(
                fromDate = LocalDate.of(2026, 10, 1),
                reason = "Личные обстоятельства",
            )
        )

        assertEquals(DissolutionRequestStatus.PENDING, dto.status)
        assertEquals(LocalDate.of(2026, 10, 1), dto.fromDate)
        assertEquals("Личные обстоятельства", dto.reason)
        verify(exactly = 1) {
            inboxService.notifyDissolutionRequest(
                recipient = "legkov777",
                subject = any(),
                body = any(),
                createdBy = "volunteer",
            )
        }
        verify(exactly = 0) {
            inboxService.notifyDissolutionRequest(
                recipient = "curator_it",
                subject = any(),
                body = any(),
                createdBy = any(),
            )
        }
        verify(exactly = 0) {
            inboxService.notifyDissolutionRequest(
                recipient = "admin_user",
                subject = any(),
                body = any(),
                createdBy = any(),
            )
        }
    }

    @Test
    fun `create skips notify when requester is the dissolution approver`() {
        every { currentUserLogin() } returns approver.username
        every { accountService.getCurrentAccount() } returns approver
        every { accountRepository.findByUsername("legkov777") } returns Optional.of(approver)
        every { repository.existsByUsernameIgnoreCaseAndStatus("legkov777", DissolutionRequestStatus.PENDING) } returns false

        service.create(
            DissolutionRequestCreateRequest(
                fromDate = LocalDate.of(2026, 10, 1),
                reason = "Собственное заявление",
            )
        )

        verify(exactly = 0) { inboxService.notifyDissolutionRequest(any(), any(), any(), any()) }
    }

    @Test
    fun `accept is allowed only for dissolution approver`() {
        val id = UUID.randomUUID()
        val item = DissolutionRequest(
            id = id,
            username = "volunteer",
            fromDate = LocalDate.of(2026, 10, 1),
            reason = "Request",
            status = DissolutionRequestStatus.PENDING,
        )
        every { repository.findById(id) } returns Optional.of(item)
        every { accountRepository.findByUsername("volunteer") } returns Optional.of(volunteer)

        every { currentUserLogin() } returns curator.username
        every { accountService.getCurrentAccount() } returns curator
        assertThrows<NotAuthorizedException> { service.accept(id) }

        every { currentUserLogin() } returns manager.username
        every { accountService.getCurrentAccount() } returns manager
        assertThrows<NotAuthorizedException> { service.accept(id) }

        every { currentUserLogin() } returns approver.username
        every { accountService.getCurrentAccount() } returns approver
        val dto = service.accept(id)

        assertEquals(DissolutionRequestStatus.ACCEPTED, dto.status)
        assertEquals("legkov777", dto.decidedBy)
        verify { inboxService.notifyDissolutionDecision("volunteer", any(), any(), "legkov777") }
    }

    @Test
    fun `accept puts account on dissolution queue`() {
        val id = UUID.randomUUID()
        val item = DissolutionRequest(
            id = id,
            username = "volunteer",
            fromDate = LocalDate.of(2026, 10, 1),
            reason = "Личные обстоятельства",
            status = DissolutionRequestStatus.PENDING,
        )
        every { currentUserLogin() } returns approver.username
        every { accountService.getCurrentAccount() } returns approver
        every { repository.findById(id) } returns Optional.of(item)
        every { accountRepository.findByUsername("volunteer") } returns Optional.of(volunteer)

        val dto = service.accept(id)

        assertEquals(DissolutionRequestStatus.ACCEPTED, dto.status)
        assertNotNull(volunteer.dissolutionQueuedAt)
        assertEquals(approver.username, volunteer.dissolutionQueuedBy)
        assertEquals("Личные обстоятельства", volunteer.dissolutionQueueReason)
    }

    @Test
    fun `accept uses fallback queue reason when request reason blank`() {
        val id = UUID.randomUUID()
        val fromDate = LocalDate.of(2026, 11, 5)
        val item = DissolutionRequest(
            id = id,
            username = "volunteer",
            fromDate = fromDate,
            reason = "   ",
            status = DissolutionRequestStatus.PENDING,
        )
        every { currentUserLogin() } returns approver.username
        every { accountService.getCurrentAccount() } returns approver
        every { repository.findById(id) } returns Optional.of(item)
        every { accountRepository.findByUsername("volunteer") } returns Optional.of(volunteer)

        service.accept(id)

        assertEquals("Заявление участника с $fromDate", volunteer.dissolutionQueueReason)
        assertEquals(approver.username, volunteer.dissolutionQueuedBy)
        assertNotNull(volunteer.dissolutionQueuedAt)
    }

    @Test
    fun `reject is forbidden for curator who is not dissolution approver`() {
        val id = UUID.randomUUID()
        val item = DissolutionRequest(
            id = id,
            username = "volunteer",
            fromDate = LocalDate.of(2026, 10, 1),
            reason = "Request",
            status = DissolutionRequestStatus.PENDING,
        )
        every { repository.findById(id) } returns Optional.of(item)
        every { currentUserLogin() } returns curator.username
        every { accountService.getCurrentAccount() } returns curator

        assertThrows<NotAuthorizedException> { service.reject(id, null) }
        verify(exactly = 0) { inboxService.notifyDissolutionDecision(any(), any(), any(), any()) }
    }

    @Test
    fun `pending returns all for approver and empty for others`() {
        val item = DissolutionRequest(
            id = UUID.randomUUID(),
            username = "volunteer",
            fromDate = LocalDate.of(2026, 10, 1),
            reason = "Request",
            status = DissolutionRequestStatus.PENDING,
        )
        every { repository.findAllByStatusOrderByCreatedAtAsc(DissolutionRequestStatus.PENDING) } returns listOf(item)
        every { accountRepository.findByUsername("volunteer") } returns Optional.of(volunteer)

        every { accountService.getCurrentAccount() } returns curator
        assertEquals(0, service.pending().size)

        every { accountService.getCurrentAccount() } returns approver
        assertEquals(1, service.pending().size)
    }
}
