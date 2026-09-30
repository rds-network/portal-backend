package rs.russian.portal.talent.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rs.russian.portal.inbox.service.InboxService
import rs.russian.portal.program.repository.ProgramRepository
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.talent.api.TalentPostCreateRequest
import rs.russian.portal.talent.api.TalentResponseCreateRequest
import rs.russian.portal.talent.api.TalentSkillsUpdateRequest
import rs.russian.portal.talent.domain.TalentPost
import rs.russian.portal.talent.domain.TalentResponse
import rs.russian.portal.talent.domain.UserSkill
import rs.russian.portal.talent.domain.enums.TalentPostStatus
import rs.russian.portal.talent.domain.enums.TalentPostType
import rs.russian.portal.talent.repository.TalentPostRepository
import rs.russian.portal.talent.repository.TalentResponseRepository
import rs.russian.portal.talent.repository.UserSkillRepository
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.AccountService
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

class TalentServiceTest {

    private val talentPostRepository = mockk<TalentPostRepository>()
    private val talentResponseRepository = mockk<TalentResponseRepository>()
    private val userSkillRepository = mockk<UserSkillRepository>(relaxed = true)
    private val programRepository = mockk<ProgramRepository>(relaxed = true)
    private val accountRepository = mockk<AccountRepository>(relaxed = true)
    private val accountService = mockk<AccountService>()
    private val inboxService = mockk<InboxService>(relaxed = true)

    private val service = TalentService(
        talentPostRepository,
        talentResponseRepository,
        userSkillRepository,
        programRepository,
        accountRepository,
        accountService,
        inboxService,
    )

    private val author = Account(
        id = 1,
        username = "author",
        email = "author@example.com",
        fullName = "Author Name",
        active = true,
        groups = emptySet(),
    )
    private val responder = Account(
        id = 2,
        username = "helper",
        email = "helper@example.com",
        fullName = "Helper Name",
        active = true,
        groups = emptySet(),
    )

