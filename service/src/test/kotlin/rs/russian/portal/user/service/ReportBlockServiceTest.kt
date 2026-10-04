package rs.russian.portal.user.service

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import rs.russian.portal.program.domain.Program
import rs.russian.portal.program.service.ProgramCuratorService
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.UserInfo
import rs.russian.portal.user.domain.enums.UserGroup

class ReportBlockServiceTest {
    private val accountService = mockk<AccountService>()
    private val curatorService = mockk<ProgramCuratorService>()
    private val service = ReportBlockService(accountService, curatorService)

    private val volunteer = account(1, "volunteer", UserGroup.VOLUNTEER).withProgram("IT")

    @Test
    fun `moderator blocks a volunteer and the reason is stored`() {
        current(account(2, "moderator", UserGroup.ADMIN_VOLUNTEER))
        target(volunteer)

        val blocked = service.block(volunteer.id!!, "  не выходит на связь  ")

        assertTrue(blocked.reportBlocked)
        assertEquals("moderator", blocked.reportBlockedBy)
        assertEquals("не выходит на связь", blocked.reportBlockedReason)
    }

    @Test
    fun `curator of the volunteer program may block`() {
        current(account(3, "curator", UserGroup.VOLUNTEER))
        target(volunteer)
        every { curatorService.programCodesOf("curator") } returns listOf("IT")

        assertTrue(service.block(volunteer.id!!, null).reportBlocked)
    }

    @Test
    fun `curator of another program may not block`() {
        current(account(4, "other", UserGroup.VOLUNTEER))
        target(volunteer)
        every { curatorService.programCodesOf("other") } returns listOf("MEDIA")

        assertThrows<NotAuthorizedException> { service.block(volunteer.id!!, null) }
    }

    @Test
    fun `the person who set the block may lift it without being a manager`() {
        val blocked = volunteer.also {
            it.reportBlocked = true
            it.reportBlockedBy = "curator"
            it.reportBlockedReason = "нет отчётов"
        }
        current(account(3, "curator", UserGroup.VOLUNTEER))
        target(blocked)

        val cleared = service.unblock(blocked.id!!)

        assertFalse(cleared.reportBlocked)
        assertNull(cleared.reportBlockedBy)
        assertNull(cleared.reportBlockedReason)
        assertNull(cleared.reportBlockedAt)
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

    private fun Account.withProgram(code: String) = also {
        it.info = UserInfo(id = it.username, account = it).apply {
            program = Program(code = code, nameRu = code, nameEn = code, nameSr = code)
        }
    }
}
