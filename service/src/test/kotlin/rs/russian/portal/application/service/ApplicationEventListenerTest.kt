package rs.russian.portal.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import rs.russian.portal.application.domain.Application
import rs.russian.portal.application.domain.ApplicationStatus
import rs.russian.portal.application.domain.ApplicationType
import rs.russian.portal.application.event.ApplicationUpdateEvent
import rs.russian.portal.application.mapper.ApplicationMapper
import rs.russian.portal.mail.service.EmailService
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.mapper.ContractMapper
import rs.russian.portal.user.service.AccountService
import java.util.UUID

class ApplicationEventListenerTest {

    private lateinit var emailService: EmailService
    private lateinit var accountService: AccountService
    private lateinit var contractMapper: ContractMapper
    private lateinit var applicationMapper: ApplicationMapper
    private lateinit var applicationService: ApplicationService
    private lateinit var listener: ApplicationEventListener

    @BeforeEach
    fun setUp() {
        emailService = mockk(relaxed = true)
        accountService = mockk(relaxed = true)
        contractMapper = mockk(relaxed = true)
        applicationMapper = mockk(relaxed = true)
        applicationService = mockk(relaxed = true)
        listener = ApplicationEventListener(
            emailService,
            accountService,
            contractMapper,
            mockk(relaxed = true),
            applicationMapper,
            applicationService,
            mockk(relaxed = true),
        )
    }

    @Test
    fun `prolongation of a depersonalized account is skipped instead of throwing`() {
        val id = UUID.randomUUID()
        val application = Application(
            id = id,
            email = "depersonalized@deleted.local",
            name = "ghost",
            status = ApplicationStatus.DONE,
            type = ApplicationType.PROLONGATION,
        )
        every { applicationService.get(id) } returns application
        every { accountService.findAccountByEmail("depersonalized@deleted.local") } returns null

        // Must not throw (previously an NPE on findAccountByEmail(...)!!).
        listener.handleApplicationStatusChange(ApplicationUpdateEvent(id))

        verify(exactly = 0) { accountService.switchActiveState(any(), any()) }
        verify(exactly = 0) { accountService.updateContracts(any(), any()) }
    }

    @Test
    fun `prolongation of an existing account reactivates it and updates contracts`() {
        val id = UUID.randomUUID()
        val application = Application(
            id = id,
            email = "volunteer@example.com",
            name = "Ivan",
            status = ApplicationStatus.DONE,
            type = ApplicationType.PROLONGATION,
            contractFrom = java.time.LocalDate.now(),
            contractUntil = java.time.LocalDate.now().plusYears(1),
            contractType = rs.russian.generated.model.ContractTypeEnum.REGULAR,
        )
        val account = Account(
            id = 42,
            username = "volunteer",
            email = "volunteer@example.com",
            fullName = "Ivan",
        )
        every { applicationService.get(id) } returns application
        every { accountService.findAccountByEmail("volunteer@example.com") } returns account
        every { contractMapper.map(account.contracts) } returns mutableSetOf()

        listener.handleApplicationStatusChange(ApplicationUpdateEvent(id))

        verify { accountService.switchActiveState(42, true) }
        verify { accountService.updateContracts(42, any()) }
    }
}
