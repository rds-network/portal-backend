package rs.russian.portal.user.scheduler

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import rs.russian.portal.accountstatus.domain.enums.AccountStatusEventSource
import rs.russian.portal.accountstatus.service.AccountStatusService
import rs.russian.portal.application.domain.ApplicationStatus
import rs.russian.portal.application.domain.ApplicationType
import rs.russian.portal.application.repository.ApplicationRepository
import rs.russian.portal.mail.service.EmailService
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.Contract
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.AccountRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ContractExpirationSchedulerTest {

    private lateinit var accountRepository: AccountRepository
    private lateinit var applicationRepository: ApplicationRepository
    private lateinit var accountStatusService: AccountStatusService
    private lateinit var emailService: EmailService
    private lateinit var templateEngine: TemplateEngine
    private lateinit var scheduler: ContractExpirationScheduler

    @BeforeEach
    fun setUp() {
        accountRepository = mockk(relaxed = true)
        applicationRepository = mockk(relaxed = true)
        accountStatusService = mockk(relaxed = true)
        emailService = mockk(relaxed = true)
        templateEngine = mockk(relaxed = true)
        scheduler = ContractExpirationScheduler(
            accountRepository,
            applicationRepository,
            accountStatusService,
            emailService,
            templateEngine
        )
    }

    @Test
    fun `run should send reminders for expiring contracts without active prolongation`() {
        val today = LocalDate.now()
        val reminderDate = today.plusDays(7)
        val formattedDate = reminderDate.format(DATE_FORMATTER)
        val account = createAccount(1, "volunteer", "volunteer@example.com", reminderDate)
        val reminderBody = "reminder-body-$formattedDate"

        every { accountRepository.findByLatestContractDate(reminderDate, true) } returns listOf(account)
        every { accountRepository.findByLatestContractDate(today.minusDays(1), false) } returns emptyList()
        every { accountRepository.findAllActiveByGroup(any<String>()) } returns emptyList()
        every {
            applicationRepository.existsByEmailAndTypeAndStatusNotIn(
                account.email,
                ApplicationType.PROLONGATION,
                listOf(ApplicationStatus.DONE, ApplicationStatus.DENY)
            )
        } returns false
        every { templateEngine.process("contract_expiration_reminder", any<Context>()) } returns reminderBody

        scheduler.run()

        verify { templateEngine.process("contract_expiration_reminder", any<Context>()) }
        verify {
            emailService.sendCommonEmail(
                account,
                "Окончание контракта",
                reminderBody
            )
        }
        verify(exactly = 0) {
            accountStatusService.applyImmediate(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `run should deactivate expired accounts without active prolongation`() {
        val today = LocalDate.now()
        val account = createAccount(2, "expired", "expired@example.com", today.minusDays(1))
        val admin = createAccount(3, "admin", "admin@example.com", today.minusDays(30))
        val adminBody = "admin-body-${account.fullName}"

        every { accountRepository.findByLatestContractDate(today.plusDays(7), true) } returns emptyList()
        every { accountRepository.findByLatestContractDate(today.minusDays(1), false) } returns listOf(account)
        every { accountRepository.findAllActiveByGroup(UserGroup.ADMIN_VOLUNTEER.name) } returns listOf(admin)
        every {
            applicationRepository.existsByEmailAndTypeAndStatusNotIn(
                account.email,
                ApplicationType.PROLONGATION,
                listOf(ApplicationStatus.DONE, ApplicationStatus.DENY)
            )
        } returns false
        every { templateEngine.process("contract_deactivation_admin", any<Context>()) } returns adminBody

        scheduler.run()

        verify {
            accountStatusService.applyImmediate(
                accountId = account.id!!,
                activeTo = false,
                source = AccountStatusEventSource.SCHEDULER,
                actorUsername = null,
                reason = "contract expired",
                notifyApprover = true,
            )
        }
        verify { templateEngine.process("contract_deactivation_admin", any<Context>()) }
        verify {
            emailService.sendCommonEmail(
                admin,
                "Деактивация учетной записи волонтера",
                adminBody
            )
        }
    }

    @Test
    fun `run should continue deactivation if one account fails`() {
        val today = LocalDate.now()
        val account1 = createAccount(4, "expired-1", "expired1@example.com", today.minusDays(2))
        val account2 = createAccount(5, "expired-2", "expired2@example.com", today.minusDays(2))
        val admin = createAccount(6, "admin2", "admin2@example.com", today.minusDays(30))
        val adminBody = "admin-body-2"

        every { accountRepository.findByLatestContractDate(today.plusDays(7), true) } returns emptyList()
        every { accountRepository.findByLatestContractDate(today.minusDays(1), false) } returns listOf(
            account1,
            account2
        )
        every { accountRepository.findAllActiveByGroup(UserGroup.ADMIN_VOLUNTEER.name) } returns listOf(admin)
        every {
            applicationRepository.existsByEmailAndTypeAndStatusNotIn(
                account1.email,
                ApplicationType.PROLONGATION,
                listOf(ApplicationStatus.DONE, ApplicationStatus.DENY)
            )
        } returns false
        every {
            applicationRepository.existsByEmailAndTypeAndStatusNotIn(
                account2.email,
                ApplicationType.PROLONGATION,
                listOf(ApplicationStatus.DONE, ApplicationStatus.DENY)
            )
        } returns false
        every { templateEngine.process("contract_deactivation_admin", any<Context>()) } returns adminBody
        every {
            accountStatusService.applyImmediate(
                accountId = account1.id!!,
                activeTo = false,
                source = AccountStatusEventSource.SCHEDULER,
                actorUsername = null,
                reason = "contract expired",
                notifyApprover = true,
            )
        } throws RuntimeException("boom")

        scheduler.run()

        verify {
            accountStatusService.applyImmediate(
                accountId = account1.id!!,
                activeTo = false,
                source = AccountStatusEventSource.SCHEDULER,
                actorUsername = null,
                reason = "contract expired",
                notifyApprover = true,
            )
        }
        verify {
            accountStatusService.applyImmediate(
                accountId = account2.id!!,
                activeTo = false,
                source = AccountStatusEventSource.SCHEDULER,
                actorUsername = null,
                reason = "contract expired",
                notifyApprover = true,
            )
        }
    }

    @Test
    fun `run should deactivate accounts expired before the cutoff date`() {
        val today = LocalDate.now()
        val account = createAccount(7, "expired-old", "expired-old@example.com", today.minusDays(3))
        val admin = createAccount(8, "admin3", "admin3@example.com", today.minusDays(30))
        val adminBody = "admin-body-3"

        every { accountRepository.findByLatestContractDate(today.plusDays(7), true) } returns emptyList()
        every { accountRepository.findByLatestContractDate(today.minusDays(1), false) } returns listOf(account)
        every { accountRepository.findAllActiveByGroup(UserGroup.ADMIN_VOLUNTEER.name) } returns listOf(admin)
        every {
            applicationRepository.existsByEmailAndTypeAndStatusNotIn(
                account.email,
                ApplicationType.PROLONGATION,
                listOf(ApplicationStatus.DONE, ApplicationStatus.DENY)
            )
        } returns false
        every { templateEngine.process("contract_deactivation_admin", any<Context>()) } returns adminBody

        scheduler.run()

        verify {
            accountStatusService.applyImmediate(
                accountId = account.id!!,
                activeTo = false,
                source = AccountStatusEventSource.SCHEDULER,
                actorUsername = null,
                reason = "contract expired",
                notifyApprover = true,
            )
        }
    }

    private fun createAccount(id: Int, username: String, email: String, endDate: LocalDate): Account {
        val account = Account(
            id = id,
            username = username,
            email = email,
            fullName = username,
            active = true,
            lastSynced = LocalDateTime.now()
        )
        val contract = Contract(
            account = account,
            startDate = endDate.minusMonths(1),
            endDate = endDate
        )
        account.contracts.add(contract)
        return account
    }

    companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    }
}
