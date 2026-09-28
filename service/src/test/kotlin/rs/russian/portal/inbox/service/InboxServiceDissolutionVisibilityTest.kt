package rs.russian.portal.inbox.service

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import rs.russian.portal.config.AppProperties
import rs.russian.portal.config.LeaveProperties
import rs.russian.portal.inbox.domain.InboxParticipant
import rs.russian.portal.inbox.domain.InboxThread
import rs.russian.portal.inbox.repository.InboxThreadRepository
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.AccountService
import java.util.Optional
import java.util.UUID

class InboxServiceDissolutionVisibilityTest {

    private val inboxThreadRepository = mockk<InboxThreadRepository>()
    private val accountService = mockk<AccountService>()
    private val accountRepository = mockk<AccountRepository>(relaxed = true)
    private val appProperties = AppProperties(
        frontendUri = "http://localhost:3000",
        leave = LeaveProperties(approverUsername = "legkov777"),
    )

    private val service = InboxService(
        inboxThreadRepository,
        accountService,
        accountRepository,
        appProperties,
    )

    private val approver = Account(
        id = 1,
        username = "legkov777",
        email = "approver@example.com",
        fullName = "Leonid",
        active = true,
    )
    private val requester = Account(
        id = 2,
        username = "andrei",
        email = "andrei@example.com",
        fullName = "Andrei",
        active = true,
    )
    private val stranger = Account(
        id = 3,
        username = "anna_curator",
        email = "anna@example.com",
        fullName = "Anna",
        active = true,
    )

    @Test
    fun `canSee DISSOLUTION_REQUEST only for approver or createdBy`() {
        val thread = dissolutionRequestThread(requester = "andrei", extra = listOf("anna_curator", "legkov777"))

        assertTrue(service.canSee(thread, "legkov777"))
        assertTrue(service.canSee(thread, "andrei"))
        assertFalse(service.canSee(thread, "anna_curator"))
        assertFalse(service.canSee(thread, "random"))
    }

    @Test
    fun `canSee DISSOLUTION_DECISION only for recipient or createdBy`() {
        val thread = dissolutionDecisionThread(volunteer = "andrei", approver = "legkov777", extra = listOf("anna_curator"))

        assertTrue(service.canSee(thread, "andrei"))
        assertTrue(service.canSee(thread, "legkov777"))
        assertFalse(service.canSee(thread, "anna_curator"))
        assertFalse(service.canSee(thread, "random"))
    }

    @Test
    fun `list hides dissolution request from stale participants`() {
        val leaked = dissolutionRequestThread(requester = "vera", extra = listOf("andrei", "legkov777"))
        every { accountService.getCurrentAccount() } returns requester
        every { inboxThreadRepository.findAllForUser("andrei") } returns listOf(leaked)
        every { accountRepository.findLastSeenByUsernames(any()) } returns emptyList()
        every { accountRepository.findAllByUsernameIn(any()) } returns emptyList()

        assertTrue(service.list().isEmpty())
    }

    @Test
    fun `list shows dissolution request to requester and approver`() {
        val thread = dissolutionRequestThread(requester = "andrei", extra = listOf("legkov777"))
        every { accountRepository.findLastSeenByUsernames(any()) } returns emptyList()
        every { accountRepository.findAllByUsernameIn(any()) } returns listOf(approver, requester)

        every { accountService.getCurrentAccount() } returns requester
        every { inboxThreadRepository.findAllForUser("andrei") } returns listOf(thread)
        assertTrue(service.list().any { it.id == thread.id })

        every { accountService.getCurrentAccount() } returns approver
        every { inboxThreadRepository.findAllForUser("legkov777") } returns listOf(thread)
        assertTrue(service.list().any { it.id == thread.id })
    }

    @Test
    fun `get rejects dissolution request for stale participant`() {
        val thread = dissolutionRequestThread(requester = "vera", extra = listOf("andrei", "legkov777"))
        every { accountService.getCurrentAccount() } returns requester
        every { inboxThreadRepository.findById(thread.id!!) } returns Optional.of(thread)

        assertThrows<NotAuthorizedException> { service.get(thread.id!!) }
    }

    private fun dissolutionRequestThread(requester: String, extra: List<String>): InboxThread {
        val thread = InboxThread(
            id = UUID.randomUUID(),
            subject = "Заявление на расторжение",
            kind = InboxThread.KIND_DISSOLUTION_REQUEST,
            createdBy = requester,
            recipient = "legkov777",
        )
        (extra + requester).distinct().forEach { login ->
            thread.participants.add(
                InboxParticipant(
                    thread = thread,
                    username = login,
                    unread = login.equals("legkov777", ignoreCase = true),
                )
            )
        }
        return thread
    }

    private fun dissolutionDecisionThread(volunteer: String, approver: String, extra: List<String>): InboxThread {
        val thread = InboxThread(
            id = UUID.randomUUID(),
            subject = "Заявление на расторжение согласовано",
            kind = InboxThread.KIND_DISSOLUTION_DECISION,
            createdBy = approver,
            recipient = volunteer,
        )
        (extra + volunteer + approver).distinct().forEach { login ->
            thread.participants.add(InboxParticipant(thread = thread, username = login, unread = false))
        }
        return thread
    }
}
