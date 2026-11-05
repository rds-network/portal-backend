package rs.russian.portal.application.domain.specification

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import rs.russian.generated.model.ApplicationsFilter
import rs.russian.portal.application.domain.Application
import rs.russian.portal.application.domain.ApplicationStatus.*
import rs.russian.portal.application.domain.listener.ApplicationEntityListener
import rs.russian.portal.application.repository.ApplicationRepository
import rs.russian.portal.note.domain.Note
import rs.russian.portal.program.domain.Program
import rs.russian.portal.shared.audit.AuditRepository
import java.time.LocalDateTime
import kotlin.test.assertEquals

@DataJpaTest(properties = ["spring.liquibase.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"])
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@ContextConfiguration(classes = [ApplicationSpecificationTest.Config::class])
class ApplicationSpecificationTest {
    @TestConfiguration
    @EnableJpaRepositories(basePackageClasses = [ApplicationRepository::class])
    @EntityScan(basePackageClasses = [Application::class, Note::class, Program::class])
    @Import(ApplicationEntityListener::class)
    class Config

    @Autowired
    lateinit var repository: ApplicationRepository

    @MockitoBean
    lateinit var auditRepository: AuditRepository

    @BeforeEach
    fun setup() {
        repository.saveAllAndFlush(listOf(
            Application(name = "Anna", email = "anna@example.com", assignee = "employee"),
            Application(name = "Boris", email = "boris@example.com", assignee = "employee", status = IN_PROGRESS),
            Application(name = "Clara", email = "clara@example.com", assignee = "employee", status = DONE),
            Application(name = "Denis", email = "denis@example.com", assignee = "employee", status = DENY),
            Application(name = "Anna Other", email = "other@example.com", assignee = "employee-other"),
            Application(name = "Unassigned", email = "unassigned@example.com"),
            Application(name = "Unassigned Completed", email = "unassigned-completed@example.com", status = DONE),
        ))
    }

    @Test
    fun `assignee matches exact login and excludes completed applications by default`() {
        assertEquals(listOf("Anna", "Boris"), names(ApplicationsFilter(assignee = "employee")))
        assertEquals(emptyList(), names(ApplicationsFilter(assignee = "missing")))
    }

    @Test
    fun `assignee combines with completed flag and text search`() {
        assertEquals(listOf("Anna", "Boris", "Clara", "Denis"),
            names(ApplicationsFilter(showCompleted = true, assignee = "employee")))
        assertEquals(listOf("Anna"), names(ApplicationsFilter(assignee = "employee"), "anna"))
        assertEquals(listOf("Clara"), names(ApplicationsFilter(showCompleted = true, assignee = "employee"), "clara@example.com"))
    }

    @Test
    fun `omitted or blank assignee includes other employees and unassigned applications`() {
        for (login in listOf(null, "", " ")) {
            assertEquals(listOf("Anna", "Anna Other", "Boris", "Unassigned"), names(ApplicationsFilter(assignee = login)))
        }
        assertEquals(7, names(null).size)
    }

    @Test
    fun `unassigned filter excludes assigned applications and combines with other filters`() {
        assertEquals(listOf("Unassigned"), names(ApplicationsFilter(unassigned = true)))
        assertEquals(listOf("Unassigned", "Unassigned Completed"),
            names(ApplicationsFilter(showCompleted = true, unassigned = true)))
        assertEquals(listOf("Unassigned Completed"),
            names(ApplicationsFilter(showCompleted = true, unassigned = true), "completed"))
        assertEquals(listOf("Unassigned"), names(ApplicationsFilter(assignee = "employee", unassigned = true)))
    }

    @Test
    fun `statuses and staleDays limit by status and last update`() {
        val stale = repository.save(Application(name = "Stale", email = "stale@example.com", status = PAUSED))
        stale.version = LocalDateTime.now().minusDays(20)
        repository.saveAndFlush(stale)
        repository.save(Application(name = "FreshPause", email = "fresh@example.com", status = PAUSED))

        val filter = ApplicationsFilter(
            showCompleted = false,
            statuses = listOf("PAUSED", "CLARIFICATION"),
            staleDays = 14,
        )
        assertEquals(listOf("Stale"), names(filter))
    }

    @Test
    fun `filter is applied before pagination and total count`() {
        val page = repository.findAll(searchSpecification(null, ApplicationsFilter(assignee = "employee")),
            PageRequest.of(1, 1, Sort.by("name")))
        assertEquals(listOf("Boris"), page.content.map { it.name })
        assertEquals(2L, page.totalElements)
        assertEquals(2, page.totalPages)
    }

    private fun names(filter: ApplicationsFilter?, query: String? = null) =
        repository.findAll(searchSpecification(query, filter), PageRequest.of(0, 100, Sort.by("name"))).content.map { it.name }
}
