package rs.russian.portal.accountstatus.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import rs.russian.portal.accountstatus.domain.AccountStatusEvent
import rs.russian.portal.accountstatus.domain.AccountStatusRequest
import rs.russian.portal.accountstatus.domain.enums.AccountStatusEventSource
import rs.russian.portal.accountstatus.domain.enums.AccountStatusRequestStatus
import rs.russian.portal.accountstatus.repository.AccountStatusEventRepository
import rs.russian.portal.accountstatus.repository.AccountStatusRequestRepository
import rs.russian.portal.config.AccountStatusProperties
import rs.russian.portal.config.AppProperties
import rs.russian.portal.inbox.service.InboxService
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.PrivilegedOps
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.shared.security.currentUserRoles
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.AccountService
import java.util.Optional
import java.util.UUID

class AccountStatusServiceTest {

    private val appProperties = AppProperties(
        frontendUri = "http://localhost:3000",
        accountStatus = AccountStatusProperties(approverUsername = "legkov777"),
    )
    private val accountService = mockk<AccountService>()
    private val accountRepository = mockk<AccountRepository>(relaxed = true)
    private val requestRepository = mockk<AccountStatusRequestRepository>()
    private val eventRepository = mockk<AccountStatusEventRepository>(relaxed = true)
    private val inboxService = mockk<InboxService>(relaxed = true)

    private val service = AccountStatusService(
        appProperties,
        accountService,
        accountRepository,
        requestRepository,
        eventRepository,
        inboxService,
    )

    private val target = Account(
        id = 10,
        username = "volunteer",
        email = "volunteer@example.com",
        fullName = "Volunteer",
        active = true,
        groups = emptySet(),
    )
    private val approver = Account(
        id = 1,
        username = "legkov777",
        email = "leonid@example.com",
        fullName = "Leonid",
        active = true,
        groups = setOf(UserGroup.ADMIN_VOLUNTEER),
    )
    private val admin = Account(
        id = 2,
        username = "admin_user",
        email = "admin@example.com",
        fullName = "Admin",
        active = true,
        groups = setOf(UserGroup.ADMIN_VOLUNTEER),
    )

    @BeforeEach
    fun setUp() {
        PrivilegedOps.approverUsername = "legkov777"
        PrivilegedOps.accountLookup = null
        mockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
        every { currentUserRoles() } returns setOf(UserGroup.ADMIN_VOLUNTEER)
        every { eventRepository.save(any()) } answers { firstArg() }
        every { requestRepository.save(any()) } answers { firstArg() }
        every { accountRepository.findByUsername("legkov777") } returns Optional.of(approver)
        every { accountRepository.findByUsername("admin_user") } returns Optional.of(admin)
        every { accountRepository.findByEmail(any()) } answers {
            val email = firstArg<String>()
            when (email) {
                "leonid@example.com" -> Optional.of(approver)
                else -> Optional.empty()
            }
        }
    }

    @AfterEach
    fun tearDown() {
        PrivilegedOps.accountLookup = null
        unmockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
    }

    @Test
    fun `non-approver creates pending request and does not flip`() {
        every { currentUserLogin() } returns admin.username
        every { accountService.getCurrentAccount() } returns admin
        every { accountService.getAccount(10) } returns target
        every {
            requestRepository.existsByTargetAccountIdAndRequestedActiveAndStatus(
                10,
                false,
                AccountStatusRequestStatus.PENDING,
            )
        } returns false

        val result = service.requestOrApply(10, false, "reason")

        assertTrue(result.pending)
        assertEquals(true, result.active)
        verify(exactly = 0) { accountService.switchActiveState(any(), any()) }
        verify {
            inboxService.notifyAccountStatusRequest(
                recipient = "legkov777",
                subject = any(),
                body = any(),
                createdBy = "admin_user",
            )
        }
        verify { requestRepository.save(match { it.status == AccountStatusRequestStatus.PENDING && !it.requestedActive }) }
    }

