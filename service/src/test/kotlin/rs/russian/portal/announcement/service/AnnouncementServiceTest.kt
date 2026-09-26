package rs.russian.portal.announcement.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import rs.russian.generated.model.AnnouncementCreateRequest
import rs.russian.generated.model.AnnouncementDto
import rs.russian.generated.model.UnreadAnnouncementsCountDto
import rs.russian.generated.model.AnnouncementAudience as ApiAnnouncementAudience
import rs.russian.portal.announcement.domain.Announcement
import rs.russian.portal.announcement.domain.enums.AnnouncementAudience
import rs.russian.portal.announcement.mapper.AnnouncementMapper
import rs.russian.portal.announcement.repository.AnnouncementReadRepository
import rs.russian.portal.announcement.repository.AnnouncementRepository
import rs.russian.portal.program.domain.Program
import rs.russian.portal.program.repository.ProgramRepository
import rs.russian.portal.program.service.ProgramCuratorService
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.shared.security.currentUserRoles
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.UserInfo
import rs.russian.portal.user.service.AccountService
import java.util.Optional
import java.util.UUID

class AnnouncementServiceTest {

    private lateinit var announcementRepository: AnnouncementRepository
    private lateinit var announcementReadRepository: AnnouncementReadRepository
    private lateinit var announcementMapper: AnnouncementMapper
    private lateinit var accountService: AccountService
    private lateinit var programRepository: ProgramRepository
    private lateinit var programCuratorService: ProgramCuratorService
    private lateinit var announcementService: AnnouncementService

