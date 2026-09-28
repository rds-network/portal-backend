package rs.russian.portal.applicationjoin.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.applicationjoin.domain.PortalApplicationJoinSettings
import rs.russian.portal.applicationjoin.repository.PortalApplicationJoinSettingsRepository
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.PrivilegedOps
import rs.russian.portal.shared.security.currentUserRoles
import rs.russian.portal.shared.security.realUserLogin
import rs.russian.portal.user.repository.AccountRepository
import java.time.Instant

@Service
class ApplicationJoinSettingsService(
    private val repository: PortalApplicationJoinSettingsRepository,
    private val accountRepository: AccountRepository,
) {

    fun getRow(): PortalApplicationJoinSettings =
        repository.findById(1).orElseGet { repository.save(defaultRow()) }

    fun publicPayload(): ApplicationJoinPayload {
        val row = getRow()
        return toPayload(row)
    }

    fun adminPayload(): ApplicationJoinPayload {
        assertPrivileged()
        return toPayload(getRow())
    }

    @Transactional
    fun replaceAdmin(body: ApplicationJoinAdminSaveBody): ApplicationJoinPayload {
        assertPrivileged()
        val row = getRow()
        row.title = body.title.take(500)
        row.body = body.body.take(50_000)
        row.agree1Label = body.agree1Label.take(5_000)
        row.agree2Label = body.agree2Label.take(5_000)
        row.buttonLabel = body.buttonLabel.take(200)
        row.updatedAt = Instant.now()
        repository.save(row)
        return adminPayload()
    }

    fun assertPrivileged() {
        val realLogin = realUserLogin()
        val account = realLogin?.trim()?.takeIf { it.isNotEmpty() }?.let { key ->
            accountRepository.findByUsername(key).orElse(null)
                ?: accountRepository.findByEmail(key).orElse(null)
        }
        if (!PrivilegedOps.isAllowed(realLogin, currentUserRoles(), account)) {
            throw NotAuthorizedException()
        }
    }

    private fun toPayload(row: PortalApplicationJoinSettings) =
        ApplicationJoinPayload(
            title = row.title,
            body = row.body,
            agree1Label = row.agree1Label,
            agree2Label = row.agree2Label,
            buttonLabel = row.buttonLabel,
            updatedAt = row.updatedAt,
        )

    private fun defaultRow(): PortalApplicationJoinSettings =
        PortalApplicationJoinSettings(
            id = 1,
            title = DEFAULT_TITLE,
            body = "",
            agree1Label = DEFAULT_AGREE1,
            agree2Label = DEFAULT_AGREE2,
            buttonLabel = DEFAULT_BUTTON,
            updatedAt = null,
        )

    companion object {
        const val DEFAULT_TITLE = "Вступление и участие в деятельности организации"
        const val DEFAULT_AGREE1 =
            "Я ознакомился и согласен с целями и условиями, установленными в " +
                "<a href='https://russian.rs/about/about-documents/' target='_blank'>" +
                "уставе и кодексе этики организации</a>"
        const val DEFAULT_AGREE2 =
            "Я согласен на обработку и хранение указанной мною информации в соответствии с " +
                "<a href='https://russian.rs/privacy-policy' target='_blank'>" +
                "политикой конфиденциальности</a>"
        const val DEFAULT_BUTTON = "Заполнить анкету"
    }
}

data class ApplicationJoinPayload(
    val title: String,
    val body: String,
    val agree1Label: String,
    val agree2Label: String,
    val buttonLabel: String,
    val updatedAt: Instant?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApplicationJoinAdminSaveBody(
    val title: String = "",
    val body: String = "",
    val agree1Label: String = "",
    val agree2Label: String = "",
    val buttonLabel: String = "",
)
