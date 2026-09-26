package rs.russian.portal.user.service

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import rs.russian.portal.program.domain.Program
import rs.russian.portal.program.service.ProgramCuratorService
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.UserInfo
import rs.russian.portal.user.domain.enums.UserGroup

class ReportControllerServiceTest {
    private val accountService = mockk<AccountService>()
    private val curatorService = mockk<ProgramCuratorService>()
    private val service = ReportControllerService(accountService, curatorService)

    private val volunteer = account(1, "volunteer", UserGroup.VOLUNTEER).withProgram("IT")
    private val controller = account(5, "controller", UserGroup.VOLUNTEER)

    @Test
    fun `moderator assigns a controller from another program`() {
        current(account(2, "moderator", UserGroup.ADMIN_VOLUNTEER))
        target(volunteer)
        known(controller)

        assertEquals("controller", service.setController(volunteer.id!!, " controller ").reportControllerUsername)
    }

    @Test
    fun `curator of the volunteer program may assign a controller`() {
        current(account(3, "curator", UserGroup.VOLUNTEER))
        target(volunteer)
        known(controller)
        every { curatorService.programCodesOf("curator") } returns listOf("IT")

        assertEquals("controller", service.setController(volunteer.id!!, "controller").reportControllerUsername)
    }

    @Test
    fun `curator of another program may not assign a controller`() {
        current(account(4, "other", UserGroup.VOLUNTEER))
        target(volunteer)
        every { curatorService.programCodesOf("other") } returns listOf("MEDIA")

        assertThrows<NotAuthorizedException> { service.setController(volunteer.id!!, "controller") }
    }

    @Test
    fun `a deactivated user may not be a controller`() {
        current(account(2, "moderator", UserGroup.ADMIN_VOLUNTEER))
        target(volunteer)
        known(controller.also { it.active = false })

        assertThrows<InvalidRequestException> { service.setController(volunteer.id!!, "controller") }
    }

    @Test
    fun `the controller may lift their own control without being a manager`() {
        val controlled = volunteer.also { it.reportControllerUsername = "controller" }
        current(controller)
        target(controlled)

        assertNull(service.clearController(controlled.id!!).reportControllerUsername)
    }

    @Test
    fun `an outsider may not lift the control`() {
        target(volunteer.also { it.reportControllerUsername = "controller" })
        current(account(6, "stranger", UserGroup.VOLUNTEER))
        every { curatorService.programCodesOf("stranger") } returns emptyList()

        assertThrows<NotAuthorizedException> { service.clearController(volunteer.id!!) }
    }

    private fun current(account: Account) {
        every { accountService.getCurrentAccount() } returns account
    }

    private fun target(account: Account) {
        every { accountService.getAccount(account.id!!) } returns account
    }

    private fun known(account: Account) {
        every { accountService.findAccountByLogin(account.username) } returns account
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
