package rs.russian.portal.program.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import rs.russian.portal.program.domain.Program
import rs.russian.portal.program.domain.ProgramCurator
import rs.russian.portal.program.domain.ProgramCuratorDelegate
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
    fun `listApprovers returns only curators and delegates without admin roles`() {
        val program = Program(
            code = "MEDIA",
            nameRu = "Медиа",
            nameEn = "Media",
            nameSr = "Mediji",
        )
        val curator = account(1, "curator", "Куратор", UserGroup.VOLUNTEER)
        val delegate = account(2, "delegate", "Делегат", UserGroup.VOLUNTEER)
        every { programRepository.findAll() } returns listOf(program)
        every { curatorRepository.findAllByOrderByProgramCodeAscUsernameAsc() } returns listOf(
            ProgramCurator(programCode = "MEDIA", username = "curator"),
        )
        every {
            delegateRepository.findAllByOrderByProgramCodeAscCuratorUsernameAscDelegateUsernameAsc()
        } returns listOf(
            ProgramCuratorDelegate(
                programCode = "MEDIA",
                curatorUsername = "curator",
                delegateUsername = "delegate",
            ),
        )
        every { accountRepository.findAllByUsernameIn(any()) } returns listOf(curator, delegate)

        val approvers = service.listApprovers()

        assertEquals(2, approvers.size)
        assertEquals(setOf("CURATOR", "DELEGATE"), approvers.map { it.role }.toSet())
        assertEquals(setOf("curator", "delegate"), approvers.map { it.username }.toSet())
        assertTrue(approvers.none { it.role == "ADMIN" })
        verify(exactly = 0) { accountRepository.findAllActiveByGroup(any()) }
    }

    @Test
    fun `listApprovers does not append portal moderators as admins`() {
        every { programRepository.findAll() } returns emptyList()
        every { curatorRepository.findAllByOrderByProgramCodeAscUsernameAsc() } returns emptyList()
        every {
            delegateRepository.findAllByOrderByProgramCodeAscCuratorUsernameAscDelegateUsernameAsc()
        } returns emptyList()
        every { accountRepository.findAllByUsernameIn(emptyList()) } returns emptyList()

        val approvers = service.listApprovers()

        assertTrue(approvers.isEmpty())
        verify(exactly = 0) { accountRepository.findAllActiveByGroup(any()) }
    }

    private fun account(id: Int, username: String, fullName: String, vararg groups: UserGroup) = Account(
        id = id,
        username = username,
        email = "$username@example.com",
        fullName = fullName,
        groups = groups.toSet(),
    )
}
