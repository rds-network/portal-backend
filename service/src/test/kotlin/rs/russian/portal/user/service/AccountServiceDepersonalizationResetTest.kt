package rs.russian.portal.user.service

import io.authentik.model.User
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import rs.russian.generated.model.ContractDto
import rs.russian.generated.model.ContractTypeEnum
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.DepersonalizationStatus
import rs.russian.portal.user.mapper.ContractMapper
import rs.russian.portal.user.mapper.UserMapper
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.authentik.AuthentikService
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

/**
 * Unit coverage for the depersonalization state machine reset: a returning volunteer must leave the WARNED
 * state so the retention clock starts over, while a terminal DEPERSONALIZED account is never revived.
 */
class AccountServiceDepersonalizationResetTest {

    private lateinit var userMapper: UserMapper
    private lateinit var accountRepository: AccountRepository
    private lateinit var authentikUserService: AuthentikService
    private lateinit var contractMapper: ContractMapper
    private lateinit var service: AccountService

    @BeforeEach
    fun setUp() {
        userMapper = mockk(relaxed = true)
        accountRepository = mockk(relaxed = true)
        authentikUserService = mockk(relaxed = true)
        contractMapper = mockk(relaxed = true)
        service = AccountService(
            userMapper,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            contractMapper,
            accountRepository,
            authentikUserService,
            mockk(relaxed = true),
            mockk(relaxed = true),
        )
    }

    @Test
    fun `reactivating a WARNED account clears the pending depersonalization`() {
        val account = account(active = false, status = DepersonalizationStatus.WARNED)
        every { accountRepository.findById(account.id!!) } returns Optional.of(account)

        service.switchActiveState(account.id!!, true)

        assertEquals(DepersonalizationStatus.NONE, account.depersonalizationStatus)
    }

    @Test
    fun `reactivating never revives a terminal DEPERSONALIZED account`() {
        val account = account(active = false, status = DepersonalizationStatus.DEPERSONALIZED)
        every { accountRepository.findById(account.id!!) } returns Optional.of(account)

        service.switchActiveState(account.id!!, true)

        assertEquals(DepersonalizationStatus.DEPERSONALIZED, account.depersonalizationStatus)
    }

    @Test
    fun `adding a new contract to a WARNED account clears the pending depersonalization`() {
        val account = account(active = false, status = DepersonalizationStatus.WARNED)
        every { accountRepository.findById(account.id!!) } returns Optional.of(account)

        service.updateContracts(account.id!!, setOf(contractDto()))

        assertEquals(DepersonalizationStatus.NONE, account.depersonalizationStatus)
    }

    @Test
    fun `SSO sync never writes back over a depersonalized account`() {
        val account = account(active = false, status = DepersonalizationStatus.DEPERSONALIZED)
        val ssoUser = mockk<User>(relaxed = true)
        every { ssoUser.pk } returns account.id!!
        every { accountRepository.findById(account.id!!) } returns Optional.of(account)

        service.createOrUpdateAccount(ssoUser)

        // The sentinel identity must not be overwritten by the real data still held in Authentik.
        verify(exactly = 0) { userMapper.update(ssoUser, any<Account>()) }
        verify(exactly = 0) { accountRepository.saveAndFlush(any<Account>()) }
    }

    private fun account(active: Boolean, status: DepersonalizationStatus) = Account(
        id = 1,
        username = "volunteer",
        email = "volunteer@example.com",
        fullName = "Ivan Volunteer",
        active = active,
        depersonalizationStatus = status,
    )

    private fun contractDto() = ContractDto(
        id = UUID.randomUUID(),
        startDate = LocalDate.now().minusYears(1),
        endDate = LocalDate.now().plusYears(1),
        type = ContractTypeEnum.REGULAR,
    )
}
