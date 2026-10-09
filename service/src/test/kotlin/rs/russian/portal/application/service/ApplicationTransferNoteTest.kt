package rs.russian.portal.application.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApplicationTransferNoteTest {

    @Test
    fun `transfer note embeds machine tag for revert`() {
        val note = ApplicationTransferService.transferNote(
            dateLabel = "09.10.2026",
            fromName = "Анна Соболевская",
            toName = "Иван Фоменко",
            fromLogin = "sobolevskaya",
            toLogin = "fomenko",
        )
        assertTrue(note.contains("Передача полномочий 09.10.2026"))
        assertTrue(note.contains("Анна Соболевская → Иван Фоменко"))
        assertEquals(
            "[transfer:sobolevskaya->fomenko]",
            ApplicationTransferService.transferTag("Sobolevskaya", "Fomenko"),
        )
        assertTrue(note.contains(ApplicationTransferService.transferTag("sobolevskaya", "fomenko")))
    }
}
