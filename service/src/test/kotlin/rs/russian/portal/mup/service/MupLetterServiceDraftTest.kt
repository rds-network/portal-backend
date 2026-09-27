package rs.russian.portal.mup.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import rs.russian.portal.application.domain.Application
import rs.russian.portal.application.repository.ApplicationRepository
import rs.russian.portal.mail.repository.EmailOutboxRepository
import rs.russian.portal.mail.service.EmailService
import rs.russian.portal.mup.api.MupLetterReason
import rs.russian.portal.report.repository.ReportRepository
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.UserInfo
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate

class MupLetterServiceDraftTest {

    private val accountService = mockk<AccountService>()
    private val applicationRepository = mockk<ApplicationRepository>()
    private val reportRepository = mockk<ReportRepository>(relaxed = true)
    private val emailService = mockk<EmailService>(relaxed = true)
    private val emailOutboxRepository = mockk<EmailOutboxRepository>(relaxed = true)

    private val service = MupLetterService(
        accountService,
        applicationRepository,
        reportRepository,
        emailService,
        emailOutboxRepository,
    )

    @Test
    fun `draft fills passport birthDate and address including Адреса line`() {
        val account = Account(
            id = 1,
            username = "volunteer",
            email = "volunteer@example.com",
            fullName = "Иван Иванов",
            active = true,
            groups = emptySet(),
        )
        val info = UserInfo(
            id = "volunteer",
            account = account,
            birthDate = LocalDate.of(1990, 5, 15),
            postalCode = "11000",
            city = "Beograd",
            address = "Knez Mihailova 1",
        )
        account.info = info
        every { accountService.findAccountByLogin("volunteer") } returns account
        every { applicationRepository.findAllByEmail("volunteer@example.com") } returns listOf(
            Application(
                email = "volunteer@example.com",
                name = "Иван",
                passport = "AB1234567",
                citizenship = "RU",
                birthDate = LocalDate.of(1989, 1, 1),
                postalCode = "11000",
                city = "Beograd",
                address = "Knez Mihailova 1",
            )
        )
        every {
            reportRepository.findTopByAccountUsernameAndStatusOrderByCreateTimeDesc(any(), any())
        } returns null

        val draft = service.draft("volunteer", MupLetterReason.NON_COMPLIANCE)

        assertTrue(draft.passport == "AB1234567")
        assertTrue(draft.birthDate == "15.05.1990")
        assertTrue(draft.address.contains("11000"))
        assertTrue(draft.address.contains("Beograd"))
        assertTrue(draft.address.contains("Knez Mihailova 1"))
        assertTrue(draft.body.contains("Адреса: ${draft.address}"))
        assertTrue(draft.body.contains("Број пасоша: AB1234567"))
        assertTrue(draft.body.contains("Датум рођења: 15.05.1990"))
    }

    @Test
    fun `send marks mup letter flags on account`() {
        val account = Account(
            id = 2,
            username = "volunteer",
            email = "volunteer@example.com",
            fullName = "Иван Иванов",
            active = true,
            groups = emptySet(),
        )
        every { accountService.findAccountByLogin("volunteer") } returns account
        every { accountService.switchActiveState(2, false) } returns account
        every { emailOutboxRepository.findAllByOrderByCreateTimeDesc() } returns emptyList()

        service.send(
            rs.russian.portal.mup.api.MupLetterSendRequest(
                username = "volunteer",
                subject = "Обавештење о престанку уговора",
                body = "x".repeat(50),
                reason = MupLetterReason.VOLUNTEER_REQUEST,
            )
        )

        assertTrue(account.mupLetterSentAt != null)
        assertTrue(account.mupLetterReason == "VOLUNTEER_REQUEST")
        verify { accountService.switchActiveState(2, false) }
        verify { emailService.sendCommonEmail(any(), any(), any()) }
    }
}