    @Test
    fun `duplicate pending request is rejected`() {
        every { currentUserLogin() } returns admin.username
        every { accountService.getCurrentAccount() } returns admin
        every { accountService.getAccount(10) } returns target
        every {
            requestRepository.existsByTargetAccountIdAndRequestedActiveAndStatus(
                10,
                false,
                AccountStatusRequestStatus.PENDING,
            )
        } returns true

        assertThrows<InvalidRequestException> { service.requestOrApply(10, false) }
        verify(exactly = 0) { accountService.switchActiveState(any(), any()) }
    }

    @Test
    fun `approver flips immediately with DIRECT event`() {
        every { currentUserLogin() } returns approver.username
        every { accountService.getCurrentAccount() } returns approver
        every { accountService.getAccount(10) } returns target
        every { accountService.switchActiveState(10, false) } answers {
            target.active = false
            target
        }

        val result = service.requestOrApply(10, false)

        assertFalse(result.pending)
        assertFalse(result.active)
        verify { accountService.switchActiveState(10, false) }
        verify {
            eventRepository.save(
                match<AccountStatusEvent> {
                    it.source == AccountStatusEventSource.DIRECT && !it.activeTo && it.actorUsername == "legkov777"
                }
            )
        }
        verify(exactly = 0) { inboxService.notifyAccountStatusRequest(any(), any(), any(), any()) }
        verify(exactly = 0) { requestRepository.save(any()) }
    }

    @Test
    fun `OIDC email login is treated as approver`() {
        every { currentUserLogin() } returns "leonid@example.com"
        every { accountService.getCurrentAccount() } returns approver
        every { accountService.getAccount(10) } returns target
        every { accountService.switchActiveState(10, false) } answers {
            target.active = false
            target
        }

        val result = service.requestOrApply(10, false)

        assertFalse(result.pending)
        assertTrue(service.isApprover("leonid@example.com", approver))
        verify { accountService.switchActiveState(10, false) }
    }

    @Test
    fun `meta marks email login as account status approver`() {
        every { currentUserLogin() } returns "leonid@example.com"

        val meta = service.meta()

        assertTrue(meta.isAccountStatusApprover)
        assertEquals("legkov777", meta.approverUsername)
    }

    @Test
    fun `approve switches state and notifies requester`() {
        every { currentUserLogin() } returns approver.username
        every { accountService.getCurrentAccount() } returns approver
        val request = AccountStatusRequest(
            id = UUID.randomUUID(),
            targetAccount = target,
            requestedActive = false,
            createdBy = admin.username,
            reason = "need deactivate",
        )
        every { requestRepository.findById(request.id!!) } returns Optional.of(request)
        every { accountService.getAccount(10) } returns target
        every { accountService.switchActiveState(10, false) } answers {
            target.active = false
            target
        }

        val dto = service.approve(request.id!!, null)

        assertEquals(AccountStatusRequestStatus.APPROVED, dto.status)
        verify { accountService.switchActiveState(10, false) }
        verify {
            eventRepository.save(match<AccountStatusEvent> { it.source == AccountStatusEventSource.REQUEST })
        }
        verify {
            inboxService.notifyAccountStatusDecision(
                username = "admin_user",
                subject = any(),
                body = any(),
                createdBy = "legkov777",
            )
        }
    }

    @Test
    fun `reject does not flip and notifies requester`() {
        every { currentUserLogin() } returns approver.username
        every { accountService.getCurrentAccount() } returns approver
        val request = AccountStatusRequest(
            id = UUID.randomUUID(),
            targetAccount = target,
            requestedActive = false,
            createdBy = admin.username,
        )
        every { requestRepository.findById(request.id!!) } returns Optional.of(request)

        val dto = service.reject(request.id!!, null)

        assertEquals(AccountStatusRequestStatus.REJECTED, dto.status)
        verify(exactly = 0) { accountService.switchActiveState(any(), any()) }
        verify {
            inboxService.notifyAccountStatusDecision(
                username = "admin_user",
                subject = any(),
                body = any(),
                createdBy = "legkov777",
            )
        }
    }

    @Test
    fun `non-approver cannot approve`() {
        every { currentUserLogin() } returns admin.username
        assertThrows<NotAuthorizedException> { service.approve(UUID.randomUUID(), null) }
    }
}
