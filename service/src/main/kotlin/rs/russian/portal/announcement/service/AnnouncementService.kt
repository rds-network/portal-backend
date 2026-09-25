package rs.russian.portal.announcement.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.generated.model.AnnouncementCreateRequest
import rs.russian.generated.model.AnnouncementDto
import rs.russian.generated.model.UnreadAnnouncementsCountDto
import rs.russian.generated.model.AnnouncementAudience as ApiAnnouncementAudience
import rs.russian.portal.announcement.domain.Announcement
import rs.russian.portal.announcement.domain.enums.AnnouncementAudience
import rs.russian.portal.announcement.mapper.AnnouncementMapper
import rs.russian.portal.announcement.repository.AnnouncementReadRepository
import rs.russian.portal.announcement.repository.AnnouncementRepository
import rs.russian.portal.program.domain.Program
import rs.russian.portal.program.repository.ProgramRepository
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.service.AccountService
import java.util.UUID

@Service
class AnnouncementService(
    private val announcementRepository: AnnouncementRepository,
    private val announcementReadRepository: AnnouncementReadRepository,
    private val announcementMapper: AnnouncementMapper,
    private val accountService: AccountService,
    private val programRepository: ProgramRepository,
) {

    @Transactional(readOnly = true)
    fun getForCurrentUser(): List<AnnouncementDto> {
        val account = accountService.getCurrentAccount()
        if (!account.active) return emptyList()

        val programCode = account.info?.program?.code
        val readIds = announcementReadRepository.findAnnouncementIdsByAccountId(account.id!!).toSet()

        return announcementRepository.findForUser(programCode, account.username)
            .map { announcementMapper.map(it, readIds.contains(it.id)) }
    }

    @Transactional(readOnly = true)
    fun getUnreadCount(): UnreadAnnouncementsCountDto {
        val account = accountService.getCurrentAccount()
        if (!account.active) return UnreadAnnouncementsCountDto(0)

        val programCode = account.info?.program?.code
        val count = announcementRepository.countUnreadForUser(programCode, account.username, account.id!!).toInt()
        return UnreadAnnouncementsCountDto(count)
    }

    @Transactional
    fun markRead(announcementId: UUID) {
        val account = accountService.getCurrentAccount()
        val announcement = announcementRepository.findById(announcementId)
            .orElseThrow { EntityNotFoundException("Announcement $announcementId not found") }

        if (!matchesAudience(announcement, account, account.info?.program?.code)) {
            throw InvalidRequestException("Announcement is not available for current user")
        }

        announcementReadRepository.insertIfAbsent(
            id = UUID.randomUUID(),
            announcementId = announcementId,
            accountId = account.id!!,
        )
    }

    @Transactional
    fun create(request: AnnouncementCreateRequest): AnnouncementDto {
        validateCreateRequest(request)

        val audience = mapAudience(request.audience)
        val program = resolveProgram(audience, request.programCode)
        val createdBy = currentUserLogin() ?: throw NotAuthorizedException()

        val announcement = announcementRepository.save(
            Announcement(
                createdBy = createdBy,
                title = request.title.trim(),
                body = request.body.trim(),
                audience = audience,
                program = program,
            )
        )

        return announcementMapper.map(announcement, read = false)
    }

    private fun validateCreateRequest(request: AnnouncementCreateRequest) {
        val audience = mapAudience(request.audience)
        if (audience == AnnouncementAudience.PROGRAM && request.programCode.isNullOrBlank()) {
            throw InvalidRequestException("programCode is required when audience is PROGRAM")
        }
    }

    private fun resolveProgram(audience: AnnouncementAudience, programCode: String?): Program? {
        if (audience != AnnouncementAudience.PROGRAM) {
            return null
        }

        val code = programCode?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw InvalidRequestException("programCode is required when audience is PROGRAM")

        return programRepository.findByCode(code)
            ?: throw InvalidRequestException("Program with code '$code' not found")
    }

    private fun mapAudience(audience: ApiAnnouncementAudience): AnnouncementAudience =
        when (audience) {
            ApiAnnouncementAudience.ALL -> AnnouncementAudience.ALL
            ApiAnnouncementAudience.PROGRAM -> AnnouncementAudience.PROGRAM
        }

    private fun matchesAudience(announcement: Announcement, account: Account, programCode: String?): Boolean {
        if (!account.active) {
            return false
        }

        return when (announcement.audience) {
            AnnouncementAudience.ALL -> true
            AnnouncementAudience.PROGRAM ->
                !programCode.isNullOrBlank() && announcement.program?.code.equals(programCode, ignoreCase = true)
            AnnouncementAudience.USER ->
                announcement.targetUsername.equals(account.username, ignoreCase = true)
        }
    }

    @Transactional
    fun createForUser(username: String, title: String, body: String): AnnouncementDto {
        val login = username.trim()
        accountService.findAccountByLogin(login)
            ?: throw InvalidRequestException("Recipient '$login' not found")
        if (title.trim().length < 3 || body.trim().isEmpty()) {
            throw InvalidRequestException("title and body are required")
        }
        val createdBy = currentUserLogin() ?: throw NotAuthorizedException()
        val announcement = announcementRepository.save(
            Announcement(
                createdBy = createdBy,
                title = title.trim(),
                body = body.trim(),
                audience = AnnouncementAudience.USER,
                targetUsername = login,
            )
        )
        return announcementMapper.map(announcement, read = false)
    }
}