    @BeforeEach
    fun setUp() {
        mockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
        every { talentPostRepository.save(any()) } answers { firstArg() }
        every { talentResponseRepository.save(any()) } answers { firstArg() }
        every { userSkillRepository.save(any()) } answers { firstArg() }
        every { accountRepository.findByUsername(any()) } answers {
            val login = firstArg<String>()
            when {
                login.equals("author", true) -> Optional.of(author)
                login.equals("helper", true) -> Optional.of(responder)
                else -> Optional.empty()
            }
        }
        every { accountRepository.findAllByUsernameIn(any()) } answers {
            firstArg<List<String>>().mapNotNull { login ->
                when {
                    login.equals("author", true) -> author
                    login.equals("helper", true) -> responder
                    else -> null
                }
            }
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
    }

    @Test
    fun `createPost stores skills and returns dto`() {
        every { currentUserLogin() } returns author.username
        every { accountService.getCurrentAccount() } returns author
        every { talentResponseRepository.existsByPost_IdAndAuthorUsernameIgnoreCase(any(), any()) } returns false
        every { accountRepository.findAllActiveUsernames() } returns listOf("author", "helper")
        every { inboxService.notifyTalentNewPost(any(), any(), any(), any()) } returns Unit

        val dto = service.createPost(
            TalentPostCreateRequest(
                type = TalentPostType.NEED_PEOPLE,
                title = "Need designer",
                body = "Looking for a volunteer designer",
                city = "Belgrade",
                skills = listOf("design", "figma", "design"),
            )
        )

        assertEquals(TalentPostType.NEED_PEOPLE, dto.type)
        assertEquals("Need designer", dto.title)
        assertEquals(listOf("design", "figma"), dto.skills)
        assertEquals("author", dto.authorUsername)
        assertTrue(dto.mine)
        verify(exactly = 1) { talentPostRepository.save(any()) }
        verify(exactly = 1) {
            inboxService.notifyTalentNewPost(
                subject = match { it.contains("Need designer") },
                body = match { it.contains("/ideas?post=") },
                createdBy = "author",
                recipients = listOf("author", "helper"),
            )
        }
    }

    @Test
    fun `backfillInboxForOpenPosts skips posts that already have a notice`() {
        val post = TalentPost(
            id = UUID.randomUUID(),
            type = TalentPostType.NEED_PEOPLE,
            status = TalentPostStatus.OPEN,
            title = "Need help",
            body = "body",
            authorUsername = author.username,
        )
        every {
            talentPostRepository.findAll(any<org.springframework.data.jpa.domain.Specification<TalentPost>>())
        } returns listOf(post)
        every { accountRepository.findAllActiveUsernames() } returns listOf("author", "helper")
        every { inboxService.purgeIncompleteTalentPostNotices(post.id!!) } returns Unit
        every { inboxService.hasTalentPostNotice(post.id!!) } returns true

        assertEquals(0, service.backfillInboxForOpenPosts())
        verify(exactly = 0) { inboxService.notifyTalentNewPost(any(), any(), any(), any()) }
    }

    @Test
    fun `createResponse notifies post author via inbox`() {
        every { currentUserLogin() } returns responder.username
        every { accountService.getCurrentAccount() } returns responder

        val postId = UUID.randomUUID()
        val post = TalentPost(
            id = postId,
            type = TalentPostType.PROJECT_IDEA,
            status = TalentPostStatus.OPEN,
            title = "Community garden",
            body = "Let's build a garden",
            authorUsername = author.username,
            responseCount = 0,
        )
        every { talentPostRepository.findByIdWithProgram(postId) } returns Optional.of(post)
        every { talentResponseRepository.existsByPost_IdAndAuthorUsernameIgnoreCase(postId, "helper") } returns false

        val response = service.createResponse(
            postId,
            TalentResponseCreateRequest(message = "I can help with planting"),
        )

        assertEquals("helper", response.authorUsername)
        assertEquals(1, post.responseCount)
        verify(exactly = 1) {
            inboxService.notifyTalentResponse(
                recipient = "author",
                subject = match { it.contains("Community garden") },
                body = match { it.contains("I can help with planting") && it.contains("/ideas?post=$postId") },
                createdBy = "helper",
            )
        }
    }

    @Test
    fun `createResponse rejects duplicate response`() {
        every { currentUserLogin() } returns responder.username
        every { accountService.getCurrentAccount() } returns responder
        val postId = UUID.randomUUID()
        val post = TalentPost(
            id = postId,
            type = TalentPostType.CAN_HELP,
            title = "Can code",
            body = "Kotlin",
            authorUsername = author.username,
        )
        every { talentPostRepository.findByIdWithProgram(postId) } returns Optional.of(post)
        every { talentResponseRepository.existsByPost_IdAndAuthorUsernameIgnoreCase(postId, "helper") } returns true

        assertThrows<InvalidRequestException> {
            service.createResponse(postId, TalentResponseCreateRequest(message = "again"))
        }
        verify(exactly = 0) { inboxService.notifyTalentResponse(any(), any(), any(), any()) }
    }

    @Test
    fun `putMySkills replaces skill list`() {
        every { currentUserLogin() } returns author.username
        every { accountService.getCurrentAccount() } returns author
        every { userSkillRepository.findAllByAccountIdOrderBySkillAsc(1) } returns listOf(
            UserSkill(accountId = 1, skill = "kotlin"),
            UserSkill(accountId = 1, skill = "design"),
        )

        val saved = slot<UserSkill>()
        every { userSkillRepository.save(capture(saved)) } answers { firstArg() }

        val result = service.putMySkills(TalentSkillsUpdateRequest(skills = listOf(" kotlin ", "figma", "kotlin")))
        assertEquals(listOf("kotlin", "figma"), result.skills)
        verify(exactly = 1) { userSkillRepository.deleteAllByAccountId(1) }
        verify(exactly = 2) { userSkillRepository.save(any()) }

        every { userSkillRepository.findAllByAccountIdOrderBySkillAsc(1) } returns listOf(
            UserSkill(accountId = 1, skill = "figma"),
            UserSkill(accountId = 1, skill = "kotlin"),
        )
        val got = service.getMySkills()
        assertEquals(listOf("figma", "kotlin"), got.skills)
    }

    @Test
    fun `listPosts returns open posts of requested type`() {
        every { currentUserLogin() } returns responder.username
        every { accountService.getCurrentAccount() } returns responder
        val post = TalentPost(
            id = UUID.randomUUID(),
            type = TalentPostType.NEED_PEOPLE,
            title = "Need help",
            body = "body",
            authorUsername = author.username,
            skills = "java,spring",
            responseCount = 2,
        )
        every {
            talentPostRepository.findAll(any<org.springframework.data.jpa.domain.Specification<TalentPost>>(), any<PageRequest>())
        } returns PageImpl(listOf(post), PageRequest.of(0, 20), 1)
        every { talentResponseRepository.findTop5ByPost_IdOrderByCreatedAtDesc(post.id!!) } returns listOf(
            TalentResponse(post = post, authorUsername = "helper", message = "hi"),
        )
        every { talentResponseRepository.existsByPost_IdAndAuthorUsernameIgnoreCase(post.id!!, "helper") } returns true

        val page = service.listPosts(TalentPostType.NEED_PEOPLE, null, null, null, PageRequest.of(0, 20))
        assertEquals(1, page.totalElements)
        assertEquals(listOf("java", "spring"), page.content[0].skills)
        assertTrue(page.content[0].alreadyResponded)
    }

    @Test
    fun `unreadCount without since counts open posts by others`() {
        every { currentUserLogin() } returns responder.username
        every { accountService.getCurrentAccount() } returns responder
        every {
            talentPostRepository.countByStatusAndAuthorUsernameNotIgnoreCase(TalentPostStatus.OPEN, "helper")
        } returns 3

        assertEquals(3, service.unreadCount(null))
        verify(exactly = 0) { talentResponseRepository.countNewForPostOwner(any(), any()) }
    }

    @Test
    fun `unreadCount with since includes new posts and responses`() {
        every { currentUserLogin() } returns author.username
        every { accountService.getCurrentAccount() } returns author
        val since = OffsetDateTime.parse("2026-09-01T00:00:00Z")
        every {
            talentPostRepository.countByStatusAndAuthorUsernameNotIgnoreCaseAndCreatedAtAfter(
                TalentPostStatus.OPEN,
                "author",
                since,
            )
        } returns 2
        every { talentResponseRepository.countNewForPostOwner("author", since) } returns 4

        assertEquals(6, service.unreadCount(since))
    }

    @Test
    fun `listMyPosts returns author history including closed`() {
        every { currentUserLogin() } returns author.username
        every { accountService.getCurrentAccount() } returns author
        val closed = TalentPost(
            id = UUID.randomUUID(),
            type = TalentPostType.NEED_PEOPLE,
            status = TalentPostStatus.CLOSED,
            title = "Old post",
            body = "body",
            authorUsername = author.username,
        )
        every {
            talentPostRepository.findAll(any<org.springframework.data.jpa.domain.Specification<TalentPost>>(), any<PageRequest>())
        } returns PageImpl(listOf(closed), PageRequest.of(0, 40), 1)
        every { talentResponseRepository.findTop5ByPost_IdOrderByCreatedAtDesc(closed.id!!) } returns emptyList()
        every { talentResponseRepository.existsByPost_IdAndAuthorUsernameIgnoreCase(closed.id!!, "author") } returns false

        val page = service.listMyPosts(null, PageRequest.of(0, 40))
        assertEquals(1, page.totalElements)
        assertEquals(TalentPostStatus.CLOSED, page.content[0].status)
        assertTrue(page.content[0].mine)
    }

    @Test
    fun `reopenPost sets status back to OPEN`() {
        every { currentUserLogin() } returns author.username
        every { accountService.getCurrentAccount() } returns author
        val postId = UUID.randomUUID()
        val post = TalentPost(
            id = postId,
            type = TalentPostType.CAN_HELP,
            status = TalentPostStatus.CLOSED,
            title = "Help",
            body = "body",
            authorUsername = author.username,
        )
        every { talentPostRepository.findByIdWithProgram(postId) } returns Optional.of(post)
        every { talentResponseRepository.existsByPost_IdAndAuthorUsernameIgnoreCase(postId, "author") } returns false

        val dto = service.reopenPost(postId)
        assertEquals(TalentPostStatus.OPEN, dto.status)
        assertEquals(TalentPostStatus.OPEN, post.status)
    }
}
