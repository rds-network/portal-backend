package rs.russian.portal.user.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import rs.russian.portal.program.domain.Program
import rs.russian.portal.program.repository.ProgramRepository
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.UserInfo
import rs.russian.portal.user.domain.UserSecondaryProgram
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.UserSecondaryProgramRepository

class SecondaryProgramServiceTest {
    private val accountService = mockk<AccountService>()
    private val programRepository = mockk<ProgramRepository>()
    private val secondaryProgramRepository = mockk<UserSecondaryProgramRepository>(relaxed = true)
    private val service = SecondaryProgramService(accountService, programRepository, secondaryProgramRepository)

    @Test
    fun `replace drops primary and unknown codes are rejected`() {
        val account = account(1, "vol").withProgram("IT")
        every { accountService.getAccount(1) } returns account
        every { programRepository.findByCode("MEDIA") } returns program("MEDIA")
        every { programRepository.findByCode("DESIGN") } returns null
        every { secondaryProgramRepository.findAllByAccountIdOrderByProgramCodeAsc(1) } returns emptyList()

        assertThrows<InvalidRequestException> {
            service.replace(1, listOf("IT", "MEDIA", "DESIGN"))
        }
    }

    @Test
    fun `replace excludes primary and persists the rest`() {
        val account = account(1, "vol").withProgram("IT")
        every { accountService.getAccount(1) } returns account
        every { programRepository.findByCode("MEDIA") } returns program("MEDIA")
        every { programRepository.findByCode("LAW") } returns program("LAW")
        every { secondaryProgramRepository.save(any()) } answers { firstArg() }
        every { secondaryProgramRepository.findAllByAccountIdOrderByProgramCodeAsc(1) } returns listOf(
            UserSecondaryProgram(accountId = 1, programCode = "LAW"),
            UserSecondaryProgram(accountId = 1, programCode = "MEDIA"),
        )

        val result = service.replace(1, listOf(" it ", "MEDIA", "LAW", "media"))

        verify { secondaryProgramRepository.deleteAllByAccountId(1) }
        verify(exactly = 2) { secondaryProgramRepository.save(any()) }
        assertEquals(listOf("LAW", "MEDIA"), result)
    }

    private fun account(id: Int, username: String) = Account(
        id = id,
        username = username,
        email = "$username@example.com",
        fullName = username,
        groups = setOf(UserGroup.VOLUNTEER),
    )

    private fun Account.withProgram(code: String) = also {
        it.info = UserInfo(id = it.username, account = it).apply {
            program = program(code)
        }
    }

    private fun program(code: String) = Program(code = code, nameRu = code, nameEn = code, nameSr = code)
}
