package rs.russian.portal.missions.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.achievements.service.AchievementsService
import rs.russian.portal.missions.api.PointMissionAwardDto
import rs.russian.portal.missions.api.PointMissionClaimResult
import rs.russian.portal.missions.api.PointMissionDto
import rs.russian.portal.missions.api.PointMissionRejectRequest
import rs.russian.portal.missions.api.PointMissionSubmissionDto
import rs.russian.portal.missions.api.PointMissionSubmitRequest
import rs.russian.portal.missions.api.PointMissionWriteRequest
import rs.russian.portal.missions.domain.PointMission
import rs.russian.portal.missions.domain.PointMissionSubmission
import rs.russian.portal.missions.repository.PointMissionRepository
import rs.russian.portal.missions.repository.PointMissionSubmissionRepository
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.shared.security.currentUserRoles
import rs.russian.portal.user.domain.enums.UserGroup
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class PointMissionService(
    private val missions: PointMissionRepository,
    private val submissions: PointMissionSubmissionRepository,
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

    @Transactional(readOnly = true)
    fun listPendingSubmissions(): List<PointMissionSubmissionDto> {
        assertCanManage()
        return submissions.findByStatusOrderByCreatedAtAsc(STATUS_PENDING).mapNotNull { toSubmissionDto(it) }
    }

    @Transactional(readOnly = true)
    fun listAwardHistory(): List<PointMissionAwardDto> {
        assertCanManage()
        return achievementsService.listMissionClaimEvents().map { event ->
            val missionId = parseMissionId(event.refId)
            val mission = missionId?.let { missions.findById(it).orElse(null) }
            val submission = if (missionId != null) {
                submissions.findFirstByUsernameAndMissionIdAndStatusOrderByReviewedAtDescCreatedAtDesc(
                    event.username,
                    missionId,
                    STATUS_APPROVED,
                )
            } else {
                null
            }
            PointMissionAwardDto(
                id = event.id.toString(),
                username = event.username,
                missionId = missionId?.toString(),
                missionTitle = mission?.title
                    ?: event.title?.removePrefix("Миссия: ")?.trim()
                    ?: event.code,
                points = event.points,
                proofText = submission?.proofText,
                reviewedBy = submission?.reviewedBy,
                awardedAt = event.createdAt.format(ISO),
                source = if (submission != null) SOURCE_REVIEW else SOURCE_INSTANT,
            )
        }
    }

    private fun parseMissionId(refId: String?): UUID? {
        if (refId.isNullOrBlank()) return null
        // one-time: "<uuid>"; repeatable: "<uuid>-<timestamp>"
        val candidate = if (refId.length > 36 && refId[36] == '-') refId.take(36) else refId
        return try {
            UUID.fromString(candidate)
        } catch (_: Exception) {
            null
        }
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
            requiresReview = request.requiresReview,
            proofLabel = request.proofLabel?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
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
        entity.requiresReview = request.requiresReview
        entity.proofLabel = request.proofLabel?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
        return toDto(missions.save(entity), null)
    }

    @Transactional
    fun delete(id: UUID) {
        assertCanManage()
        if (!missions.existsById(id)) throw InvalidRequestException("Mission not found")
        missions.deleteById(id)
    }

    /** Instant claim — only when mission does not require review. */
    @Transactional
    fun claim(id: UUID): PointMissionClaimResult {
        val login = currentUserLogin() ?: throw NotAuthorizedException()
        val mission = missions.findById(id).orElseThrow { InvalidRequestException("Mission not found") }
        if (!mission.active) throw InvalidRequestException("Mission is inactive")
        if (mission.requiresReview) {
            throw InvalidRequestException("This mission requires proof submission and moderator approval")
        }
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
            submissionStatus = if (result.alreadyClaimed) STATUS_APPROVED else STATUS_APPROVED,
        )
    }

    @Transactional
    fun submit(id: UUID, request: PointMissionSubmitRequest): PointMissionClaimResult {
        val login = currentUserLogin() ?: throw NotAuthorizedException()
        val mission = missions.findById(id).orElseThrow { InvalidRequestException("Mission not found") }
        if (!mission.active) throw InvalidRequestException("Mission is inactive")
        if (!mission.requiresReview) {
            throw InvalidRequestException("This mission does not require review — use claim")
        }
        val proof = request.proofText.trim()
        if (proof.length < 2) throw InvalidRequestException("Proof text is required")
        if (proof.length > 500) throw InvalidRequestException("Proof text is too long")

        if (achievementsService.hasMissionClaim(login, mission.id)) {
            return PointMissionClaimResult(
                missionId = mission.id.toString(),
                points = mission.points,
                balance = achievementsService.balanceOf(login),
                alreadyClaimed = true,
                submissionStatus = STATUS_APPROVED,
            )
        }
        if (submissions.existsByUsernameAndMissionIdAndStatus(login, mission.id, STATUS_PENDING)) {
            throw InvalidRequestException("Submission already pending review")
        }

        submissions.save(
            PointMissionSubmission(
                missionId = mission.id,
                username = login,
                proofText = proof,
                status = STATUS_PENDING,
            )
        )
        return PointMissionClaimResult(
            missionId = mission.id.toString(),
            points = 0,
            balance = achievementsService.balanceOf(login),
            alreadyClaimed = false,
            submissionStatus = STATUS_PENDING,
        )
    }

    @Transactional
    fun approveSubmission(submissionId: UUID): PointMissionSubmissionDto {
        assertCanManage()
        val reviewer = currentUserLogin() ?: throw NotAuthorizedException()
        val submission = submissions.findById(submissionId)
            .orElseThrow { InvalidRequestException("Submission not found") }
        if (submission.status != STATUS_PENDING) {
            throw InvalidRequestException("Submission is not pending")
        }
        val mission = missions.findById(submission.missionId)
            .orElseThrow { InvalidRequestException("Mission not found") }

        submission.status = STATUS_APPROVED
        submission.reviewedBy = reviewer
        submission.reviewedAt = LocalDateTime.now()
        submission.rejectReason = null
        submissions.save(submission)

        achievementsService.claimMission(
            username = submission.username,
            missionId = mission.id,
            points = mission.points,
            title = mission.title,
            oneTime = mission.oneTime,
        )
        return toSubmissionDto(submission)
            ?: throw InvalidRequestException("Mission not found")
    }

    @Transactional
    fun rejectSubmission(submissionId: UUID, request: PointMissionRejectRequest): PointMissionSubmissionDto {
        assertCanManage()
        val reviewer = currentUserLogin() ?: throw NotAuthorizedException()
        val submission = submissions.findById(submissionId)
            .orElseThrow { InvalidRequestException("Submission not found") }
        if (submission.status != STATUS_PENDING) {
            throw InvalidRequestException("Submission is not pending")
        }
        submission.status = STATUS_REJECTED
        submission.reviewedBy = reviewer
        submission.reviewedAt = LocalDateTime.now()
        submission.rejectReason = request.reason?.trim()?.takeIf { it.isNotEmpty() }?.take(500)
        submissions.save(submission)
        return toSubmissionDto(submission)
            ?: throw InvalidRequestException("Mission not found")
    }

    private fun toDto(mission: PointMission, username: String?): PointMissionDto {
        val claimed = username?.let { achievementsService.hasMissionClaim(it, mission.id) } ?: false
        var submissionStatus: String? = null
        var proofText: String? = null
        var rejectReason: String? = null
        if (username != null && mission.requiresReview) {
            val latest = submissions.findFirstByUsernameAndMissionIdAndStatusInOrderByCreatedAtDesc(
                username,
                mission.id,
                listOf(STATUS_PENDING, STATUS_APPROVED, STATUS_REJECTED),
            )
            if (latest != null) {
                submissionStatus = latest.status
                proofText = latest.proofText
                rejectReason = latest.rejectReason
            } else if (claimed) {
                submissionStatus = STATUS_APPROVED
            }
        } else if (claimed) {
            submissionStatus = STATUS_APPROVED
        }
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
            requiresReview = mission.requiresReview,
            proofLabel = mission.proofLabel,
            claimed = claimed,
            submissionStatus = submissionStatus,
            proofText = proofText,
            rejectReason = rejectReason,
        )
    }

    private fun toSubmissionDto(submission: PointMissionSubmission): PointMissionSubmissionDto? {
        val mission = missions.findById(submission.missionId).orElse(null) ?: return null
        return PointMissionSubmissionDto(
            id = submission.id.toString(),
            missionId = mission.id.toString(),
            missionTitle = mission.title,
            points = mission.points,
            username = submission.username,
            proofText = submission.proofText,
            status = submission.status,
            rejectReason = submission.rejectReason,
            reviewedBy = submission.reviewedBy,
            reviewedAt = submission.reviewedAt?.format(ISO),
            createdAt = submission.createdAt.format(ISO),
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
        private val ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        private const val STATUS_PENDING = "PENDING"
        private const val STATUS_APPROVED = "APPROVED"
        private const val STATUS_REJECTED = "REJECTED"
        private const val SOURCE_REVIEW = "REVIEW"
        private const val SOURCE_INSTANT = "INSTANT"
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
