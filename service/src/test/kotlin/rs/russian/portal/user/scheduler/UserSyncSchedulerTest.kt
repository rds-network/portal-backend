package rs.russian.portal.user.scheduler

import io.authentik.model.User
import io.authentik.model.UserTypeEnum
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.domain.Specification
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.AccountService
import rs.russian.portal.user.service.authentik.AuthentikService
import rs.russian.portal.user.service.wordpress.MultiWordpressAccountSynchronizer
import java.time.LocalDateTime
import java.util.*

class UserSyncSchedulerTest {

    private lateinit var accountService: AccountService
    private lateinit var accountRepository: AccountRepository
    private lateinit var multiWordpressAccountSynchroniser: MultiWordpressAccountSynchronizer
    private lateinit var authentikUserService: AuthentikService
    private lateinit var userSyncScheduler: UserSyncScheduler

    @BeforeEach
    fun setUp() {
        accountService = mockk(relaxed = true)
        accountRepository = mockk(relaxed = true)
        multiWordpressAccountSynchroniser = mockk(relaxed = true)
        authentikUserService = mockk(relaxed = true)

        userSyncScheduler = UserSyncScheduler(
            accountService,
            mockk(relaxed = true),
            accountRepository,
            listOf(multiWordpressAccountSynchroniser),
            authentikUserService
        )
    }

    @Test
    fun `sync should process active users and sync to WordPress`() {
        // Arrange
        val user1 = createUser(1, "user1", UserTypeEnum.internal)
        val user2 = createUser(2, "user2", UserTypeEnum.external)
        val serviceAccount = createUser(3, "service", UserTypeEnum.service_account)
        val account1 = createAccount(1, "user1")
        val account2 = createAccount(2, "user2")

        every { authentikUserService.getAllUsers() } returns listOf(user1, user2, serviceAccount)
        every { accountService.createOrUpdateAccount(user1) } returns account1
        every { accountService.createOrUpdateAccount(user2) } returns account2
        every { accountRepository.findAll(any<Specification<Account>>()) } returns emptyList()

        // Act
        userSyncScheduler.sync()

        // Assert
        verify { authentikUserService.getAllUsers() }
        verify { accountService.createOrUpdateAccount(user1) }
        verify { accountService.createOrUpdateAccount(user2) }
        verify(exactly = 0) { accountService.createOrUpdateAccount(serviceAccount) }
        verify { multiWordpressAccountSynchroniser.sync(listOf(account1, account2)) }
        verify { accountRepository.findAll(any<Specification<Account>>()) }
    }

    @Test
    fun `sync should deactivate and delete inactive users`() {
        // Arrange
        val user1 = createUser(1, "user1", UserTypeEnum.internal)
        val account1 = createAccount(1, "user1")
        val inactiveAccount = createAccount(3, "inactive")

        every { authentikUserService.getAllUsers() } returns listOf(user1)
        every { accountService.createOrUpdateAccount(user1) } returns account1
        every { accountRepository.findAll(any<Specification<Account>>()) } returns listOf(inactiveAccount)

        // Act
        userSyncScheduler.sync()

        // Assert
        verify { authentikUserService.getAllUsers() }
        verify { accountService.createOrUpdateAccount(user1) }
        verify { multiWordpressAccountSynchroniser.sync(listOf(account1)) }

        verify { accountService.save(match { it.id == 3 && !it.active }) }
        verify { multiWordpressAccountSynchroniser.delete(inactiveAccount) }
    }

    private fun createUser(id: Int, username: String, type: UserTypeEnum) = User(
        pk = id,
        username = username,
        email = "$username@example.com",
        type = type,
        name = username,
        isSuperuser = false,
        groupsObj = null,
        avatar = "null",
        uid = username,
        uuid = UUID.randomUUID(),
        isActive = true,
    )

    private fun createAccount(id: Int, username: String) = Account(
        id = id,
        username = username,
        email = "$username@example.com",
        active = true,
        lastSynced = LocalDateTime.now(),
        fullName = username
    )
}
