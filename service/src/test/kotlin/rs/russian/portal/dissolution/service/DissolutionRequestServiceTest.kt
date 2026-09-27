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
import rs.russian.portal.dissolution.api.DissolutionRequestCreateRequest
import rs.russian.portal.dissolution.domain.DissolutionRequest
import rs.russian.portal.dissolution.domain.enums.DissolutionRequestStatus
import rs.russian.portal.dissolution.repository.DissolutionRequestRepository
import rs.russian.portal.inbox.service.InboxService
import rs.russian.portal.program.domain.Program
import rs.russian.portal.program.domain.ProgramCurator
import rs.russian.portal.program.repository.ProgramCuratorRepository
import rs.russian.portal.program.service.ProgramCuratorService
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.UserInfo
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.AccountService
import rs.russian.portal.user.service.DissolutionQueueService
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
    private val dissolutionQueueService = DissolutionQueueService(accountService)

    private val service = DissolutionRequestService(
        repository,
        accountRepository,
        accountService,
        programCuratorService,
        programCuratorRepository,
        inboxService,
        dissolutionQueueService,
    )

    private val volunteer = Account(
        id = 10,
        username = "volunteer",
        email = "volunteer@example.com",
        fullName = "Volunteer",
        active = true,
        groups = emptySet(),
    )
    private val senior = Account(
        id = 2,
        username = "main_volunteer",
        email = "main@example.com",
        fullName = "Main Volunteer",
        active = true,
        groups = setOf(UserGroup.MAIN_VOLUNTEER),
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
        every { programCuratorService.isCurator(any()) } returns false
        every { accountRepository.findAllActiveUsernamesByGroup(UserGroup.MAIN_VOLUNTEER.name) } returns listOf(senior.username)
        every { accountRepository.findAllActiveUsernamesByGroup(UserGroup.ADMIN.name) } returns emptyList()
        every { accountRepository.findAllActiveUsernamesByGroup(UserGroup.ADMIN_VOLUNTEER.name) } returns listOf(manager.username)
        every { accountRepository.findAllByUsernameIn(any()) } answers {
            val logins = firstArg<List<String>>().map { it.lowercase() }.toSet()
            listOf(senior, curator, manager).filter { it.username.lowercase() in logins }
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
    }

    @Test
    fun `create without program notifies senior managers only`() {
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
            inboxService.notifyDissolutionRequest(senior.username, any(), any(), volunteer.username)
        }
        verify(exactly = 0) {
            inboxService.notifyDissolutionRequest(manager.username, any(), any(), any())
        }
        verify(exactly = 0) {
            accountRepository.findAllActiveUsernamesByGroup(UserGroup.ADMIN_VOLUNTEER.name)
        }
    }

    @Test
    fun `create with program notifies program curators only`() {
        volunteer.info = UserInfo(id = volunteer.username, account = volunteer).apply {
            program = Program(code = "IT", nameRu = "IT", nameEn = "IT", nameSr = "IT")
        }
        every { currentUserLogin() } returns volunteer.username
        every { accountService.getCurrentAccount() } returns volunteer
        every { accountRepository.findByUsername("volunteer") } returns Optional.of(volunteer)
        every { repository.existsByUsernameIgnoreCaseAndStatus("volunteer", DissolutionRequestStatus.PENDING) } returns false
        every { programCuratorRepository.findAllByProgramCodeIgnoreCase("IT") } returns listOf(
            ProgramCurator(programCode = "IT", username = curator.username),
        )

        service.create(
            DissolutionRequestCreateRequest(
                fromDate = LocalDate.of(2026, 10, 1),
                reason = "Переезд",
            )
        )

        verify(exactly = 1) {
            inboxService.notifyDissolutionRequest(curator.username, any(), any(), volunteer.username)
        }
        verify(exactly = 0) {
            inboxService.notifyDissolutionRequest(senior.username, any(), any(), any())
        }
        verify(exactly = 0) {
            inboxService.notifyDissolutionRequest(manager.username, any(), any(), any())
        }
        verify(exactly = 0) {
            accountRepository.findAllActiveUsernamesByGroup(UserGroup.ADMIN_VOLUNTEER.name)
        }
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

    @Test
    fun `accept puts account on dissolution queue`() {
        val id = UUID.randomUUID()
        val fromDate = LocalDate.of(2026, 10, 1)
        val item = DissolutionRequest(
            id = id,
            username = "volunteer",
            fromDate = fromDate,
            reason = "Личные обстоятельства",
            status = DissolutionRequestStatus.PENDING,
        )
        every { currentUserLogin() } returns curator.username
        every { accountService.getCurrentAccount() } returns curator
        every { programCuratorService.isCurator("volunteer") } returns false
        every { programCuratorService.isCurator(curator.username) } returns true
        volunteer.info = UserInfo(id = volunteer.username, account = volunteer).apply {
            program = Program(code = "IT", nameRu = "IT", nameEn = "IT", nameSr = "IT")
        }
        every { programCuratorService.programCodesOf(curator.username) } returns listOf("IT")
        every { repository.findById(id) } returns Optional.of(item)
        every { accountRepository.findByUsername("volunteer") } returns Optional.of(volunteer)

        val dto = service.accept(id)

        assertEquals(DissolutionRequestStatus.ACCEPTED, dto.status)
        assertNotNull(volunteer.dissolutionQueuedAt)
        assertEquals(curator.username, volunteer.dissolutionQueuedBy)
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
        every { currentUserLogin() } returns manager.username
        every { accountService.getCurrentAccount() } returns manager
        every { repository.findById(id) } returns Optional.of(item)
        every { accountRepository.findByUsername("volunteer") } returns Optional.of(volunteer)

        service.accept(id)

        assertEquals("Заявление участника с $fromDate", volunteer.dissolutionQueueReason)
        assertEquals(manager.username, volunteer.dissolutionQueuedBy)
        assertNotNull(volunteer.dissolutionQueuedAt)
    }
}
