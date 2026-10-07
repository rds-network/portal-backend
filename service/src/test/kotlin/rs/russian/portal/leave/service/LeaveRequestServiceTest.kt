package rs.russian.portal.leave.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import rs.russian.portal.config.AppProperties
import rs.russian.portal.config.LeaveProperties
import rs.russian.portal.inbox.service.InboxService
import rs.russian.portal.leave.api.LeaveRequestCreateRequest
import rs.russian.portal.leave.domain.LeaveRequest
import rs.russian.portal.leave.domain.enums.LeaveRequestStatus
import rs.russian.portal.leave.repository.LeaveRequestRepository
import rs.russian.portal.program.service.ProgramCuratorService
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.PrivilegedOps
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.shared.security.currentUserRoles
import rs.russian.portal.shared.security.realUserLogin
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class LeaveRequestServiceTest {

    private val appProperties = AppProperties(
        frontendUri = "http://localhost:3000",
        leave = LeaveProperties(approverUsername = "legkov777"),
    )
    private val leaveRequestRepository = mockk<LeaveRequestRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val accountService = mockk<AccountService>()
    private val programCuratorService = mockk<ProgramCuratorService>(relaxed = true)
    private val inboxService = mockk<InboxService>(relaxed = true)

    private val service = LeaveRequestService(
        appProperties,
        leaveRequestRepository,
        accountRepository,
        accountService,
        programCuratorService,
        inboxService,
    )

    private val approver = Account(
        id = 1,
        username = "legkov777",
        email = "approver@example.com",
        fullName = "Leonid",
        active = true,
        groups = setOf(UserGroup.ADMIN_VOLUNTEER),
    )
    private val curator = Account(
        id = 2,
        username = "anna_curator",
        email = "anna@example.com",
        fullName = "Anna NovikovaLavrova",
        active = true,
        groups = setOf(UserGroup.ADMIN_VOLUNTEER),
    )
    private val volunteer = Account(
        id = 3,
        username = "volunteer",
        email = "volunteer@example.com",
        fullName = "Volunteer",
        active = true,
        groups = emptySet(),
    )

    @BeforeEach
    fun setUp() {
        mockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
        PrivilegedOps.approverUsername = "legkov777"
        PrivilegedOps.accountLookup = null
        every { leaveRequestRepository.save(any()) } answers { firstArg() }
        every { accountRepository.findByUsername(any()) } answers {
            when (firstArg<String>()) {
                "legkov777" -> Optional.of(approver)
                "anna_curator" -> Optional.of(curator)
                "volunteer" -> Optional.of(volunteer)
                else -> Optional.empty()
            }
        }
        every { accountRepository.findByEmail(any()) } returns Optional.empty()
        every { currentUserRoles() } returns emptySet()
    }

    @AfterEach
    fun tearDown() {
        PrivilegedOps.accountLookup = null
        unmockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
    }

    @Test
    fun `notifyNewLeave notifies only the configured leave approver`() {
        every { currentUserLogin() } returns volunteer.username
        every { realUserLogin() } returns volunteer.username
        every { accountService.getCurrentAccount() } returns volunteer

        service.create(
            LeaveRequestCreateRequest(
                startDate = LocalDate.of(2026, 10, 1),
                endDate = LocalDate.of(2026, 10, 14),
                reason = "rest",
            )
        )

        verify(exactly = 1) {
            inboxService.notifyLeaveRequest(
                recipient = "legkov777",
                subject = any(),
                body = any(),
                createdBy = "volunteer",
            )
        }
        verify(exactly = 0) {
            inboxService.notifyLeaveRequest(
                recipient = "anna_curator",
                subject = any(),
                body = any(),
                createdBy = any(),
            )
        }
    }

    @Test
    fun `notifyNewLeave skips when requester is the leave approver`() {
        every { currentUserLogin() } returns approver.username
        every { realUserLogin() } returns approver.username
        every { accountService.getCurrentAccount() } returns approver

        service.create(
            LeaveRequestCreateRequest(
                startDate = LocalDate.of(2026, 10, 1),
                endDate = LocalDate.of(2026, 10, 14),
            )
        )

        verify(exactly = 0) { inboxService.notifyLeaveRequest(any(), any(), any(), any()) }
    }

    @Test
    fun `accept is allowed only for leave approver`() {
        val leave = LeaveRequest(
            id = UUID.randomUUID(),
            username = volunteer.username,
            startDate = LocalDate.of(2026, 10, 1),
            endDate = LocalDate.of(2026, 10, 14),
            status = LeaveRequestStatus.PENDING,
        )
        every { leaveRequestRepository.findById(leave.id!!) } returns Optional.of(leave)

        every { currentUserLogin() } returns curator.username
        every { realUserLogin() } returns curator.username
        every { accountService.getCurrentAccount() } returns curator
        assertThrows<NotAuthorizedException> { service.accept(leave.id!!) }

        every { currentUserLogin() } returns approver.username
        every { realUserLogin() } returns approver.username
        every { accountService.getCurrentAccount() } returns approver
        val dto = service.accept(leave.id!!)
        assertEquals(LeaveRequestStatus.ACCEPTED, dto.status)
        assertEquals("legkov777", dto.decidedBy)
    }

    @Test
    fun `accept is allowed for ADMIN_SSO even when username differs`() {
        val leave = LeaveRequest(
            id = UUID.randomUUID(),
            username = volunteer.username,
            startDate = LocalDate.of(2026, 10, 1),
            endDate = LocalDate.of(2026, 10, 14),
            status = LeaveRequestStatus.PENDING,
        )
        every { leaveRequestRepository.findById(leave.id!!) } returns Optional.of(leave)
        every { currentUserLogin() } returns "other_admin"
        every { realUserLogin() } returns "other_admin"
        every { currentUserRoles() } returns setOf(UserGroup.ADMIN_SSO)
        every { accountService.getCurrentAccount() } returns curator

        val dto = service.accept(leave.id!!)
        assertEquals(LeaveRequestStatus.ACCEPTED, dto.status)
        assertEquals("other_admin", dto.decidedBy)
    }

    @Test
    fun `reject is forbidden for curator who is not leave approver`() {
        val leave = LeaveRequest(
            id = UUID.randomUUID(),
            username = volunteer.username,
            startDate = LocalDate.of(2026, 10, 1),
            endDate = LocalDate.of(2026, 10, 14),
            status = LeaveRequestStatus.PENDING,
        )
        every { leaveRequestRepository.findById(leave.id!!) } returns Optional.of(leave)
        every { currentUserLogin() } returns curator.username
        every { realUserLogin() } returns curator.username
        every { accountService.getCurrentAccount() } returns curator

        assertThrows<NotAuthorizedException> { service.reject(leave.id!!, null) }
        verify(exactly = 0) { inboxService.notifyLeaveDecision(any(), any(), any(), any()) }
    }

    @Test
    fun `pending returns all for approver and empty for others`() {
        val leave = LeaveRequest(
            id = UUID.randomUUID(),
            username = volunteer.username,
            startDate = LocalDate.of(2026, 10, 1),
            endDate = LocalDate.of(2026, 10, 14),
            status = LeaveRequestStatus.PENDING,
        )
        every {
            leaveRequestRepository.findAllByStatusOrderByCreatedAtAsc(LeaveRequestStatus.PENDING)
        } returns listOf(leave)

        every { realUserLogin() } returns curator.username
        every { accountService.getCurrentAccount() } returns curator
        assertTrue(service.pending().isEmpty())

        every { realUserLogin() } returns approver.username
        every { accountService.getCurrentAccount() } returns approver
        assertEquals(1, service.pending().size)
    }

    @Test
    fun `pending still visible for approver while impersonating`() {
        val leave = LeaveRequest(
            id = UUID.randomUUID(),
            username = volunteer.username,
            startDate = LocalDate.of(2026, 10, 1),
            endDate = LocalDate.of(2026, 10, 14),
            status = LeaveRequestStatus.PENDING,
        )
        every {
            leaveRequestRepository.findAllByStatusOrderByCreatedAtAsc(LeaveRequestStatus.PENDING)
        } returns listOf(leave)
        every { currentUserLogin() } returns volunteer.username
        every { realUserLogin() } returns approver.username
        every { accountService.getCurrentAccount() } returns volunteer

        assertEquals(1, service.pending().size)
        assertTrue(service.meta().isLeaveApprover)
    }
}
