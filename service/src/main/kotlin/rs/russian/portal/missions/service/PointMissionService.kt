package rs.russian.portal.missions.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.achievements.service.AchievementsService
import rs.russian.portal.missions.api.PointMissionClaimResult
import rs.russian.portal.missions.api.PointMissionDto
import rs.russian.portal.missions.api.PointMissionWriteRequest
import rs.russian.portal.missions.domain.PointMission
import rs.russian.portal.missions.repository.PointMissionRepository
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.shared.security.currentUserRoles
import rs.russian.portal.user.domain.enums.UserGroup
import java.util.UUID

@Service
class PointMissionService(
    private val missions: PointMissionRepository,
    private val achievementsService: AchievementsService,
) {

    @Transactional(readOnly = true)
    fun listForVolunteer(): List<PointMissionDto> {
        val login = currentUserLogin() ?: throw NotAuthorizedException()
        return missions.findByActiveTrueOrderBySortOrderAscCreatedAtAsc().map { toDto(it, login) }
    }

    @Transactional(readOnly = true)
    fun listAdmin(): List<PointMissionDto> {
        assertCanManage()
        return missions.findAllByOrderBySortOrderAscCreatedAtAsc().map { toDto(it, null) }
    }

    @Transactional
    fun create(request: PointMissionWriteRequest): PointMissionDto {
        assertCanManage()
        val login = currentUserLogin() ?: throw NotAuthorizedException()
        val visual = normalizeVisual(request.visualType, request.visualKey, request.imageUrl)
        val entity = PointMission(
            title = request.title.trim(),
            description = request.description?.trim()?.takeIf { it.isNotEmpty() },
            points = validatePoints(request.points),
            link = normalizeLink(request.link),
            active = request.active,
            oneTime = request.oneTime,
            sortOrder = request.sortOrder,
            visualType = visual.type,
            visualKey = visual.key,
            imageUrl = visual.imageUrl,
            createdBy = login,
        )
        return toDto(missions.save(entity), null)
    }

    @Transactional
    fun update(id: UUID, request: PointMissionWriteRequest): PointMissionDto {
        assertCanManage()
        val entity = missions.findById(id).orElseThrow { InvalidRequestException("Mission not found") }
        val visual = normalizeVisual(request.visualType, request.visualKey, request.imageUrl)
        entity.title = request.title.trim()
        entity.description = request.description?.trim()?.takeIf { it.isNotEmpty() }
        entity.points = validatePoints(request.points)
        entity.link = normalizeLink(request.link)
        entity.active = request.active
        entity.oneTime = request.oneTime
        entity.sortOrder = request.sortOrder
        entity.visualType = visual.type
        entity.visualKey = visual.key
        entity.imageUrl = visual.imageUrl
        return toDto(missions.save(entity), null)
    }

    @Transactional
    fun delete(id: UUID) {
        assertCanManage()
        if (!missions.existsById(id)) throw InvalidRequestException("Mission not found")
        missions.deleteById(id)
    }

    @Transactional
    fun claim(id: UUID): PointMissionClaimResult {
        val login = currentUserLogin() ?: throw NotAuthorizedException()
        val mission = missions.findById(id).orElseThrow { InvalidRequestException("Mission not found") }
        if (!mission.active) throw InvalidRequestException("Mission is inactive")
        val result = achievementsService.claimMission(
            username = login,
            missionId = mission.id,
            points = mission.points,
            title = mission.title,
            oneTime = mission.oneTime,
        )
        return PointMissionClaimResult(
            missionId = mission.id.toString(),
            points = mission.points,
            balance = result.balance,
            alreadyClaimed = result.alreadyClaimed,
        )
    }

    private fun toDto(mission: PointMission, username: String?): PointMissionDto {
        val claimed = username?.let {
            achievementsService.hasMissionClaim(it, mission.id)
        } ?: false
        return PointMissionDto(
            id = mission.id.toString(),
            title = mission.title,
            description = mission.description,
            points = mission.points,
            link = mission.link,
            active = mission.active,
            oneTime = mission.oneTime,
            sortOrder = mission.sortOrder,
            visualType = mission.visualType,
            visualKey = mission.visualKey,
            imageUrl = mission.imageUrl,
            claimed = claimed,
        )
    }

    private data class Visual(val type: String, val key: String?, val imageUrl: String?)

    private fun normalizeVisual(typeRaw: String?, keyRaw: String?, imageRaw: String?): Visual {
        val type = (typeRaw ?: "PICTOGRAM").trim().uppercase()
        if (type !in ALLOWED_TYPES) {
            throw InvalidRequestException("visualType must be PICTOGRAM, LOGO or COVER")
        }
        return when (type) {
            "PICTOGRAM" -> {
                val key = keyRaw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "star"
                if (key !in ALLOWED_PICTOGRAMS) {
                    throw InvalidRequestException("Unknown pictogram: $key")
                }
                Visual(type = type, key = key, imageUrl = null)
            }
            else -> {
                val url = normalizeImageUrl(imageRaw)
                    ?: throw InvalidRequestException("imageUrl is required for $type")
                Visual(type = type, key = null, imageUrl = url)
            }
        }
    }

    private fun normalizeImageUrl(url: String?): String? {
        val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!raw.startsWith("http://", ignoreCase = true) && !raw.startsWith("https://", ignoreCase = true)) {
            throw InvalidRequestException("imageUrl must start with http:// or https://")
        }
        if (raw.length > 1024) throw InvalidRequestException("imageUrl is too long")
        return raw
    }

    private fun validatePoints(points: Int): Int {
        if (points < 1 || points > 10_000) throw InvalidRequestException("Points must be between 1 and 10000")
        return points
    }

    private fun normalizeLink(link: String?): String? {
        val raw = link?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!raw.startsWith("http://", ignoreCase = true) && !raw.startsWith("https://", ignoreCase = true)) {
            throw InvalidRequestException("Link must start with http:// or https://")
        }
        return raw
    }

    private fun assertCanManage() {
        val roles = currentUserRoles() ?: emptySet()
        val allowed = setOf(
            UserGroup.ADMIN,
            UserGroup.ADMIN_SSO,
            UserGroup.ADMIN_VOLUNTEER,
            UserGroup.MAIN_VOLUNTEER,
        )
        if (roles.none { it in allowed }) throw NotAuthorizedException()
    }

    companion object {
        private val ALLOWED_TYPES = setOf("PICTOGRAM", "LOGO", "COVER")
        private val ALLOWED_PICTOGRAMS = setOf(
            "instagram",
            "survey",
            "event",
            "star",
            "heart",
            "users",
            "book",
            "leaf",
            "handshake",
            "gift",
            "camera",
            "link",
        )
    }
}
