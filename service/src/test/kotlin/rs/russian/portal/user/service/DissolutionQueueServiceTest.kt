package rs.russian.portal.user.service

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup

class DissolutionQueueServiceTest {
    private val accountService = mockk<AccountService>()
    private val service = DissolutionQueueService(accountService)

    private val volunteer = account(1, "volunteer", UserGroup.VOLUNTEER)

    @Test
    fun `manager enqueues with reason and username`() {
        current(account(2, "moderator", UserGroup.ADMIN_VOLUNTEER))
        target(volunteer)

        val queued = service.enqueue(volunteer.id!!, "  просрочка отчётов  ")

        assertNotNull(queued.dissolutionQueuedAt)
        assertEquals("moderator", queued.dissolutionQueuedBy)
        assertEquals("просрочка отчётов", queued.dissolutionQueueReason)
    }

    @Test
    fun `manager clears queue fields`() {
        volunteer.dissolutionQueuedAt = java.time.OffsetDateTime.now()
        volunteer.dissolutionQueuedBy = "moderator"
        volunteer.dissolutionQueueReason = "reason"
        current(account(2, "moderator", UserGroup.MAIN_VOLUNTEER))
        target(volunteer)

        val cleared = service.dequeue(volunteer.id!!)

        assertNull(cleared.dissolutionQueuedAt)
        assertNull(cleared.dissolutionQueuedBy)
        assertNull(cleared.dissolutionQueueReason)
    }

    @Test
    fun `non-manager may not enqueue`() {
        current(account(3, "volunteer2", UserGroup.VOLUNTEER))
        target(volunteer)

        assertThrows<NotAuthorizedException> { service.enqueue(volunteer.id!!, null) }
    }

    @Test
    fun `enqueueAccount sets queue fields without manager check`() {
        val queued = service.enqueueAccount(volunteer, "curator_it", "  заявление  ")

        assertNotNull(queued.dissolutionQueuedAt)
        assertEquals("curator_it", queued.dissolutionQueuedBy)
        assertEquals("заявление", queued.dissolutionQueueReason)
    }

    private fun current(account: Account) {
        every { accountService.getCurrentAccount() } returns account
    }

    private fun target(account: Account) {
        every { accountService.getAccount(account.id!!) } returns account
    }

    private fun account(id: Int, username: String, vararg groups: UserGroup) = Account(
        id = id,
        username = username,
        email = "$username@example.com",
        fullName = username.replaceFirstChar { it.uppercase() },
        groups = groups.toSet(),
    )
}
