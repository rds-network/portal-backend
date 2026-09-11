package rs.russian.portal.application.service

import io.mockk.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mapstruct.factory.Mappers
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import rs.russian.generated.model.ApplicationDto
import rs.russian.generated.model.ContractDto
import rs.russian.generated.model.ContractTypeEnum
import rs.russian.generated.model.NoteDto
import rs.russian.portal.application.domain.Application
import rs.russian.portal.application.domain.ApplicationStatus.*
import rs.russian.portal.application.mapper.ApplicationMapper
import rs.russian.portal.application.repository.ApplicationRepository
import rs.russian.portal.note.domain.Note
import rs.russian.portal.note.service.NoteService
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup.*
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate
import java.util.*
import kotlin.test.*

class ApplicationAssignmentTest {
    private val repository = mockk<ApplicationRepository>()
    private val accounts = mockk<AccountService>()
    private val accountRepository = mockk<AccountRepository>()
    private val notes = mockk<NoteService>()
    private val mapper = Mappers.getMapper(ApplicationMapper::class.java)
    private val service = ApplicationService(notes, mockk<EntityManager>(), accounts, mapper, repository, accountRepository)
    private lateinit var application: Application

    @BeforeEach
    fun setup() {
        application = Application(email = "applicant@example.com", name = "Applicant", assignee = "previous")
        every { repository.findById(application.id!!) } returns Optional.of(application)
        every { repository.save(any()) } answers { firstArg() }
        val jwt = Jwt.withTokenValue("test").header("alg", "none")
            .claim("sub", "employee-id").claim("preferred_username", "employee").build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
    }

    @AfterEach
    fun cleanup() = SecurityContextHolder.clearContext()

    @Test
    fun `candidate lookup resolves unique employee logins as full accounts`() {
        every { accountRepository.findAllActiveUsernamesByGroup(ADMIN_VOLUNTEER.name) } returns listOf("employee")
        every { accountRepository.findAllActiveUsernamesByGroup(INTERVIEWER.name) } returns listOf("employee", "interviewer")
        val employee = Account(username = "employee", email = "employee@example.com", fullName = "Employee")
        val interviewer = Account(username = "interviewer", email = "interviewer@example.com", fullName = "Interviewer")
        every { accounts.resolve(listOf("employee", "interviewer")) } returns listOf(interviewer, employee)
        assertEquals(listOf("employee", "interviewer"), service.getAssignees().map { it.username })
    }

    @Test
    fun `status change preserves existing assignee`() {
        val updated = service.update(ApplicationDto(id = application.id!!, status = IN_PROGRESS.name, assignee = "forged"))
        assertEquals(IN_PROGRESS, updated.status)
        assertEquals("previous", updated.assignee)
    }

    @Test
    fun `saving same status and other fields preserves assignment`() {
        val updated = service.update(ApplicationDto(id = application.id!!, status = CREATED.name,
            name = "Changed Name", comment = "Comment", assignee = "forged"))
        assertEquals("previous", updated.assignee)
        assertEquals("Changed Name", updated.name)
        assertEquals("Comment", updated.comment)
    }

    @Test
    fun `partial contract edit preserves status and assignment`() {
        application.status = IN_PROGRESS
        service.update(ApplicationDto(id = application.id!!, contract = ContractDto(
            id = UUID.randomUUID(), startDate = LocalDate.now(), endDate = LocalDate.now().plusYears(1),
            type = ContractTypeEnum.REGULAR)))
        assertEquals("previous", application.assignee)
        assertEquals(IN_PROGRESS, application.status)
    }

    @Test
    fun `unassigned application stays unassigned on ordinary edit`() {
        application.assignee = null
        service.update(ApplicationDto(id = application.id!!, name = "Changed Name"))
        assertNull(application.assignee)
    }

    @Test
    fun `creation mapper ignores forged assignment`() {
        val created = Application(email = "new@example.com", name = "New")
        mapper.toEntity(ApplicationDto(id = UUID.randomUUID(), assignee = "forged"), created)
        assertNull(created.assignee)
    }

    @Test
    fun `manual assignment accepts active employees in either permitted role and allows clearing`() {
        listOf(ADMIN_VOLUNTEER, INTERVIEWER).forEach { role ->
            every { accounts.findAccountByLogin("selected") } returns Account(
                username = "selected", fullName = "Selected Employee", email = "selected@example.com", groups = setOf(role))
            assertEquals("selected", service.assign(application.id!!, "selected").assignee)
            assertEquals(CREATED, application.status)
        }
        assertNull(service.assign(application.id!!, null).assignee)
    }

    @Test
    fun `manual assignment rejects unknown inactive and non employee accounts`() {
        every { accounts.findAccountByLogin("missing") } returns null
        assertThrows<InvalidRequestException> { service.assign(application.id!!, "missing") }
        listOf(false to setOf(INTERVIEWER), true to setOf(VOLUNTEER)).forEach { (active, groups) ->
            every { accounts.findAccountByLogin("invalid") } returns Account(username = "invalid", fullName = "Invalid",
                email = "invalid@example.com", active = active, groups = groups)
            assertThrows<InvalidRequestException> { service.assign(application.id!!, "invalid") }
        }
        assertEquals("previous", application.assignee)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `adding a note preserves assignment`() {
        every { accounts.getAccountByLogin("employee") } returns Account(
            username = "employee", email = "employee@example.com", fullName = "Employee")
        every { notes.save(any()) } answers { firstArg<Note>() }
        service.addNote(application.id!!, NoteDto(id = UUID.randomUUID(), text = "A note"))
        assertEquals("previous", application.assignee)
    }

    @Test
    fun `system save preserves assignment without authentication`() {
        SecurityContextHolder.clearContext()
        application.status = DENY
        assertEquals("previous", service.save(application).assignee)
        application.assignee = null
        assertNull(service.save(application).assignee)
    }

    @Test
    fun `status change without authenticated employee preserves assignee`() {
        SecurityContextHolder.clearContext()
        val updated = service.update(ApplicationDto(id = application.id!!, status = IN_PROGRESS.name))
        assertEquals(IN_PROGRESS, updated.status)
        assertEquals("previous", updated.assignee)
    }

    @Test
    fun `failed status validation does not change assignment`() {
        assertThrows<InvalidRequestException> { service.update(ApplicationDto(id = application.id!!, status = DONE.name)) }
        assertEquals("previous", application.assignee)
        verify(exactly = 0) { repository.save(any()) }
    }
}
