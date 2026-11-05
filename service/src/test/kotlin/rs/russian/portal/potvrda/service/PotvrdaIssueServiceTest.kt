package rs.russian.portal.potvrda.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import rs.russian.generated.model.PotvrdaIssueCreateRequest
import rs.russian.generated.model.PotvrdaKindEnum
import rs.russian.portal.potvrda.mapper.PotvrdaIssueMapper
import rs.russian.portal.potvrda.repository.PotvrdaIssueRepository
import rs.russian.portal.shared.security.currentUserLogin
import java.time.LocalDate

class PotvrdaIssueServiceTest {

    private val repository = mockk<PotvrdaIssueRepository>()
    private val mapper = PotvrdaIssueMapper()
    private val service = PotvrdaIssueService(repository, mapper)

    @BeforeEach
    fun setUp() {
        mockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
        every { currentUserLogin() } returns "admin"
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("rs.russian.portal.shared.security.SecurityExtensionsKt")
    }

    @Test
    fun `next number increments within year`() {
        every { repository.maxSequenceForYear(2025) } returns 8

        val next = service.nextNumber(2025)

        assertEquals("009/2025", next.confirmNumber)
        assertEquals(9, next.sequenceNumber)
    }

    @Test
    fun `create allocates next sequence for issue year`() {
        every { repository.maxSequenceForYear(2025) } returns 0
        every { repository.save(any()) } answers { firstArg() }

        val dto =
            service.create(
                PotvrdaIssueCreateRequest(
                    kind = PotvrdaKindEnum.OFFICIAL,
                    volunteerUsername = "volunteer1",
                    volunteerFullName = "Test User",
                    issueDate = LocalDate.of(2025, 11, 4),
                )
            )

        assertEquals("001/2025", dto.confirmNumber)
        assertEquals("volunteer1", dto.volunteerUsername)
    }
}
