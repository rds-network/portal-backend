package rs.russian.portal.orglink.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.orglink.api.OrgLinkDto
import rs.russian.portal.orglink.api.OrgLinkWriteRequest
import rs.russian.portal.orglink.domain.OrgLink
import rs.russian.portal.orglink.repository.OrgLinkRepository
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin
import java.util.UUID

@Service
class OrgLinkService(
    private val orgLinkRepository: OrgLinkRepository,
) {

    @Transactional(readOnly = true)
    fun list(): List<OrgLinkDto> = orgLinkRepository.findAllByOrderBySortOrderAscTitleAsc().map(::toDto)

    @Transactional
    fun create(request: OrgLinkWriteRequest): OrgLinkDto {
        val createdBy = currentUserLogin() ?: throw NotAuthorizedException()
        val title = request.title.trim()
        val url = request.url.trim()
        if (title.length < 2 || url.length < 8) {
            throw InvalidRequestException("title and url are required")
        }
        val maxOrder = orgLinkRepository.findAllByOrderBySortOrderAscTitleAsc().maxOfOrNull { it.sortOrder } ?: 0
        val saved = orgLinkRepository.save(
            OrgLink(
                createdBy = createdBy,
                title = title,
                url = url,
                description = request.description?.trim()?.takeIf { it.isNotEmpty() },
                sortOrder = request.sortOrder ?: (maxOrder + 10),
            )
        )
        return toDto(saved)
    }

    @Transactional
    fun update(id: UUID, request: OrgLinkWriteRequest): OrgLinkDto {
        val item = orgLinkRepository.findById(id).orElseThrow { EntityNotFoundException("Link $id not found") }
        val title = request.title.trim()
        val url = request.url.trim()
        if (title.length < 2 || url.length < 8) {
            throw InvalidRequestException("title and url are required")
        }
        item.title = title
        item.url = url
        item.description = request.description?.trim()?.takeIf { it.isNotEmpty() }
        request.sortOrder?.let { item.sortOrder = it }
        return toDto(item)
    }

    @Transactional
    fun delete(id: UUID) {
        if (!orgLinkRepository.existsById(id)) {
            throw EntityNotFoundException("Link $id not found")
        }
        orgLinkRepository.deleteById(id)
    }

    private fun toDto(item: OrgLink) = OrgLinkDto(
        id = item.id!!,
        createTime = item.createTime,
        createdBy = item.createdBy,
        title = item.title,
        url = item.url,
        description = item.description,
        sortOrder = item.sortOrder,
    )
}
