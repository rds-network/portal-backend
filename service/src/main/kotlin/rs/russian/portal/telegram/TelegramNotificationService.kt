package rs.russian.portal.telegram

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import rs.russian.portal.config.AppProperties
import rs.russian.portal.config.TelegramProperties
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

@Service
class TelegramNotificationService(
    private val telegram: TelegramProperties,
    private val app: AppProperties,
) {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    fun notifyNewApplication(id: String, name: String, email: String, type: String?) {
        val chat = telegram.chatForApplications() ?: return
        val link = "${app.frontendUri.trimEnd('/')}/application/$id"
        val typeLabel = when (type?.uppercase()) {
            "PROLONGATION" -> "продление"
            else -> "новая"
        }
        send(
            chat,
            """
            🆕 Заявка ($typeLabel)
            $name
            $email
            $link
            """.trimIndent(),
        )
    }

    fun notifyReportForAcceptance(
        reportId: String,
        volunteerName: String,
        customerLogin: String,
        customerTelegram: String? = null,
    ) {
        val chat = telegram.chatForAcceptance() ?: return
        val link = "${app.frontendUri.trimEnd('/')}/report/$reportId"
        val mention = customerTelegram?.trim()?.trimStart('@')?.takeIf { it.isNotEmpty() }?.let { "@$it" }
            ?: customerLogin
        send(
            chat,
            """
            📋 На приёмку
            Волонтёр: $volunteerName
            Заказчик: $mention
            $link
            """.trimIndent(),
        )
    }

    fun send(chatId: String, text: String) {
        if (!telegram.enabled) return
        try {
            val token = telegram.botToken.trim()
            val body = "chat_id=${enc(chatId)}&text=${enc(text)}&disable_web_page_preview=true"
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.telegram.org/bot$token/sendMessage"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                log.warn("Telegram send failed chat={} status={} body={}", chatId, response.statusCode(), response.body().take(300))
            }
        } catch (ex: Exception) {
            log.warn("Telegram send failed chat={}: {}", chatId, ex.message)
        }
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    companion object {
        private val log = LoggerFactory.getLogger(TelegramNotificationService::class.java)
    }
}
