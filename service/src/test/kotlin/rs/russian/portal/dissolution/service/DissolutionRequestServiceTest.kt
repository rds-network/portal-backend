package rs.russian.portal.dissolution.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import rs.russian.portal.dissolution.api.DissolutionRequestCreateRequest
import rs.russian.portal.dissolution.domain.DissolutionRequest
import rs.russian.portal.dissolution.domain.enums.DissolutionRequestStatus
import rs.russian.portal.dissolution.repository.DissolutionRequestRepository
import rs.russian.portal.inbox.service.InboxService
import rs.russian.portal.program.repository.ProgramCuratorRepository
import rs.russian.portal.program.service.ProgramCuratorService
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class DissolutionRequestServiceTest {

    private val repository = mockk<DissolutionRequestRepository>()
    private val accountRepository = mockk<AccountRepository>(relaxed = true)
    private val accountService = mockk<AccountService>()
    private val programCuratorService = mockk<ProgramCuratorService>(relaxed = true)
    private val programCuratorRepository = mockk<ProgramCuratorRepository>(relaxed = true)
    private val inboxService = mockk<InboxService>(relaxed = true)

    private val service = DissolutionRequestService(
        repository,
        accountRepository,
        accountService,
        programCuratorService,
        programCuratorRepository,
        inboxService,
    )

    private val volunteer = Account(
        id = 10,
        username = "volunteer",
        email = "volunteer@example.com",
        fullName = "Volunteer",
        active = true,
        groups = emptySet(),
    )
    private val manager = Account(
        id = 2,
        username = "admin_user",
        email = "admin@example.com",
        fullName = "Admin",
        active = true,
        groups = setOf(UserGroup.ADMIN_VOLUNTEER),
    )

    @BeforeEach
    fun setUp() {
        mockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
        every { repository.save(any()) } answers { firstArg() }
        every { programCuratorService.isCurator(any()) } returns false
        every { accountRepository.findAllActiveUsernamesByGroup(any()) } returns listOf(manager.username)
        every { accountRepository.findAllByUsernameIn(any()) } returns listOf(manager)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
    }

    @Test
    fun `create stores pending request and notifies managers`() {
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
        verify { inboxService.notifyDissolutionRequest(manager.username, any(), any(), volunteer.username) }
    }

    @Test
    fun `accept marks accepted without sending MUP`() {
        val id = UUID.randomUUID()
        val item = DissolutionRequest(
            id = id,
            username = "volunteer",
            fromDate = LocalDate.of(2026, 10, 1),
            reason = "Request",
            status = DissolutionRequestStatus.PENDING,
        )
        every { currentUserLogin() } returns manager.username
        every { accountService.getCurrentAccount() } returns manager
        every { repository.findById(id) } returns Optional.of(item)
        every { accountRepository.findByUsername("volunteer") } returns Optional.of(volunteer)

        val dto = service.accept(id)

        assertEquals(DissolutionRequestStatus.ACCEPTED, dto.status)
        assertEquals(manager.username, dto.decidedBy)
        verify { inboxService.notifyDissolutionDecision("volunteer", any(), any(), manager.username) }
    }
}
