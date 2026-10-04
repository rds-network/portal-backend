package rs.russian.portal.event.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.event.api.PortalEventDto
import rs.russian.portal.event.api.PortalEventWriteRequest
import rs.russian.portal.event.domain.PortalEvent
import rs.russian.portal.event.repository.PortalEventRepository
import rs.russian.portal.program.service.ProgramCuratorService
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.shared.security.currentUserRoles
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_SSO
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import rs.russian.portal.user.domain.enums.UserGroup.MAIN_VOLUNTEER
import java.time.OffsetDateTime
import java.util.UUID

@Service
class PortalEventService(
    private val portalEventRepository: PortalEventRepository,
    private val programCuratorService: ProgramCuratorService,
) {

    @Transactional(readOnly = true)
    fun listUpcoming(limit: Int = 20): List<PortalEventDto> {
        val from = OffsetDateTime.now().minusHours(2)
        return portalEventRepository
            .findAllByStartsAtGreaterThanEqualOrderByStartsAtAsc(from)
            .take(limit.coerceIn(1, 100))
            .map(::toDto)
    }

    @Transactional(readOnly = true)
    fun listAll(): List<PortalEventDto> =
        portalEventRepository.findAllByOrderByStartsAtDesc().map(::toDto)

    @Transactional
    fun create(request: PortalEventWriteRequest): PortalEventDto {
        assertCanManage(request.programCode)
        val createdBy = currentUserLogin() ?: throw NotAuthorizedException()
        val title = request.title.trim()
        if (title.length < 3) {
            throw InvalidRequestException("title is required")
        }
        val saved = portalEventRepository.save(
            PortalEvent(
                createdBy = createdBy,
                title = title,
                description = request.description?.trim()?.takeIf { it.isNotEmpty() },
                startsAt = request.startsAt,
                location = request.location?.trim()?.takeIf { it.isNotEmpty() },
                type = request.type,
                programCode = request.programCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
            )
        )
        return toDto(saved)
    }

    @Transactional
    fun update(id: UUID, request: PortalEventWriteRequest): PortalEventDto {
        assertCanManage(request.programCode)
        val item = portalEventRepository.findById(id).orElseThrow { EntityNotFoundException("Event $id not found") }
        val title = request.title.trim()
        if (title.length < 3) {
            throw InvalidRequestException("title is required")
        }
        item.title = title
        item.description = request.description?.trim()?.takeIf { it.isNotEmpty() }
        item.startsAt = request.startsAt
        item.location = request.location?.trim()?.takeIf { it.isNotEmpty() }
        item.type = request.type
        item.programCode = request.programCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        return toDto(item)
    }

    @Transactional
    fun delete(id: UUID) {
        val item = portalEventRepository.findById(id).orElseThrow { EntityNotFoundException("Event $id not found") }
        assertCanManage(item.programCode)
        portalEventRepository.delete(item)
    }

    private fun assertCanManage(programCode: String?) {
        val roles = currentUserRoles() ?: throw NotAuthorizedException()
        if (roles.any { it == ADMIN || it == ADMIN_VOLUNTEER || it == ADMIN_SSO || it == MAIN_VOLUNTEER }) {
            return
        }
        if (!programCuratorService.isCurrentCurator()) {
            throw NotAuthorizedException()
        }
        val programs = programCuratorService.programCodesOfCurrentUser()
        if (programs.isEmpty()) {
            throw NotAuthorizedException()
        }
        val code = programCode?.trim()?.uppercase()
        if (!code.isNullOrBlank() && programs.none { it.equals(code, ignoreCase = true) }) {
            throw NotAuthorizedException()
        }
    }

    private fun toDto(item: PortalEvent) = PortalEventDto(
        id = item.id!!,
        createTime = item.createTime,
        createdBy = item.createdBy,
        title = item.title,
        description = item.description,
        startsAt = item.startsAt,
        location = item.location,
        type = item.type,
        programCode = item.programCode,
    )
}
