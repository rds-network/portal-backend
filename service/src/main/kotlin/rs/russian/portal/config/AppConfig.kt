@file:Suppress("UsePropertyAccessSyntax")

package rs.russian.portal.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.filter.CommonsRequestLoggingFilter
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import rs.russian.portal.shared.utils.CacheService.Companion.CACHE_MAP
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI
import javax.sql.DataSource

@Configuration
@EnableCaching
@EnableScheduling
@EnableSchedulerLock(defaultLockAtLeastFor = "PT1M", defaultLockAtMostFor = "PT59M")
@EnableConfigurationProperties(
    value = [
        AppProperties::class,
        S3Properties::class,
        AuthentikProperties::class,
        WordpressProperties::class,
        OutlineProperties::class,
        HelpdeskProperties::class,
        TelegramProperties::class,
    ]
)
class AppConfig {

    @Bean
    @Primary
    fun objectMapper(): ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModules(JavaTimeModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @Bean
    fun csvMapper(): CsvMapper = CsvMapper().apply {
        registerKotlinModule()
        registerModules(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    @Bean
    fun shedlockProvider(datasource: DataSource): LockProvider = JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(JdbcTemplate(datasource))
            .usingDbTime()
            .build()
    )

    @Bean
    fun s3Client(props: S3Properties): S3Client {
        val creds = AwsBasicCredentials.create(props.accessKey, props.secretKey)
        return S3Client.builder()
            .region(Region.of(props.region))
            .endpointOverride(URI(props.endpoint))
            .forcePathStyle(true)
            .credentialsProvider(StaticCredentialsProvider.create(creds))
            .build()
    }

    @Bean
    fun s3Presigner(props: S3Properties, s3Client: S3Client): S3Presigner {
        val creds = AwsBasicCredentials.create(props.accessKey, props.secretKey)
        return S3Presigner.builder()
            .region(Region.of(props.region))
            .endpointOverride(URI(props.endpoint))
            .credentialsProvider(StaticCredentialsProvider.create(creds))
            .s3Client(s3Client)
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build()
            )
            .build()
    }

    @Bean
    fun cacheManager(): CaffeineCacheManager {
        val cacheManager = CaffeineCacheManager()
        CACHE_MAP.forEach { (name, cache) -> cacheManager.registerCustomCache(name, cache) }
        return cacheManager
    }

    @Primary
    @Bean("emailTemplateEngine")
    fun emailTemplateEngine(): SpringTemplateEngine {
        val templateResolver = ClassLoaderTemplateResolver()
        templateResolver.prefix = "templates/email/"
        templateResolver.suffix = ".html"
        templateResolver.setTemplateMode("HTML")
        templateResolver.characterEncoding = "UTF-8"
        val templateEngine = SpringTemplateEngine()
        templateEngine.setTemplateResolver(templateResolver)
        return templateEngine
    }

    @Bean("ticketTemplateEngine")
    fun ticketTemplateEngine(): SpringTemplateEngine {
        val templateResolver = ClassLoaderTemplateResolver()
        templateResolver.prefix = "templates/ticket/"
        templateResolver.suffix = ".html"
        templateResolver.setTemplateMode("HTML")
        templateResolver.characterEncoding = "UTF-8"
        val templateEngine = SpringTemplateEngine()
        templateEngine.setTemplateResolver(templateResolver)
        return templateEngine
    }

    @Bean
    @Profile("local")
    fun requestLoggingFilter(): CommonsRequestLoggingFilter {
        val filter = CommonsRequestLoggingFilter()
        filter.setIncludeQueryString(true)
        filter.setIncludePayload(false)
        filter.setIncludeHeaders(false)
        filter.setIncludeClientInfo(false)
        filter.setMaxPayloadLength(0)
        return filter
    }
}
