package rs.russian.portal.announcement.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.generated.model.AnnouncementCreateRequest
import rs.russian.generated.model.AnnouncementDto
import rs.russian.generated.model.UnreadAnnouncementsCountDto
import rs.russian.generated.model.AnnouncementAudience as ApiAnnouncementAudience
import rs.russian.portal.announcement.api.BannerDto
import rs.russian.portal.announcement.domain.Announcement
import rs.russian.portal.announcement.domain.enums.AnnouncementAudience
import rs.russian.portal.announcement.mapper.AnnouncementMapper
import rs.russian.portal.announcement.repository.AnnouncementReadRepository
import rs.russian.portal.announcement.repository.AnnouncementRepository
import rs.russian.portal.program.domain.Program
import rs.russian.portal.program.repository.ProgramRepository
import rs.russian.portal.program.service.ProgramCuratorService
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.shared.security.currentUserRoles
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_SSO
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import rs.russian.portal.user.domain.enums.UserGroup.MAIN_VOLUNTEER
import rs.russian.portal.user.service.AccountService
import java.util.UUID

@Service
class AnnouncementService(
    private val announcementRepository: AnnouncementRepository,
    private val announcementReadRepository: AnnouncementReadRepository,
    private val announcementMapper: AnnouncementMapper,
    private val accountService: AccountService,
    private val programRepository: ProgramRepository,
    private val programCuratorService: ProgramCuratorService,
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

    @Transactional(readOnly = true)
    fun getActiveBanner(): BannerDto? {
        val account = accountService.getCurrentAccount()
        if (!account.active) return null
        val programCode = account.info?.program?.code
        val announcement = announcementRepository.findLatestUnreadBanner(
            programCode,
            account.username,
            account.id!!,
        ) ?: return null
        return BannerDto(
            id = announcement.id!!,
            title = announcement.title,
            body = announcement.body,
            createdBy = announcement.createdBy,
            createTime = announcement.createTime.toString(),
        )
    }

    @Transactional
    fun create(request: AnnouncementCreateRequest): AnnouncementDto {
        validateCreateRequest(request)

        val audience = mapAudience(request.audience)
        assertCanPublish(audience, request.programCode)
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

    @Transactional
    fun publish(
        title: String,
        body: String,
        audienceName: String,
        programCode: String?,
        username: String?,
        banner: Boolean,
    ): AnnouncementDto {
        val audience = when (audienceName.trim().uppercase()) {
            "ALL" -> AnnouncementAudience.ALL
            "PROGRAM" -> AnnouncementAudience.PROGRAM
            "USER" -> AnnouncementAudience.USER
            else -> throw InvalidRequestException("Unknown audience")
        }
        if (title.trim().length < 3 || body.trim().isEmpty()) {
            throw InvalidRequestException("title and body are required")
        }
        if (audience == AnnouncementAudience.PROGRAM && programCode.isNullOrBlank()) {
            throw InvalidRequestException("programCode is required when audience is PROGRAM")
        }
        if (audience == AnnouncementAudience.USER && username.isNullOrBlank()) {
            throw InvalidRequestException("username is required when audience is USER")
        }
        if (banner && audience == AnnouncementAudience.USER) {
            throw InvalidRequestException("Banner cannot be personal")
        }
        assertCanPublish(audience, programCode)

        val createdBy = currentUserLogin() ?: throw NotAuthorizedException()
        val program = resolveProgram(audience, programCode)
        val target = username?.trim()?.takeIf { audience == AnnouncementAudience.USER }
        if (target != null) {
            accountService.findAccountByLogin(target)
                ?: throw InvalidRequestException("Recipient '$target' not found")
        }
        val announcement = announcementRepository.save(
            Announcement(
                createdBy = createdBy,
                title = title.trim(),
                body = body.trim(),
                audience = audience,
                program = program,
                targetUsername = target,
                banner = banner,
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

    private fun assertCanPublish(audience: AnnouncementAudience, programCode: String?) {
        val roles = currentUserRoles() ?: throw NotAuthorizedException()
        val managers = setOf(ADMIN, ADMIN_VOLUNTEER, ADMIN_SSO, MAIN_VOLUNTEER)
        if (roles.any { it in managers }) {
            return
        }
        val login = currentUserLogin() ?: throw NotAuthorizedException()
        val programs = programCuratorService.programCodesOf(login)
        if (programs.isEmpty()) {
            throw NotAuthorizedException()
        }
        if (audience != AnnouncementAudience.PROGRAM) {
            throw InvalidRequestException("Team leads can publish only to their program")
        }
        val code = programCode?.trim()?.uppercase()
        if (code.isNullOrBlank() || programs.none { it.equals(code, ignoreCase = true) }) {
            throw InvalidRequestException("Team leads can publish only to their program")
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
        assertCanPublish(AnnouncementAudience.USER, null)
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
