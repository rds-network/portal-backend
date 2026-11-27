package rs.russian.portal.yearreview.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.achievements.repository.VolunteerPointEventRepository
import rs.russian.portal.achievements.service.AchievementCatalog
import rs.russian.portal.achievements.service.AchievementsService
import rs.russian.portal.program.repository.ProgramCuratorDelegateRepository
import rs.russian.portal.program.repository.ProgramCuratorRepository
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.report.repository.ReportCustomerDecisionRepository
import rs.russian.portal.report.repository.ReportRepository
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.shared.security.currentUserRoles
import rs.russian.portal.talent.repository.TalentPostRepository
import rs.russian.portal.talent.repository.TalentResponseRepository
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.yearreview.api.AnnualCertificateDto
import rs.russian.portal.yearreview.api.AnnualCertificateVerifyDto
import rs.russian.portal.yearreview.api.AttachCertificatePdfRequest
import rs.russian.portal.yearreview.api.YearReviewCriteriaDto
import rs.russian.portal.yearreview.api.YearReviewCriteriaUpdateRequest
import rs.russian.portal.yearreview.api.YearReviewCriterionProgressDto
import rs.russian.portal.yearreview.api.YearReviewDto
import rs.russian.portal.yearreview.api.YearReviewEligibilityDto
import rs.russian.portal.yearreview.api.YearReviewStatsDto
import rs.russian.portal.yearreview.domain.AnnualCertificate
import rs.russian.portal.yearreview.domain.AnnualCertificateSource
import rs.russian.portal.yearreview.domain.AnnualCertificateStatus
import rs.russian.portal.yearreview.domain.YearReviewCriteria
import rs.russian.portal.yearreview.repository.AnnualCertificateRepository
import rs.russian.portal.yearreview.repository.YearReviewCriteriaRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class YearReviewService(
    private val pointEvents: VolunteerPointEventRepository,
    private val reportRepository: ReportRepository,
    private val decisionRepository: ReportCustomerDecisionRepository,
    private val accountRepository: AccountRepository,
    private val curatorRepository: ProgramCuratorRepository,
    private val curatorDelegateRepository: ProgramCuratorDelegateRepository,
    private val talentPostRepository: TalentPostRepository,
    private val talentResponseRepository: TalentResponseRepository,
    private val criteriaRepository: YearReviewCriteriaRepository,
    private val certificateRepository: AnnualCertificateRepository,
    private val achievementsService: AchievementsService,
    private val objectMapper: ObjectMapper,
) {

    @Transactional(readOnly = true)
    fun myReview(year: Int): YearReviewDto {
        val login = currentUserLogin() ?: throw NotAuthorizedException()
        return buildReview(login, year)
    }

    @Transactional(readOnly = true)
    fun myDocuments(): List<AnnualCertificateDto> {
        val login = currentUserLogin() ?: throw NotAuthorizedException()
        return certificateRepository.findAllByUsernameIgnoreCaseOrderByYearDesc(login).map { toDto(it) }
    }

    @Transactional
    fun issueMyCertificate(year: Int): AnnualCertificateDto {
        val login = currentUserLogin() ?: throw NotAuthorizedException()
        val review = buildReview(login, year)
        if (!review.eligibility.eligible && review.certificate == null) {
            throw InvalidRequestException("Not eligible for annual certificate")
        }
        return issueInternal(login, year, AnnualCertificateSource.AUTOMATIC, review)
    }

    @Transactional
    fun attachPdf(id: UUID, request: AttachCertificatePdfRequest): AnnualCertificateDto {
        val login = currentUserLogin() ?: throw NotAuthorizedException()
        val cert = certificateRepository.findById(id).orElseThrow { InvalidRequestException("Certificate not found") }
        if (!cert.username.equals(login, ignoreCase = true) && !isManager()) {
            throw NotAuthorizedException()
        }
        if (cert.status != AnnualCertificateStatus.ISSUED) {
            throw InvalidRequestException("Certificate is not issued")
        }
        val fileId = runCatching { UUID.fromString(request.fileId) }.getOrNull()
            ?: throw InvalidRequestException("Invalid file id")
        if (cert.pdfFileId == null) {
            cert.pdfFileId = fileId
            cert.updatedAt = LocalDateTime.now()
            certificateRepository.save(cert)
        }
        return toDto(cert)
    }

    @Transactional(readOnly = true)
    fun verify(token: String): AnnualCertificateVerifyDto {
        val uuid = runCatching { UUID.fromString(token) }.getOrNull()
            ?: return AnnualCertificateVerifyDto(
                valid = false,
                status = "INVALID",
                recipientFullName = null,
                documentTitle = "Захвалница RADIMO",
                year = 0,
                certificateNumber = "",
                issuedAt = "",
                organization = ORGANIZATION,
            )
        val cert = certificateRepository.findByVerificationToken(uuid).orElse(null)
            ?: return AnnualCertificateVerifyDto(
                valid = false,
                status = "NOT_FOUND",
                recipientFullName = null,
                documentTitle = "Захвалница RADIMO",
                year = 0,
                certificateNumber = "",
                issuedAt = "",
                organization = ORGANIZATION,
            )
        val valid = cert.status == AnnualCertificateStatus.ISSUED
        return AnnualCertificateVerifyDto(
            valid = valid,
            status = cert.status.name,
            recipientFullName = cert.recipientFullName,
            documentTitle = "Захвалница за ${cert.year}. годину · Супер-учесник RADIMO",
            year = cert.year,
            certificateNumber = cert.certificateNumber,
            issuedAt = cert.issuedAt.toString(),
            organization = ORGANIZATION,
        )
    }

    @Transactional(readOnly = true)
    fun getCriteria(year: Int): YearReviewCriteriaDto = toCriteriaDto(criteriaFor(year))

    @Transactional
    fun updateCriteria(year: Int, request: YearReviewCriteriaUpdateRequest): YearReviewCriteriaDto {
        requireManager()
        val entity = criteriaFor(year)
        request.topRankMax?.let { entity.topRankMax = it.coerceAtLeast(1) }
        request.minAcceptedReports?.let { entity.minAcceptedReports = it.coerceAtLeast(0) }
        request.minEarnedPoints?.let { entity.minEarnedPoints = it.coerceAtLeast(0) }
        request.minActiveWeeks?.let { entity.minActiveWeeks = it.coerceAtLeast(0) }
        request.minBadges?.let { entity.minBadges = it.coerceAtLeast(0) }
        request.minCriteriaMet?.let { entity.minCriteriaMet = it.coerceIn(1, 5) }
        request.enabled?.let { entity.enabled = it }
        entity.updatedAt = LocalDateTime.now()
        return toCriteriaDto(criteriaRepository.save(entity))
    }

    @Transactional
    fun adminIssue(username: String, year: Int): AnnualCertificateDto {
        requireManager()
        val review = buildReview(username, year)
        return issueInternal(username, year, AnnualCertificateSource.MANUAL, review)
    }

    @Transactional
    fun adminRevoke(id: UUID, reason: String?): AnnualCertificateDto {
        requireManager()
        val cert = certificateRepository.findById(id).orElseThrow { InvalidRequestException("Certificate not found") }
        if (cert.status == AnnualCertificateStatus.REVOKED) return toDto(cert)
        cert.status = AnnualCertificateStatus.REVOKED
        cert.revokedAt = LocalDateTime.now()
        cert.revokedBy = currentUserLogin()
        cert.revokeReason = reason?.take(1000)
        cert.updatedAt = LocalDateTime.now()
        return toDto(certificateRepository.save(cert))
    }

    private fun buildReview(username: String, year: Int): YearReviewDto {
        val stats = computeStats(username, year)
        val criteria = criteriaFor(year)
        val eligibility = evaluate(stats, criteria)
        val certificate = certificateRepository
            .findByUsernameIgnoreCaseAndYearAndTemplateVersion(
                username,
                year,
                AnnualCertificate.TEMPLATE_ANNUAL_SUPER,
            )
            .orElse(null)
            ?.let { toDto(it) }
        val special = buildList {
            if (stats.isPodium) add("PODIUM")
            if (stats.curatorGratitudes > 0 || stats.managerGratitudes > 0) add("GRATITUDE")
            if (eligibility.eligible) add("SUPER_PARTICIPANT")
        }
        val ready = year < LocalDate.now().year ||
            (year == LocalDate.now().year && LocalDate.now().monthValue >= 11)
        return YearReviewDto(
            year = year,
            ready = ready,
            stats = stats,
            eligibility = eligibility,
            certificate = certificate,
            specialThanks = special,
        )
    }

    private fun computeStats(username: String, year: Int): YearReviewStatsDto {
        val from = LocalDateTime.of(year, 1, 1, 0, 0)
        val until = LocalDateTime.of(year + 1, 1, 1, 0, 0)
        val fromOd = from.atOffset(ZoneOffset.UTC)
        val untilOd = until.atOffset(ZoneOffset.UTC)

        val account = accountRepository.findAllByUsernameLowerIn(listOf(username.lowercase())).firstOrNull()
        val events = pointEvents.findByUsernameAndCreatedAtRange(username, from, until)
        val weeklyRefs = events
            .asSequence()
            .filter { it.code == AchievementsService.CODE_WEEKLY_LOGIN }
            .mapNotNull { it.refId?.trim()?.takeIf { r -> r.isNotEmpty() } }
            .distinct()
            .sorted()
            .toList()

        val earnedPoints = pointEvents.sumPositivePointsInRange(username, from, until)
        val acceptedReports = reportRepository.countAcceptedInRange(
            username,
            ReportStatus.ACCEPTED,
            fromOd,
            untilOd,
        ).toInt()
        val completedTasks = pointEvents.countByUsernameAndCodeInRange(
            username,
            AchievementsService.CODE_MISSION_CLAIM,
            from,
            until,
        ).toInt()
        val curatorGratitudes = pointEvents.countByUsernameAndCodeInRange(
            username,
            AchievementsService.CODE_CURATOR_GRATITUDE,
            from,
            until,
        ).toInt()
        val managerGratitudes = pointEvents.countByUsernameAndCodeInRange(
            username,
            AchievementsService.CODE_MANAGER_GRATITUDE,
            from,
            until,
        ).toInt()

        val weeklyLogins = weeklyRefs.size
        val cleanReports = pointEvents.countByUsernameAndCodeInRange(
            username,
            AchievementsService.CODE_REPORT_CLEAN,
            from,
            until,
        ).toInt()
        val hasAvatar = events.any { it.code == AchievementsService.CODE_PROFILE_AVATAR } ||
            account?.info?.avatar != null
        val firstReport = events.any { it.code == AchievementsService.CODE_FIRST_ACCEPTED_REPORT } || acceptedReports > 0
        val board = achievementsService.leaderboard(10_000)
        val meOnBoard = board.leaders.find { it.username.equals(username, ignoreCase = true) }
            ?: board.me?.takeIf { it.username.equals(username, ignoreCase = true) }
        val currentRank = meOnBoard?.rank
        val badges = countBadges(
            weeklyLogins = weeklyLogins,
            cleanReports = cleanReports,
            curatorGratitudes = curatorGratitudes,
            managerGratitudes = managerGratitudes,
            hasAvatar = hasAvatar,
            acceptedReports = acceptedReports,
            earnedPoints = earnedPoints,
            currentRank = currentRank,
        )

        val program = account?.info?.program
        val curatorPrograms = curatorRepository.findAllByUsernameIgnoreCase(username)
        val directionsCount = curatorPrograms.map { it.programCode.lowercase() }.distinct().size
        val delegatesCount = curatorDelegateRepository.countByCuratorUsernameIgnoreCase(username).toInt()
        val reportsReviewed = decisionRepository.countAcceptedDecisionsInRange(username, fromOd, untilOd).toInt()
        val peopleHelped = decisionRepository.countDistinctHelpedAuthorsInRange(username, fromOd, untilOd).toInt()
        val isTeamBuilder = directionsCount > 0 || delegatesCount > 0 || reportsReviewed > 0

        val ideasCreated = talentPostRepository.countByAuthorInRange(username, fromOd, untilOd).toInt()
        val responsesReceived =
            talentResponseRepository.countReceivedOnOwnedPostsInRange(username, fromOd, untilOd).toInt()
        val responsesSent = talentResponseRepository.countSentByAuthorInRange(username, fromOd, untilOd).toInt()
        val teamsJoined = talentResponseRepository.countJoinedTeamsInRange(username, fromOd, untilOd).toInt()
        val missedWeeks = pointEvents.countByUsernameAndCodeInRange(
            username,
            AchievementsService.CODE_WEEKLY_MISS,
            from,
            until,
        ).toInt()
        // Heuristic: soft weekly misses or sparse accepted reports vs weeks online.
        val irregularReporting = missedWeeks > 0 ||
            (weeklyLogins >= 8 && acceptedReports < (weeklyLogins / 2).coerceAtLeast(1))

        return YearReviewStatsDto(
            year = year,
            displayName = account?.fullName?.takeIf { it.isNotBlank() } ?: username,
            username = account?.username ?: username,
            programCode = program?.code,
            programNameRu = program?.nameRu,
            programNameEn = program?.nameEn,
            programNameSr = program?.nameSr,
            activeWeeks = weeklyLogins,
            longestStreak = longestIsoWeekStreak(weeklyRefs),
            earnedPoints = earnedPoints,
            acceptedReports = acceptedReports,
            completedTasks = completedTasks,
            badgesEarned = badges,
            currentRank = currentRank,
            bestRank = currentRank,
            totalParticipants = board.totalParticipants,
            curatorGratitudes = curatorGratitudes,
            managerGratitudes = managerGratitudes,
            isPodium = currentRank != null && currentRank in 1..3,
            isTeamBuilder = isTeamBuilder,
            directionsCount = directionsCount,
            delegatesCount = delegatesCount,
            reportsReviewed = reportsReviewed,
            peopleHelped = peopleHelped,
            ideasCreated = ideasCreated,
            responsesReceived = responsesReceived,
            responsesSent = responsesSent,
            teamsJoined = teamsJoined,
            missedWeeks = missedWeeks,
            irregularReporting = irregularReporting,
        )
    }

    private fun countBadges(
        weeklyLogins: Int,
        cleanReports: Int,
        curatorGratitudes: Int,
        managerGratitudes: Int,
        hasAvatar: Boolean,
        acceptedReports: Int,
        earnedPoints: Long,
        currentRank: Int?,
    ): Int =
        AchievementCatalog.ALL.count { def ->
            val progress = when (def.kind) {
                AchievementCatalog.Kind.WEEKLY_LOGINS -> weeklyLogins
                AchievementCatalog.Kind.CLEAN_REPORTS -> cleanReports
                AchievementCatalog.Kind.CURATOR_GRATITUDE -> curatorGratitudes
                AchievementCatalog.Kind.MANAGER_GRATITUDE -> managerGratitudes
                AchievementCatalog.Kind.HAS_AVATAR -> if (hasAvatar) 1 else 0
                AchievementCatalog.Kind.ANY_ACCEPTED_REPORT -> if (acceptedReports > 0) 1 else 0
                AchievementCatalog.Kind.POSITIVE_BALANCE -> if (earnedPoints > 0) 1 else 0
                AchievementCatalog.Kind.IN_LEADERBOARD -> if (currentRank != null && currentRank > 0) 1 else 0
                AchievementCatalog.Kind.LEADERBOARD_TOP ->
                    if (currentRank != null && currentRank > 0 && currentRank <= def.target) 1 else 0
                AchievementCatalog.Kind.EKOMAPA_PODIUM_BEST -> 0
            }
            progress >= def.target
        }

    private fun evaluate(stats: YearReviewStatsDto, criteria: YearReviewCriteria): YearReviewEligibilityDto {
        if (!criteria.enabled) {
            return YearReviewEligibilityDto(
                eligible = false,
                criteriaMet = 0,
                criteriaRequired = criteria.minCriteriaMet,
                reasons = emptyList(),
                progress = emptyList(),
                remainingHint = null,
            )
        }
        val progress = listOf(
            YearReviewCriterionProgressDto(
                id = "top_rank",
                met = stats.currentRank != null && stats.currentRank <= criteria.topRankMax,
                current = stats.currentRank ?: 0,
                required = criteria.topRankMax,
            ),
            YearReviewCriterionProgressDto(
                id = "accepted_reports",
                met = stats.acceptedReports >= criteria.minAcceptedReports,
                current = stats.acceptedReports,
                required = criteria.minAcceptedReports,
            ),
            YearReviewCriterionProgressDto(
                id = "earned_points",
                met = stats.earnedPoints >= criteria.minEarnedPoints,
                current = stats.earnedPoints.toInt().coerceAtMost(Int.MAX_VALUE),
                required = criteria.minEarnedPoints,
            ),
            YearReviewCriterionProgressDto(
                id = "active_weeks",
                met = stats.activeWeeks >= criteria.minActiveWeeks,
                current = stats.activeWeeks,
                required = criteria.minActiveWeeks,
            ),
            YearReviewCriterionProgressDto(
                id = "badges",
                met = stats.badgesEarned >= criteria.minBadges,
                current = stats.badgesEarned,
                required = criteria.minBadges,
            ),
        )
        val met = progress.count { it.met }
        val reasons = progress.filter { it.met }.map {
            when (it.id) {
                "top_rank" -> "Топ-${criteria.topRankMax} рейтинга"
                "accepted_reports" -> "${stats.acceptedReports} принятых отчёта"
                "earned_points" -> "${stats.earnedPoints} баллов за год"
                "active_weeks" -> "${stats.activeWeeks} активных недель"
                "badges" -> "${stats.badgesEarned} значков"
                else -> it.id
            }
        }
        val eligible = met >= criteria.minCriteriaMet
        val remainingHint = if (eligible) {
            null
        } else {
            val need = criteria.minCriteriaMet - met
            val nearest = progress.filter { !it.met }.minByOrNull { (it.required - it.current).coerceAtLeast(0) }
            when (nearest?.id) {
                "earned_points" -> "До захвалницы осталось ${(nearest.required - nearest.current).coerceAtLeast(0)} баллов"
                "accepted_reports" -> "До захвалницы осталось ${(nearest.required - nearest.current).coerceAtLeast(0)} принятый отчёт"
                "active_weeks" -> "До захвалницы осталось ${(nearest.required - nearest.current).coerceAtLeast(0)} активных недель"
                "badges" -> "До захвалницы осталось ${(nearest.required - nearest.current).coerceAtLeast(0)} значков"
                "top_rank" -> "Нужно войти в топ-${criteria.topRankMax} рейтинга"
                else -> "Выполните ещё $need критерия из ${criteria.minCriteriaMet}"
            }
        }
        return YearReviewEligibilityDto(
            eligible = eligible,
            criteriaMet = met,
            criteriaRequired = criteria.minCriteriaMet,
            reasons = reasons,
            progress = progress,
            remainingHint = remainingHint,
        )
    }

    private fun issueInternal(
        username: String,
        year: Int,
        source: AnnualCertificateSource,
        review: YearReviewDto,
    ): AnnualCertificateDto {
        val existing = certificateRepository.findByUsernameIgnoreCaseAndYearAndTemplateVersion(
            username,
            year,
            AnnualCertificate.TEMPLATE_ANNUAL_SUPER,
        ).orElse(null)
        if (existing != null) {
            if (existing.status == AnnualCertificateStatus.REVOKED && source == AnnualCertificateSource.MANUAL) {
                existing.status = AnnualCertificateStatus.ISSUED
                existing.issueSource = source
                existing.revokedAt = null
                existing.revokedBy = null
                existing.revokeReason = null
                existing.issuedAt = LocalDateTime.now()
                existing.updatedAt = LocalDateTime.now()
                existing.qualificationReasons = objectMapper.writeValueAsString(review.eligibility.reasons)
                existing.statsSnapshot = objectMapper.writeValueAsString(review.stats)
                return toDto(certificateRepository.save(existing))
            }
            return toDto(existing)
        }
        val number = nextCertificateNumber(year)
        val account = accountRepository.findAllByUsernameLowerIn(listOf(username.lowercase())).firstOrNull()
        val cert = AnnualCertificate(
            username = account?.username ?: username,
            year = year,
            certificateNumber = number,
            verificationToken = UUID.randomUUID(),
            status = AnnualCertificateStatus.ISSUED,
            issueSource = source,
            qualificationReasons = objectMapper.writeValueAsString(review.eligibility.reasons),
            statsSnapshot = objectMapper.writeValueAsString(review.stats),
            templateVersion = AnnualCertificate.TEMPLATE_ANNUAL_SUPER,
            recipientFullName = review.stats.displayName,
            issuedAt = LocalDateTime.now(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        return toDto(certificateRepository.save(cert))
    }

    private fun nextCertificateNumber(year: Int): String {
        val prefix = "RDS-RADIMO-$year-"
        val seq = certificateRepository.countByYearAndCertificateNumberStartingWith(year, prefix) + 1
        return prefix + seq.toString().padStart(6, '0')
    }

    private fun criteriaFor(year: Int): YearReviewCriteria =
        criteriaRepository.findByYear(year).orElseGet {
            criteriaRepository.save(
                YearReviewCriteria(
                    year = year,
                    updatedAt = LocalDateTime.now(),
                )
            )
        }

    private fun longestIsoWeekStreak(weeks: List<String>): Int {
        if (weeks.isEmpty()) return 0
        // Expect refs like 2026-W12
        data class W(val year: Int, val week: Int)
        val parsed = weeks.mapNotNull { ref ->
            val m = WEEK_REF.matchEntire(ref) ?: return@mapNotNull null
            W(m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }.sortedWith(compareBy({ it.year }, { it.week }))
        if (parsed.isEmpty()) return weeks.size.coerceAtMost(1)
        var best = 1
        var cur = 1
        for (i in 1 until parsed.size) {
            val prev = parsed[i - 1]
            val next = parsed[i]
            val sequential =
                (next.year == prev.year && next.week == prev.week + 1) ||
                    (next.year == prev.year + 1 && prev.week >= 52 && next.week == 1)
            if (sequential) {
                cur += 1
                best = maxOf(best, cur)
            } else {
                cur = 1
            }
        }
        return best
    }

    private fun toDto(cert: AnnualCertificate): AnnualCertificateDto {
        val reasons = runCatching {
            objectMapper.readValue(cert.qualificationReasons ?: "[]", Array<String>::class.java).toList()
        }.getOrDefault(emptyList())
        return AnnualCertificateDto(
            id = cert.id!!.toString(),
            year = cert.year,
            certificateNumber = cert.certificateNumber,
            verificationToken = cert.verificationToken.toString(),
            status = cert.status.name,
            issueSource = cert.issueSource.name,
            qualificationReasons = reasons,
            recipientFullName = cert.recipientFullName,
            templateVersion = cert.templateVersion,
            issuedAt = cert.issuedAt.toString(),
            pdfFileId = cert.pdfFileId?.toString(),
        )
    }

    private fun toCriteriaDto(c: YearReviewCriteria) = YearReviewCriteriaDto(
        year = c.year,
        topRankMax = c.topRankMax,
        minAcceptedReports = c.minAcceptedReports,
        minEarnedPoints = c.minEarnedPoints,
        minActiveWeeks = c.minActiveWeeks,
        minBadges = c.minBadges,
        minCriteriaMet = c.minCriteriaMet,
        enabled = c.enabled,
    )

    private fun isManager(): Boolean {
        val login = currentUserLogin()?.lowercase()
        val accountRoles = login
            ?.let { accountRepository.findAllByUsernameLowerIn(listOf(it)).firstOrNull()?.groups }
            .orEmpty()
        val roles = currentUserRoles().orEmpty() + accountRoles
        return roles.any {
            it == UserGroup.ADMIN || it == UserGroup.ADMIN_SSO ||
                it == UserGroup.ADMIN_VOLUNTEER || it == UserGroup.MAIN_VOLUNTEER
        }
    }

    private fun requireManager() {
        if (!isManager()) throw NotAuthorizedException()
    }

    companion object {
        private val WEEK_REF = Regex("""^(\d{4})-W(\d{1,2})$""")
        private const val ORGANIZATION = "Udruženje „Ruska dijaspora u Srbiji“"
    }
}
