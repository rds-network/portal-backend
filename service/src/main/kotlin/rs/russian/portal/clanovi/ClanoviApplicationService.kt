package rs.russian.portal.clanovi

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.generated.model.ApplicationDto
import rs.russian.portal.application.domain.ApplicationStatus
import rs.russian.portal.application.domain.ApplicationType
import rs.russian.portal.application.mapper.ApplicationMapper
import rs.russian.portal.application.repository.ApplicationRepository
import rs.russian.portal.application.service.ApplicationService
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.utils.toOffsetDateTime
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.UUID

@Service
class ClanoviApplicationService(
    private val applicationService: ApplicationService,
    private val applicationMapper: ApplicationMapper,
    private val applicationRepository: ApplicationRepository,
) {

    @Transactional(readOnly = true)
    fun listChanged(sinceRaw: String?, limitRaw: Int?, open: Boolean?, pageRaw: Int?): ClanoviApplicationListResponse {
        val limit = (limitRaw ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val pageNumber = (pageRaw ?: 0).coerceAtLeast(0)
        val rows = if (open == true) {
            applicationRepository.findOpenByType(
                ApplicationType.NEW,
                DEPERSONALIZED_EMAIL,
                PageRequest.of(pageNumber, limit),
            )
        } else {
            applicationRepository.findChangedSince(
                parseSince(sinceRaw),
                DEPERSONALIZED_EMAIL,
                PageRequest.of(0, limit),
            )
        }
        val since = parseSince(sinceRaw)
        val items = rows.map { application ->
            val updated = application.version ?: application.created
            ClanoviApplicationListItem(
                id = application.id!!,
                status = application.status.name,
                type = application.type.name,
                created = toOffsetDateTime(application.created),
                updated = toOffsetDateTime(updated),
                email = application.email,
                name = application.name,
            )
        }
        val nextSince = items.lastOrNull()?.updated ?: toOffsetDateTime(since)
        return ClanoviApplicationListResponse(
            since = toOffsetDateTime(since),
            nextSince = nextSince,
            truncated = items.size >= limit,
            items = items,
        )
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): ApplicationDto {
        val application = applicationService.get(id)
        if (application.email == DEPERSONALIZED_EMAIL) {
            throw NoSuchElementException()
        }
        return applicationMapper.toDto(application)
    }

    /**
     * After Clanovi emails the document pack: mark the portal application completed (DONE).
     * Idempotent if already DONE. Requires contract dates + program/project (same as UI).
     */
    @Transactional
    fun markDone(id: UUID): ApplicationDto {
        val application = applicationService.get(id)
        if (application.email == DEPERSONALIZED_EMAIL) {
            throw NoSuchElementException()
        }
        if (application.status == ApplicationStatus.DONE) {
            return applicationMapper.toDto(application)
        }
        val dto = applicationMapper.toDto(application)
        dto.status = ApplicationStatus.DONE.name
        return applicationMapper.toDto(applicationService.update(dto))
    }

    companion object {
        const val DEFAULT_LIMIT = 200
        const val MAX_LIMIT = 200
        private const val DEPERSONALIZED_EMAIL = "depersonalized@deleted.local"
        private val EPOCH: LocalDateTime = LocalDateTime.of(1970, 1, 1, 0, 0)

        fun parseSince(raw: String?): LocalDateTime {
            if (raw.isNullOrBlank()) return EPOCH
            val value = raw.trim()
            return try {
                OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
            } catch (_: DateTimeException) {
                try {
                    Instant.parse(value).atOffset(ZoneOffset.UTC).toLocalDateTime()
                } catch (_: DateTimeParseException) {
                    try {
                        LocalDateTime.parse(value)
                    } catch (_: DateTimeParseException) {
                        throw InvalidRequestException("Invalid since: $value")
                    }
                }
            }
        }
    }
}
