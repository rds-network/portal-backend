package rs.russian.portal.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import rs.russian.portal.shared.security.FrontendUriValidator
import java.time.Duration

private val logger = LoggerFactory.getLogger(AppProperties::class.java)

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val frontendUri: String,
    val corsAllowedOrigins: List<String> = emptyList(),
    val clanovi: ClanoviProperties = ClanoviProperties(),
    val accountStatus: AccountStatusProperties = AccountStatusProperties(),
) {
    /**
     * Validated CORS origins: frontendUri + additional configured origins.
     * Only trusted origins (*.russian.rs, localhost) are included.
     */
    val allowedOrigins: List<String> = validateCorsOrigins(frontendUri, corsAllowedOrigins)

    init {
        FrontendUriValidator.validate(frontendUri)
    }
}

private fun validateCorsOrigins(frontendUri: String, corsAllowedOrigins: List<String>): List<String> {
    val allOrigins = listOf(frontendUri) + corsAllowedOrigins
    val (valid, invalid) = allOrigins.partition { FrontendUriValidator.validateOrNull(it) != null }

    if (invalid.any { it.isNotBlank() }) {
        logger.warn("Blocked untrusted CORS origins: {}", invalid.filter { it.isNotBlank() })
    }

    val validated = valid.mapNotNull { FrontendUriValidator.validateOrNull(it) }.distinct()
    val localhostOrigins = validated.filter { FrontendUriValidator.isLocalhost(it) }

    if (localhostOrigins.isNotEmpty()) {
        logger.warn("CORS allows localhost origins - ensure this is not production: {}", localhostOrigins)
    }

    return validated
}

data class ClanoviProperties(
    val apiKey: String = "",
)

data class AccountStatusProperties(
    /** Username of the person who must approve account activation/deactivation. */
    val approverUsername: String = "legkov777",
)

@ConfigurationProperties(prefix = "app.s3")
data class S3Properties(
    val accessKey: String,
    val secretKey: String,
    val region: String,
    val endpoint: String,
    val presignDuration: Duration,
    val bucket: String,
    val bucketService: String,
)

@ConfigurationProperties(prefix = "app.authentik")
data class AuthentikProperties(
    val baseUrl: String,
    val apiKey: String,
)

@ConfigurationProperties(prefix = "app.wordpress")
data class WordpressProperties(
    val instances: List<WordpressInstance>,
)

data class WordpressInstance(
    val name: String,
    val baseUrl: String,
    val username: String,
    val password: String,
)

@ConfigurationProperties(prefix = "app.outline")
data class OutlineProperties(
    val apiKey: String,
    val baseUrl: String,
)

@ConfigurationProperties(prefix = "app.helpdesk")
data class HelpdeskProperties(
    val apiKey: String,
    val apiBaseUrl: String,
    val ticketBaseUrl: String,
)
