package rs.russian.portal.user.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.MapSession

class SessionServiceTest {
    @Test
    fun `revokes every session belonging to the specified user`() {
        val repository = mockk<FindByIndexNameSessionRepository<MapSession>>(relaxed = true)
        every { repository.findByPrincipalName("disabled") } returns mapOf(
            "browser-one" to MapSession("browser-one"),
            "browser-two" to MapSession("browser-two"),
        )
        SessionService(repository).invalidate("disabled")
        verify(exactly = 1) { repository.deleteById("browser-one") }
        verify(exactly = 1) { repository.deleteById("browser-two") }
        verify(exactly = 2) { repository.deleteById(any()) }
    }
}
