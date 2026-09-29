package rs.russian.portal.application.service

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import rs.russian.portal.application.domain.Application
import rs.russian.portal.application.domain.ApplicationStatus
import rs.russian.portal.application.mapper.ApplicationMapper
import rs.russian.portal.application.repository.ApplicationRepository
import rs.russian.portal.note.service.NoteService
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.AccountService
import jakarta.persistence.EntityManager
import java.time.LocalDateTime

class ApplicationLookupTest {

    private val noteService = mockk<NoteService>(relaxed = true)
    private val entityManager = mockk<EntityManager>(relaxed = true)
    private val accountService = mockk<AccountService>()
    private val applicationMapper = mockk<ApplicationMapper>(relaxed = true)
    private val applicationRepository = mockk<ApplicationRepository>()
    private val accountRepository = mockk<AccountRepository>(relaxed = true)

    private val service = ApplicationService(
        noteService,
        entityManager,
        accountService,
        applicationMapper,
        applicationRepository,
        accountRepository,
    )

    @Test
    fun `findLatestForUsername prefers DONE over newer non-done`() {
        val account = account("volunteer", "volunteer@example.com")
        every { accountService.findAccountByLogin("volunteer") } returns account
        val olderDone = Application(
            email = account.email,
            name = "Done",
            status = ApplicationStatus.DONE,
            created = LocalDateTime.of(2024, 1, 1, 0, 0),
        )
        val newerOpen = Application(
            email = account.email,
            name = "Open",
            status = ApplicationStatus.IN_PROGRESS,
            created = LocalDateTime.of(2025, 6, 1, 0, 0),
        )
        every { applicationRepository.findAllByEmail(account.email) } returns listOf(olderDone, newerOpen)

        val found = service.findLatestForUsername("volunteer")

        assertEquals(olderDone, found)
    }

    @Test
    fun `findLatestForUsername falls back to latest by created when no DONE`() {
        val account = account("volunteer", "volunteer@example.com")
        every { accountService.findAccountByLogin("volunteer") } returns account
        val older = Application(
            email = account.email,
            name = "Older",
            status = ApplicationStatus.CREATED,
            created = LocalDateTime.of(2024, 1, 1, 0, 0),
        )
        val newer = Application(
            email = account.email,
            name = "Newer",
            status = ApplicationStatus.IN_PROGRESS,
            created = LocalDateTime.of(2025, 6, 1, 0, 0),
        )
        every { applicationRepository.findAllByEmail(account.email) } returns listOf(older, newer)

        val found = service.findLatestForUsername("volunteer")

        assertEquals(newer, found)
    }

    @Test
    fun `findLatestForUsername resolves by email when key looks like email`() {
        val account = account("volunteer", "volunteer@example.com")
        every { accountService.findAccountByLogin("volunteer@example.com") } returns null
        every { accountService.findAccountByEmail("volunteer@example.com") } returns account
        val application = Application(email = account.email, name = "Vol", status = ApplicationStatus.DONE)
        every { applicationRepository.findAllByEmail(account.email) } returns listOf(application)

        val found = service.findLatestForUsername("volunteer@example.com")

        assertEquals(application, found)
    }

    @Test
    fun `findLatestForUsername returns null when account or applications missing`() {
        every { accountService.findAccountByLogin("missing") } returns null
        assertNull(service.findLatestForUsername("missing"))

        val account = account("empty", "empty@example.com")
        every { accountService.findAccountByLogin("empty") } returns account
        every { applicationRepository.findAllByEmail(account.email) } returns emptyList()
        assertNull(service.findLatestForUsername("empty"))
    }

    private fun account(username: String, email: String) = Account(
        id = 1,
        username = username,
        email = email,
        fullName = "Test User",
        active = true,
        groups = emptySet(),
    )
}
