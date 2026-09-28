package rs.russian.portal.user.domain.specification

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import rs.russian.generated.model.ContractTypeEnum
import rs.russian.portal.file.domain.FileInfo
import rs.russian.portal.file.domain.listener.FileInfoListener
import rs.russian.portal.file.service.S3Service
import rs.russian.portal.note.domain.Note
import rs.russian.portal.program.domain.Program
import rs.russian.portal.program.domain.Project
import rs.russian.portal.report.domain.Report
import rs.russian.portal.report.domain.Task
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.report.domain.listener.ReportEntityListener
import rs.russian.portal.report.repository.ReportRepository
import rs.russian.portal.shared.audit.AuditRepository
import rs.russian.portal.shared.utils.CacheService
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.Contract
import rs.russian.portal.user.domain.ResidencePermit
import rs.russian.portal.user.domain.UserInfo
import rs.russian.portal.user.domain.listener.AccountEntityListener
import rs.russian.portal.user.repository.AccountRepository
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DataJpaTest(properties = ["spring.liquibase.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"])
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@ContextConfiguration(classes = [HeatMapAccountSpecificationTest.Config::class])
class HeatMapAccountSpecificationTest {

    @TestConfiguration
    @EnableJpaRepositories(basePackageClasses = [AccountRepository::class, ReportRepository::class])
    @EntityScan(
        basePackageClasses = [
            Account::class,
            Contract::class,
            ResidencePermit::class,
            UserInfo::class,
            FileInfo::class,
            Report::class,
            Task::class,
            Note::class,
            Program::class,
            Project::class,
        ]
    )
    @Import(AccountEntityListener::class, ReportEntityListener::class, FileInfoListener::class)
    class Config

    @Autowired
    lateinit var accountRepository: AccountRepository

    @Autowired
    lateinit var reportRepository: ReportRepository

    @MockitoBean
    lateinit var applicationEventPublisher: ApplicationEventPublisher

    @MockitoBean
    lateinit var auditRepository: AuditRepository

    @MockitoBean
    lateinit var s3Service: S3Service

    @MockitoBean
    lateinit var cacheService: CacheService

    private val year2026 = LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 12, 31)

    @BeforeEach
    fun setup() {
        reportRepository.deleteAll()
        accountRepository.deleteAll()

        saveWithContract(
            id = 20101,
            username = "anna_active_unique",
            fullName = "Anna Active",
            start = LocalDate.of(2026, 1, 1),
            end = LocalDate.of(2026, 12, 31),
        )
        saveWithContract(
            id = 20102,
            username = "anna_expired_midyear_unique",
            fullName = "Anna Midyear",
            start = LocalDate.of(2026, 1, 1),
            end = LocalDate.of(2026, 9, 30),
        )
        saveWithContract(
            id = 20103,
            username = "anna_prev_year_unique",
            fullName = "Anna PrevYear",
            start = LocalDate.of(2025, 1, 1),
            end = LocalDate.of(2025, 12, 31),
        )
        val reportOnly = Account(
            id = 20104,
            username = "anna_reports_only_unique",
            email = "anna_reports_only@example.com",
            fullName = "Anna ReportsOnly",
            active = true,
        )
        accountRepository.saveAndFlush(reportOnly)
        val report = Report(account = reportOnly, status = ReportStatus.ACCEPTED)
        report.tasks.add(
            Task(
                date = LocalDate.of(2026, 9, 15),
                name = "Clean city",
                description = "Week 38",
                timeSpent = 120,
                report = report,
            )
        )
        reportRepository.saveAndFlush(report)

        saveWithContract(
            id = 20105,
            username = "anna_associated_unique",
            fullName = "Anna Associated",
            start = LocalDate.of(2026, 6, 1),
            end = LocalDate.of(2026, 12, 31),
            type = ContractTypeEnum.ASSOCIATED,
        )
    }

    @Test
    fun `year overlap includes mid-year expired and associated contracts`() {
        val found = usernames(hasHeatMapContractOverlapping(year2026.first, year2026.second))
        assertTrue(found.contains("anna_active_unique"))
        assertTrue(found.contains("anna_expired_midyear_unique"))
        assertTrue(found.contains("anna_associated_unique"))
        assertFalse(found.contains("anna_prev_year_unique"))
        assertFalse(found.contains("anna_reports_only_unique"))
    }

    @Test
    fun `single-day active check excludes mid-year expired when after end`() {
        val on = LocalDate.of(2026, 10, 15)
        val found = usernames(hasActiveHeatMapContract(on))
        assertTrue(found.contains("anna_active_unique"))
        assertTrue(found.contains("anna_associated_unique"))
        assertFalse(found.contains("anna_expired_midyear_unique"))
        assertFalse(found.contains("anna_prev_year_unique"))
    }

    @Test
    fun `report-in-range finds accounts without heatmap contract`() {
        val found = usernames(hasReportWithTaskInRange(year2026.first, year2026.second))
        assertTrue(found.contains("anna_reports_only_unique"))
        assertFalse(found.contains("anna_active_unique"))
    }

    @Test
    fun `search union year-contract or reports includes report-only volunteer`() {
        val yearStart = year2026.first
        val yearEnd = year2026.second
        val eligibility = hasHeatMapContractOverlapping(yearStart, yearEnd)
            .or(hasReportWithTaskInRange(yearStart, yearEnd))
        val found = usernames(searchSpecification("Anna", null).and(eligibility))
        assertTrue(found.contains("anna_active_unique"))
        assertTrue(found.contains("anna_expired_midyear_unique"))
        assertTrue(found.contains("anna_reports_only_unique"))
        assertTrue(found.contains("anna_associated_unique"))
        assertFalse(found.contains("anna_prev_year_unique"))
    }

    @Test
    fun `browse without search keeps only year-overlapping contracts`() {
        val found = usernames(
            searchSpecification("", null).and(hasHeatMapContractOverlapping(year2026.first, year2026.second))
        )
        assertTrue(found.contains("anna_active_unique"))
        assertTrue(found.contains("anna_expired_midyear_unique"))
        assertFalse(found.contains("anna_reports_only_unique"))
        assertFalse(found.contains("anna_prev_year_unique"))
    }

    private fun usernames(spec: Specification<Account>) =
        accountRepository.findAll(spec).map { it.username }.sorted()

    private fun saveWithContract(
        id: Int,
        username: String,
        fullName: String,
        start: LocalDate,
        end: LocalDate,
        type: ContractTypeEnum = ContractTypeEnum.REGULAR,
    ) {
        val account = Account(
            id = id,
            username = username,
            email = "$username@example.com",
            fullName = fullName,
            active = true,
        )
        account.contracts.add(
            Contract(account = account, startDate = start, endDate = end, type = type)
        )
        accountRepository.saveAndFlush(account)
    }
}
