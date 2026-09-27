package rs.russian.portal.program.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import rs.russian.portal.program.repository.ProgramCuratorDelegateRepository
import rs.russian.portal.program.repository.ProgramCuratorRepository
import rs.russian.portal.program.repository.ProgramRepository
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.AccountService

class ProgramCuratorServiceTest {
    private val curatorRepository = mockk<ProgramCuratorRepository>()
    private val delegateRepository = mockk<ProgramCuratorDelegateRepository>()
    private val programRepository = mockk<ProgramRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val accountService = mockk<AccountService>()
    private val service = ProgramCuratorService(
        curatorRepository = curatorRepository,
        delegateRepository = delegateRepository,
        programRepository = programRepository,
        accountRepository = accountRepository,
        accountService = accountService,
    )

    @Test
    fun `listApprovers appends only portal moderators not WP administrator or main volunteer`() {
        every { programRepository.findAll() } returns emptyList()
        every { curatorRepository.findAllByOrderByProgramCodeAscUsernameAsc() } returns emptyList()
        every {
            delegateRepository.findAllByOrderByProgramCodeAscCuratorUsernameAscDelegateUsernameAsc()
        } returns emptyList()
        every { accountRepository.findAllByUsernameIn(emptyList()) } returns emptyList()

        val moderator = account(1, "moderator", "Модератор", UserGroup.ADMIN_VOLUNTEER)
        val sso = account(2, "sso-admin", "SSO Админ", UserGroup.ADMIN_SSO)
        every { accountRepository.findAllActiveByGroup(UserGroup.ADMIN_VOLUNTEER.name) } returns listOf(moderator)
        every { accountRepository.findAllActiveByGroup(UserGroup.ADMIN_SSO.name) } returns listOf(sso)

        val approvers = service.listApprovers()

        assertEquals(2, approvers.size)
        assertEquals(setOf("moderator", "sso-admin"), approvers.map { it.username }.toSet())
        assertTrue(approvers.all { it.role == "ADMIN" })
        verify(exactly = 0) { accountRepository.findAllActiveByGroup(UserGroup.ADMIN.name) }
        verify(exactly = 0) { accountRepository.findAllActiveByGroup(UserGroup.MAIN_VOLUNTEER.name) }
    }

    private fun account(id: Int, username: String, fullName: String, vararg groups: UserGroup) = Account(
        id = id,
        username = username,
        email = "$username@example.com",
        fullName = fullName,
        groups = groups.toSet(),
    )
}