    @BeforeEach
    fun setup() {
        announcementRepository = mockk()
        announcementReadRepository = mockk()
        announcementMapper = mockk()
        accountService = mockk()
        programRepository = mockk()
        programCuratorService = mockk()
        announcementService = AnnouncementService(
            announcementRepository,
            announcementReadRepository,
            announcementMapper,
            accountService,
            programRepository,
            programCuratorService,
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }


    @Test
    fun `getForCurrentUser should return empty list when account is inactive`() {
        val account = account(active = false)
        every { accountService.getCurrentAccount() } returns account

        val result = announcementService.getForCurrentUser()

        assertTrue(result.isEmpty())
        verify(exactly = 0) { announcementRepository.findForUser(any(), any()) }
    }

    @Test
    fun `getForCurrentUser should return announcements for active account without program`() {
        val account = account()
        val announcement = announcement(audience = AnnouncementAudience.ALL)
        val dto = mockk<AnnouncementDto>()

        every { accountService.getCurrentAccount() } returns account
        every { announcementReadRepository.findAnnouncementIdsByAccountId(ACCOUNT_ID) } returns emptyList()
        every { announcementRepository.findForUser(null, "user") } returns listOf(announcement)
        every { announcementMapper.map(announcement, false) } returns dto

        val result = announcementService.getForCurrentUser()

        assertEquals(listOf(dto), result)
        verify(exactly = 1) { announcementRepository.findForUser(null, "user") }
    }

    @Test
    fun `getForCurrentUser should mark announcement as read when its id is in read set`() {
        val account = account()
        val announcement = announcement(audience = AnnouncementAudience.ALL)
        val dto = mockk<AnnouncementDto>()

        every { accountService.getCurrentAccount() } returns account
        every { announcementReadRepository.findAnnouncementIdsByAccountId(ACCOUNT_ID) } returns listOf(announcement.id!!)
        every { announcementRepository.findForUser(null, "user") } returns listOf(announcement)
        every { announcementMapper.map(announcement, true) } returns dto

        val result = announcementService.getForCurrentUser()

        assertEquals(listOf(dto), result)
        verify(exactly = 1) { announcementMapper.map(announcement, true) }
    }

    @Test
    fun `getForCurrentUser should pass program code from account info to repository`() {
        val program = program("IT")
        val account = accountWithProgram(program)
        val announcement = announcement(audience = AnnouncementAudience.PROGRAM, program = program)
        val dto = mockk<AnnouncementDto>()

        every { accountService.getCurrentAccount() } returns account
        every { announcementReadRepository.findAnnouncementIdsByAccountId(ACCOUNT_ID) } returns emptyList()
        every { announcementRepository.findForUser("IT", "user") } returns listOf(announcement)
        every { announcementMapper.map(announcement, false) } returns dto

        val result = announcementService.getForCurrentUser()

        assertEquals(listOf(dto), result)
        verify(exactly = 1) { announcementRepository.findForUser("IT", "user") }
    }


    @Test
    fun `getUnreadCount should return 0 when account is inactive`() {
        val account = account(active = false)
        every { accountService.getCurrentAccount() } returns account

        val result = announcementService.getUnreadCount()

        assertEquals(UnreadAnnouncementsCountDto(0), result)
        verify(exactly = 0) { announcementRepository.countUnreadForUser(any(), any(), any()) }
    }

    @Test
    fun `getUnreadCount should return count from repository for active account without program`() {
        val account = account()
        every { accountService.getCurrentAccount() } returns account
        every { announcementRepository.countUnreadForUser(null, "user", ACCOUNT_ID) } returns 3L

        val result = announcementService.getUnreadCount()

        assertEquals(UnreadAnnouncementsCountDto(3), result)
        verify(exactly = 1) { announcementRepository.countUnreadForUser(null, "user", ACCOUNT_ID) }
    }

    @Test
    fun `getUnreadCount should pass program code from account info to repository`() {
        val program = program("IT")
        val account = accountWithProgram(program)
        every { accountService.getCurrentAccount() } returns account
        every { announcementRepository.countUnreadForUser("IT", "user", ACCOUNT_ID) } returns 5L

        val result = announcementService.getUnreadCount()

        assertEquals(UnreadAnnouncementsCountDto(5), result)
        verify(exactly = 1) { announcementRepository.countUnreadForUser("IT", "user", ACCOUNT_ID) }
    }


    @Test
    fun `markRead should throw EntityNotFoundException when announcement not found`() {
        val id = UUID.randomUUID()
        every { accountService.getCurrentAccount() } returns account()
        every { announcementRepository.findById(id) } returns Optional.empty()

        assertThrows<EntityNotFoundException> { announcementService.markRead(id) }
        verify(exactly = 0) { announcementReadRepository.insertIfAbsent(any(), any(), any()) }
    }

    @Test
    fun `markRead should throw InvalidRequestException when account is inactive`() {
        val id = UUID.randomUUID()
        val announcement = announcement(id = id, audience = AnnouncementAudience.ALL)
        every { accountService.getCurrentAccount() } returns account(active = false)
        every { announcementRepository.findById(id) } returns Optional.of(announcement)

        assertThrows<InvalidRequestException> { announcementService.markRead(id) }
        verify(exactly = 0) { announcementReadRepository.insertIfAbsent(any(), any(), any()) }
    }

    @Test
    fun `markRead should throw InvalidRequestException when announcement program does not match account program`() {
        val id = UUID.randomUUID()
        val announcement = announcement(id = id, audience = AnnouncementAudience.PROGRAM, program = program("RS"))
        every { accountService.getCurrentAccount() } returns accountWithProgram(program("IT"))
        every { announcementRepository.findById(id) } returns Optional.of(announcement)

        assertThrows<InvalidRequestException> { announcementService.markRead(id) }
        verify(exactly = 0) { announcementReadRepository.insertIfAbsent(any(), any(), any()) }
    }

    @Test
    fun `markRead should call insertIfAbsent for ALL audience announcement`() {
        val id = UUID.randomUUID()
        val announcement = announcement(id = id, audience = AnnouncementAudience.ALL)
        every { accountService.getCurrentAccount() } returns account()
        every { announcementRepository.findById(id) } returns Optional.of(announcement)
        every { announcementReadRepository.insertIfAbsent(any(), id, ACCOUNT_ID) } returns 1

        announcementService.markRead(id)

        verify(exactly = 1) { announcementReadRepository.insertIfAbsent(any(), id, ACCOUNT_ID) }
    }

    @Test
    fun `markRead should call insertIfAbsent when PROGRAM announcement matches account program`() {
        val id = UUID.randomUUID()
        val program = program("IT")
        val announcement = announcement(id = id, audience = AnnouncementAudience.PROGRAM, program = program)
        every { accountService.getCurrentAccount() } returns accountWithProgram(program)
        every { announcementRepository.findById(id) } returns Optional.of(announcement)
        every { announcementReadRepository.insertIfAbsent(any(), id, ACCOUNT_ID) } returns 1

        announcementService.markRead(id)

        verify(exactly = 1) { announcementReadRepository.insertIfAbsent(any(), id, ACCOUNT_ID) }
    }


    @Test
    fun `create should throw InvalidRequestException when audience is PROGRAM and programCode is null`() {
        val request = AnnouncementCreateRequest(
            title = "Title",
            body = "Body",
            audience = ApiAnnouncementAudience.PROGRAM,
            programCode = null,
        )

        assertThrows<InvalidRequestException> { announcementService.create(request) }
        verify(exactly = 0) { announcementRepository.save(any()) }
    }

    @Test
    fun `create should throw InvalidRequestException when audience is PROGRAM and programCode is blank`() {
        val request = AnnouncementCreateRequest(
            title = "Title",
            body = "Body",
            audience = ApiAnnouncementAudience.PROGRAM,
            programCode = "   ",
        )

        assertThrows<InvalidRequestException> { announcementService.create(request) }
        verify(exactly = 0) { announcementRepository.save(any()) }
    }

    @Test
    fun `create should throw InvalidRequestException when program code is not found`() {
        mockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
        every { currentUserRoles() } returns setOf(UserGroup.ADMIN)
        every { programRepository.findByCode("UNKNOWN") } returns null

        val request = AnnouncementCreateRequest(
            title = "Title",
            body = "Body",
            audience = ApiAnnouncementAudience.PROGRAM,
            programCode = "UNKNOWN",
        )

        assertThrows<InvalidRequestException> { announcementService.create(request) }
        verify(exactly = 0) { announcementRepository.save(any()) }
    }

    @Test
    fun `create should throw NotAuthorizedException when no authenticated user`() {
        mockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
        every { currentUserRoles() } returns setOf(UserGroup.ADMIN)
        every { currentUserLogin() } returns null

        val request = AnnouncementCreateRequest(
            title = "Title",
            body = "Body",
            audience = ApiAnnouncementAudience.ALL,
        )

        assertThrows<NotAuthorizedException> { announcementService.create(request) }
        verify(exactly = 0) { announcementRepository.save(any()) }
    }

    @Test
    fun `create should save announcement with ALL audience and trim whitespace`() {
        mockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
        every { currentUserRoles() } returns setOf(UserGroup.ADMIN)
        every { currentUserLogin() } returns "admin"

        val saved = announcement(audience = AnnouncementAudience.ALL)
        val dto = mockk<AnnouncementDto>()
        every { announcementRepository.save(any()) } returns saved
        every { announcementMapper.map(saved, false) } returns dto

        val result = announcementService.create(
            AnnouncementCreateRequest(
                title = "  Title  ",
                body = "  Body  ",
                audience = ApiAnnouncementAudience.ALL,
            )
        )

        assertEquals(dto, result)
        verify(exactly = 1) {
            announcementRepository.save(match {
                it.title == "Title" && it.body == "Body"
                    && it.audience == AnnouncementAudience.ALL
                    && it.program == null
                    && it.createdBy == "admin"
            })
        }
    }

    @Test
    fun `create should save announcement with PROGRAM audience and resolved program`() {
        mockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
        every { currentUserRoles() } returns setOf(UserGroup.ADMIN)
        every { currentUserLogin() } returns "admin"

        val program = program("IT")
        val saved = announcement(audience = AnnouncementAudience.PROGRAM, program = program)
        val dto = mockk<AnnouncementDto>()
        every { programRepository.findByCode("IT") } returns program
        every { announcementRepository.save(any()) } returns saved
        every { announcementMapper.map(saved, false) } returns dto

        val result = announcementService.create(
            AnnouncementCreateRequest(
                title = "Title",
                body = "Body",
                audience = ApiAnnouncementAudience.PROGRAM,
                programCode = "IT",
            )
        )

        assertEquals(dto, result)
        verify(exactly = 1) {
            announcementRepository.save(match {
                it.audience == AnnouncementAudience.PROGRAM && it.program?.code == "IT"
            })
        }
    }


    @Test
    fun `listManage should map announcements to manage dto`() {
        val program = program("IT")
        val announcement = announcement(audience = AnnouncementAudience.PROGRAM, program = program)
        announcement.banner = true
        every { announcementRepository.findAllForManage() } returns listOf(announcement)

        val result = announcementService.listManage()

        assertEquals(1, result.size)
        val dto = result.first()
        assertEquals(announcement.id, dto.id)
        assertEquals("Title", dto.title)
        assertEquals("admin", dto.createdBy)
        assertEquals("PROGRAM", dto.audience)
        assertEquals("IT", dto.programCode)
        assertTrue(dto.banner)
    }

    @Test
    fun `softDelete should throw EntityNotFoundException when announcement not found`() {
        val id = UUID.randomUUID()
        every { announcementRepository.findById(id) } returns Optional.empty()

        assertThrows<EntityNotFoundException> { announcementService.softDelete(id) }
        verify(exactly = 0) { announcementRepository.save(any()) }
    }

    @Test
    fun `softDelete should deactivate announcement`() {
        val id = UUID.randomUUID()
        val announcement = announcement(id = id)
        every { announcementRepository.findById(id) } returns Optional.of(announcement)
        every { announcementRepository.save(announcement) } returns announcement

        announcementService.softDelete(id)

        assertFalse(announcement.active)
        verify(exactly = 1) { announcementRepository.save(match { !it.active }) }
    }


    private companion object {
        const val ACCOUNT_ID = 1

        fun account(active: Boolean = true) = Account(
            id = ACCOUNT_ID,
            username = "user",
            email = "user@example.com",
            fullName = "Test User",
            active = active,
        )

        fun accountWithProgram(program: Program): Account {
            val account = account()
            account.info = UserInfo(id = account.username, account = account, program = program)
            return account
        }

        fun program(code: String) = Program(
            code = code,
            nameRu = code,
            nameEn = code,
            nameSr = code,
        )

        fun announcement(
            id: UUID = UUID.randomUUID(),
            audience: AnnouncementAudience = AnnouncementAudience.ALL,
            program: Program? = null,
        ) = Announcement(
            id = id,
            createdBy = "admin",
            title = "Title",
            body = "Body",
            audience = audience,
            program = program,
        )
    }
}
