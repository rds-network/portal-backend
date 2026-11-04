package rs.russian.portal.application.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import rs.russian.generated.model.ApplicationDto
import rs.russian.generated.model.ContractDto
import rs.russian.generated.model.ContractTypeEnum
import rs.russian.portal.application.domain.ApplicationStatus
import rs.russian.portal.application.domain.ApplicationType
import rs.russian.portal.config.DefaultUserFilter
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.testconfig.AbstractIntegrationTest
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ApplicationServiceTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var applicationService: ApplicationService

    @Autowired
    lateinit var accountService: AccountService

    @Autowired
    lateinit var defaultUserFilter: DefaultUserFilter

    @BeforeEach
    fun setup() {
        SecurityContextHolder.getContext().authentication = defaultUserFilter.getDefaultOAuth2Token()
    }

    @Test
    fun `create should infer program from project`() {
        val application = applicationService.create(
            ApplicationDto(
                id = UUID.randomUUID(),
                email = uniqueEmail("infer-project"),
                name = "Project Applicant",
                project = "LAYOUT"
            )
        )

        assertEquals("LAYOUT", application.project?.code)
        assertEquals("IT", application.program?.code)
    }

    @Test
    fun `update should reject mismatched project and program`() {
        val created = applicationService.create(
            ApplicationDto(
                id = UUID.randomUUID(),
                email = uniqueEmail("invalid-pair"),
                name = "Invalid Pair Applicant"
            )
        )

        assertThrows<InvalidRequestException> {
            applicationService.update(
                ApplicationDto(
                    id = created.id!!,
                    email = created.email,
                    name = created.name,
                    program = "MEDIA",
                    project = "LAYOUT"
                )
            )
        }
    }

    @Test
    fun `update DONE should create account with inferred program and project`() {
        val created = applicationService.create(
            ApplicationDto(
                id = UUID.randomUUID(),
                email = uniqueEmail("done-new"),
                name = "Done New Applicant",
                project = "FORMS"
            )
        )

        val updated = applicationService.update(
            ApplicationDto(
                id = created.id!!,
                email = created.email,
                name = created.name,
                status = ApplicationStatus.DONE.name,
                contract = testContract()
            )
        )

        assertEquals(ApplicationStatus.DONE, updated.status)
        val account = accountService.findAccountByEmail(created.email)
        assertNotNull(account)
        assertEquals("FORMS", account.info?.project?.code)
        assertEquals("IT", account.info?.program?.code)
    }

    @Test
    fun `update DONE should set program and project for prolongation account`() {
        val email = uniqueEmail("done-prolongation")
        val account = accountService.create(email, "Existing Volunteer")
        accountService.setProgram(account.id!!, "MEDIA")

        val created = applicationService.create(
            ApplicationDto(
                id = UUID.randomUUID(),
                email = email,
                name = "Existing Volunteer",
                project = "LAYOUT"
            )
        )

        assertEquals(ApplicationType.PROLONGATION, created.type)

        val updated = applicationService.update(
            ApplicationDto(
                id = created.id!!,
                email = created.email,
                name = created.name,
                status = ApplicationStatus.DONE.name,
                contract = testContract()
            )
        )

        assertEquals(ApplicationStatus.DONE, updated.status)
        val updatedAccount = accountService.findAccountByEmail(email)
        assertNotNull(updatedAccount)
        assertEquals("LAYOUT", updatedAccount.info?.project?.code)
        assertEquals("IT", updatedAccount.info?.program?.code)
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["IT"])
    fun `completion requires program and project and succeeds after employee fills them`(program: String?) {
        val created = applicationService.create(ApplicationDto(
            id = UUID.randomUUID(), email = uniqueEmail("missing-program-project"),
            name = "Incomplete Applicant", program = program
        ))
        val prepared = applicationService.update(ApplicationDto(id = created.id!!, contract = testContract()))

        val error = assertThrows<InvalidRequestException> {
            applicationService.update(ApplicationDto(id = created.id!!, status = ApplicationStatus.DONE.name))
        }
        assertEquals("Program and project must be specified before completing the application", error.message)
        val unchanged = applicationService.get(created.id!!)
        assertEquals(prepared.status, unchanged.status)
        assertEquals(prepared.assignee, unchanged.assignee)
        assertNull(accountService.findAccountByEmail(created.email))

        applicationService.update(ApplicationDto(id = created.id!!, program = "IT", project = "FORMS"))
        val completed = applicationService.update(ApplicationDto(id = created.id!!, status = ApplicationStatus.DONE.name))
        assertEquals(ApplicationStatus.DONE, completed.status)
        val account = accountService.findAccountByEmail(created.email)
        assertNotNull(account)
        assertEquals("IT", account.info?.program?.code)
        assertEquals("FORMS", account.info?.project?.code)
    }

    @Test
    fun `contract document date is fixed on first save`() {
        val created = applicationService.create(
            ApplicationDto(
                id = UUID.randomUUID(),
                email = uniqueEmail("contract-date"),
                name = "Contract Date Applicant"
            )
        )
        assertNull(created.contractDocumentDate)

        val start = LocalDate.now().plusDays(10)
        val saved = applicationService.update(
            ApplicationDto(
                id = created.id!!,
                contract = ContractDto(
                    id = UUID.randomUUID(),
                    startDate = start,
                    endDate = start.plusYears(1),
                    type = ContractTypeEnum.REGULAR
                )
            )
        )
        val today = LocalDate.now(ZoneId.of("Europe/Belgrade"))
        assertEquals(today, saved.contractDocumentDate)

        val moved = applicationService.update(
            ApplicationDto(
                id = created.id!!,
                contract = ContractDto(
                    id = UUID.randomUUID(),
                    startDate = start.plusDays(3),
                    endDate = start.plusYears(1),
                    type = ContractTypeEnum.REGULAR
                )
            )
        )
        assertEquals(saved.contractDocumentDate, moved.contractDocumentDate)
        assertEquals(start.plusDays(3), moved.contractFrom)
    }

    private fun uniqueEmail(prefix: String) = "$prefix-${UUID.randomUUID()}@example.com"

    private fun testContract() = ContractDto(
        id = UUID.randomUUID(),
        startDate = LocalDate.now(),
        endDate = LocalDate.now().plusMonths(6),
        type = ContractTypeEnum.REGULAR
    )
}
