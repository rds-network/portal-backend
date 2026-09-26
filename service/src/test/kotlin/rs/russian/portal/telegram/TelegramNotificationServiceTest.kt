package rs.russian.portal.telegram

import org.junit.jupiter.api.Test
import rs.russian.portal.config.AppProperties
import rs.russian.portal.config.TelegramProperties
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelegramNotificationServiceTest {

    @Test
    fun `disabled when bot token blank`() {
        val props = TelegramProperties(botToken = "", applicationsChatId = "1")
        assertFalse(props.enabled)
        assertNull(TelegramNotificationService(props, AppProperties(frontendUri = "https://portal.russian.rs")).let {
            // no-op send should not throw
            it.notifyNewApplication("id", "Name", "a@b.c", "NEW")
            null
        })
    }

    @Test
    fun `acceptance chat falls back to applications chat`() {
        val props = TelegramProperties(
            botToken = "token",
            applicationsChatId = "-1001",
            acceptanceChatId = "",
        )
        assertTrue(props.enabled)
        kotlin.test.assertEquals("-1001", props.chatForAcceptance())
    }
}
