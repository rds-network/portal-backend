package rs.russian.portal.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.telegram")
data class TelegramProperties(
    /** Bot token from @BotFather. Empty = Telegram alerts disabled. */
    val botToken: String = "",
    /** Chat/group for new volunteer applications. */
    val applicationsChatId: String = "",
    /**
     * Chat/group for reports waiting for acceptance.
     * Falls back to [applicationsChatId] when empty.
     */
    val acceptanceChatId: String = "",
) {
    val enabled: Boolean get() = botToken.isNotBlank()

    fun chatForApplications(): String? =
        applicationsChatId.trim().takeIf { it.isNotEmpty() }

    fun chatForAcceptance(): String? =
        acceptanceChatId.trim().takeIf { it.isNotEmpty() } ?: chatForApplications()
}
