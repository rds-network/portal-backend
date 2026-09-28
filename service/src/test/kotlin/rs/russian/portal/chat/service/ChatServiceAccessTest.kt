package rs.russian.portal.chat.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.Pageable
import rs.russian.portal.chat.api.ChatSendMessageRequest
import rs.russian.portal.chat.domain.ChatMessage
import rs.russian.portal.chat.domain.ChatRoom
import rs.russian.portal.chat.domain.enums.ChatRoomType
import rs.russian.portal.chat.repository.ChatMessageRepository
import rs.russian.portal.chat.repository.ChatRoomRepository
import rs.russian.portal.program.domain.Program
import rs.russian.portal.program.repository.ProgramCuratorRepository
import rs.russian.portal.program.repository.ProgramRepository
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.PrivilegedOps
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.shared.security.currentUserRoles
import rs.russian.portal.shared.security.realUserLogin
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.UserInfo
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.repository.UserSecondaryProgramRepository
import rs.russian.portal.user.service.AccountService
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class ChatServiceAccessTest {

    private val chatRoomRepository = mockk<ChatRoomRepository>()
    private val chatMessageRepository = mockk<ChatMessageRepository>()
    private val accountService = mockk<AccountService>()
    private val accountRepository = mockk<AccountRepository>()
    private val programRepository = mockk<ProgramRepository>()
    private val programCuratorRepository = mockk<ProgramCuratorRepository>(relaxed = true)
    private val secondaryProgramRepository = mockk<UserSecondaryProgramRepository>(relaxed = true)

    private val service = ChatService(
        chatRoomRepository,
        chatMessageRepository,
        accountService,
        accountRepository,
        programRepository,
        programCuratorRepository,
        secondaryProgramRepository,
    )

    private val law = Program(code = "LAW", nameRu = "Право", nameEn = "Law", nameSr = "Pravo")
    private val media = Program(code = "MEDIA", nameRu = "Медиа", nameEn = "Media", nameSr = "Mediji")

    private val general = ChatRoom(
        id = UUID.randomUUID(),
        type = ChatRoomType.GENERAL,
        title = "Общий чат",
        createdBy = "system",
    )
    private val lawRoom = ChatRoom(
        id = UUID.randomUUID(),
        type = ChatRoomType.PROGRAM,
        program = law,
        title = "Чат · Право",
        createdBy = "admin",
    )
    private val mediaRoom = ChatRoom(
        id = UUID.randomUUID(),
        type = ChatRoomType.PROGRAM,
        program = media,
        title = "Чат · Медиа",
        createdBy = "admin",
    )

    @BeforeEach
    fun setUp() {
        PrivilegedOps.approverUsername = "legkov777"
        PrivilegedOps.accountLookup = null
        mockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
        every { chatRoomRepository.findAllWithProgram() } returns listOf(general, lawRoom, mediaRoom)
        every { secondaryProgramRepository.findAllByAccountIdOrderByProgramCodeAsc(any()) } returns emptyList()
        every { programCuratorRepository.existsByUsernameIgnoreCase(any()) } returns false
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
    }

    @Test
    fun `GENERAL visible to active volunteer`() {
        val volunteer = account("vol1", program = law)
        loginAs(volunteer, emptySet())

        val result = service.listRooms()

        assertTrue(result.rooms.any { it.type == "GENERAL" })
        assertFalse(result.canSeeAll)
        assertEquals(setOf("GENERAL", "PROGRAM"), result.rooms.map { it.type }.toSet())
        assertTrue(result.rooms.any { it.programCode == "LAW" })
        assertFalse(result.rooms.any { it.programCode == "MEDIA" })
    }

    @Test
    fun `PROGRAM room gated by user program`() {
        val volunteer = account("vol2", program = media)
        loginAs(volunteer, emptySet())

        every { chatRoomRepository.findByIdWithProgram(lawRoom.id!!) } returns Optional.of(lawRoom)

        assertThrows<NotAuthorizedException> {
            service.assertCanAccessRoom(volunteer, lawRoom)
        }
        service.assertCanAccessRoom(volunteer, mediaRoom)
        service.assertCanAccessRoom(volunteer, general)
    }

    @Test
    fun `privileged Leonid sees all rooms`() {
        val leonid = account("legkov777", program = law, groups = setOf(UserGroup.ADMIN_VOLUNTEER))
        loginAs(leonid, setOf(UserGroup.ADMIN_VOLUNTEER))
        every { realUserLogin() } returns "legkov777"

        val result = service.listRooms()

        assertTrue(result.canSeeAll)
        assertEquals(3, result.rooms.size)
        assertTrue(result.rooms.any { it.programCode == "MEDIA" })
    }

    @Test
    fun `ADMIN_SSO sees all rooms`() {
        val admin = account("sso_admin", program = null, groups = setOf(UserGroup.ADMIN_SSO))
        loginAs(admin, setOf(UserGroup.ADMIN_SSO))
        every { realUserLogin() } returns "sso_admin"

        val result = service.listRooms()

        assertTrue(result.canSeeAll)
        assertEquals(3, result.rooms.size)
    }

    @Test
    fun `listMembers marks online within 5 minutes and sorts online first`() {
        val viewer = account("viewer", program = law)
        loginAs(viewer, emptySet())
        every { chatRoomRepository.findByIdWithProgram(general.id!!) } returns Optional.of(general)

        val online = account("online_user", program = law, fullName = "Я Online").apply {
            lastSeenAt = LocalDateTime.now().minusMinutes(2)
        }
        val offline = account("offline_user", program = law, fullName = "А Offline").apply {
            lastSeenAt = LocalDateTime.now().minusHours(3)
        }
        val never = account("never_user", program = law, fullName = "Б Never")

        every { accountRepository.findActiveAccounts(any<Pageable>()) } returns listOf(offline, never, online)

        val members = service.listMembers(general.id!!)

        assertEquals(listOf("online_user", "offline_user", "never_user"), members.map { it.username })
        assertTrue(members[0].online)
        assertNull(members[0].seenLabel)
        assertFalse(members[1].online)
        assertTrue(members[1].seenLabel!!.contains("ч назад"))
        assertEquals("давно не был", members[2].seenLabel)
    }

    @Test
    fun `sendMessage allows empty body when imageUrl present`() {
        val volunteer = account("vol_img", program = law)
        loginAs(volunteer, emptySet())
        every { chatRoomRepository.findByIdWithProgram(general.id!!) } returns Optional.of(general)

        val savedId = UUID.randomUUID()
        every { chatMessageRepository.save(any()) } answers {
            firstArg<ChatMessage>().also { it.id = savedId }
        }

        val dto = service.sendMessage(
            general.id!!,
            ChatSendMessageRequest(body = "  ", imageUrl = "https://cdn.example/a.png"),
        )

        assertEquals(savedId, dto.id)
        assertEquals("", dto.body)
        assertEquals("https://cdn.example/a.png", dto.imageUrl)
        assertTrue(dto.mine)
    }

    @Test
    fun `sendMessage rejects blank body without imageUrl`() {
        val volunteer = account("vol_blank", program = law)
        loginAs(volunteer, emptySet())
        every { chatRoomRepository.findByIdWithProgram(general.id!!) } returns Optional.of(general)

        assertThrows<InvalidRequestException> {
            service.sendMessage(general.id!!, ChatSendMessageRequest(body = "   "))
        }
    }

    @Test
    fun `touchPresence updates lastSeen`() {
        val volunteer = account("vol_presence", program = law)
        loginAs(volunteer, emptySet())
        every { accountRepository.touchLastSeen(any(), any(), any()) } returns 1

        service.touchPresence()

        verify(exactly = 1) { accountRepository.touchLastSeen(eq("vol_presence"), any(), any()) }
    }

    private fun loginAs(account: Account, roles: Set<UserGroup>) {
        every { currentUserLogin() } returns account.username
        every { currentUserRoles() } returns roles
        every { realUserLogin() } returns account.username
        every { accountService.getCurrentAccount() } returns account
    }

    private fun account(
        username: String,
        program: Program?,
        groups: Set<UserGroup> = emptySet(),
        fullName: String = username,
    ): Account {
        val acc = Account(
            id = username.hashCode().and(0x7fffffff),
            username = username,
            email = "$username@example.com",
            fullName = fullName,
            active = true,
            groups = groups,
        )
        if (program != null) {
            acc.info = UserInfo(id = username, account = acc, program = program)
        }
        return acc
    }
}
