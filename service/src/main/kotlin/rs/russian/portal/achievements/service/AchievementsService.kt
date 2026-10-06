package rs.russian.portal.achievements.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.achievements.api.AchievementDto
import rs.russian.portal.achievements.api.AchievementsLeaderboardDto
import rs.russian.portal.achievements.api.AchievementsMeDto
import rs.russian.portal.achievements.api.InboxDeliveryStatsDto
import rs.russian.portal.achievements.api.PointEventDto
import rs.russian.portal.achievements.api.PointLeaderDto
import rs.russian.portal.achievements.domain.VolunteerPointEvent
import rs.russian.portal.achievements.repository.VolunteerPointEventRepository
import rs.russian.portal.activity.repository.ActivityEventRepository
import rs.russian.portal.inbox.repository.InboxThreadRepository
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.report.repository.ReportRepository
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.shared.security.currentUserRoles
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.AccountRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

@Service
class AchievementsService(
    private val pointEvents: VolunteerPointEventRepository,
    private val activityEvents: ActivityEventRepository,
    private val accountRepository: AccountRepository,
    private val reportRepository: ReportRepository,
    private val inboxThreadRepository: InboxThreadRepository,
) {

    @Transactional
    fun me(): AchievementsMeDto {
        val login = currentUserLogin() ?: throw NotAuthorizedException()
        syncWeeklyPresence(login)
        val balance = pointEvents.sumPoints(login)
        val recent = pointEvents.findByUsernameOrderByCreatedAtDesc(login).take(30).map {
            PointEventDto(
                code = it.code,
                points = it.points,
                title = it.title,
                refId = it.refId,
                createdAt = it.createdAt.toString(),
            )
        }
        val weeklyLogins = pointEvents.countByUsernameAndCode(login, CODE_WEEKLY_LOGIN)
        val cleanReports = pointEvents.countByUsernameAndCode(login, CODE_REPORT_CLEAN)
        val curatorThanks = pointEvents.countByUsernameAndCode(login, CODE_CURATOR_GRATITUDE)
        val managerThanks = pointEvents.countByUsernameAndCode(login, CODE_MANAGER_GRATITUDE)
        val account = accountRepository.findByUsername(login).orElse(null)
        val hasAvatar = account?.info?.avatar != null
        val acceptedCount =
            reportRepository.countByAccountUsernameIgnoreCaseAndStatus(login, ReportStatus.ACCEPTED)
        val achievements = AchievementCatalog.ALL.map { def ->
            val progress = when (def.kind) {
                AchievementCatalog.Kind.WEEKLY_LOGINS -> weeklyLogins.toInt()
                AchievementCatalog.Kind.CLEAN_REPORTS -> cleanReports.toInt()
                AchievementCatalog.Kind.CURATOR_GRATITUDE -> curatorThanks.toInt()
                AchievementCatalog.Kind.MANAGER_GRATITUDE -> managerThanks.toInt()
                AchievementCatalog.Kind.HAS_AVATAR -> if (hasAvatar) 1 else 0
                AchievementCatalog.Kind.ANY_ACCEPTED_REPORT -> acceptedCount.toInt().coerceAtMost(1)
                AchievementCatalog.Kind.POSITIVE_BALANCE -> if (balance > 0) 1 else 0
            }
            AchievementDto(
                id = def.id,
                category = def.category,
                title = def.title,
                description = def.description,
                points = def.points,
                unlocked = progress >= def.target,
                progress = progress,
                target = def.target,
            )
        }
        val week = currentIsoWeek()
        val thisWeekVisited = activityEvents.existsByUsernameIgnoreCaseAndCreateTimeGreaterThanEqual(
            login,
            weekStart(week),
        )
        return AchievementsMeDto(
            balance = balance,
            unlockedCount = achievements.count { it.unlocked },
            totalCount = achievements.size,
            achievements = achievements,
            recent = recent,
            inbox = inboxDeliveryStats(login),
            thisWeekVisited = thisWeekVisited,
        )
    }

    /**
     * +10 once per ISO week when the volunteer was active this week;
     * −10 for the previous week if they never visited (soft penalty, not a hard lock).
     */
    @Transactional
    fun syncWeeklyPresence(username: String) {
        val thisWeek = currentIsoWeek()
        val thisStart = weekStart(thisWeek)
        if (activityEvents.existsByUsernameIgnoreCaseAndCreateTimeGreaterThanEqual(username, thisStart)) {
            award(
                username = username,
                code = CODE_WEEKLY_LOGIN,
                points = 10,
                refId = thisWeek,
                title = "Визит на портал за неделю $thisWeek",
            )
        }
        val prevWeek = previousIsoWeek(thisWeek)
        val prevStart = weekStart(prevWeek)
        val prevEnd = thisStart.minusSeconds(1)
        // New volunteers: no −10 until they had at least one visit in some earlier week.
        if (!hadActivityBefore(username, prevStart)) return
        val visitedPrev = activityEvents.existsByUsernameIgnoreCaseAndCreateTimeBetween(
            username,
            prevStart,
            prevEnd,
        )
        if (!visitedPrev) {
            award(
                username = username,
                code = CODE_WEEKLY_MISS,
                points = -10,
                refId = prevWeek,
                title = "Нет визита за неделю $prevWeek",
            )
        }
    }

    @Transactional
    fun onReportAcceptedClean(username: String, reportId: UUID) {
        award(
            username = username,
            code = CODE_REPORT_CLEAN,
            points = 5,
            refId = reportId.toString(),
            title = "Отчёт принят без замечаний",
        )
    }

    @Transactional
    fun onCuratorGratitude(username: String, reportId: UUID, awardedBy: String) {
        award(
            username = username,
            code = CODE_CURATOR_GRATITUDE,
            points = 15,
            refId = reportId.toString(),
            title = "Благодарность куратора ($awardedBy)",
        )
    }

    @Transactional
    fun onManagerGratitude(username: String, reportId: UUID, awardedBy: String) {
        award(
            username = username,
            code = CODE_MANAGER_GRATITUDE,
            points = 40,
            refId = reportId.toString(),
            title = "Благодарность руководителя ($awardedBy)",
        )
    }

    data class MissionClaimResult(val balance: Long, val alreadyClaimed: Boolean)

    @Transactional(readOnly = true)
    fun hasMissionClaim(username: String, missionId: UUID): Boolean =
        pointEvents.existsByUsernameAndCodeAndRefId(username, CODE_MISSION_CLAIM, missionId.toString())

    @Transactional(readOnly = true)
    fun balanceOf(username: String): Long = pointEvents.sumPoints(username)

    @Transactional(readOnly = true)
    fun leaderboard(limit: Int = 100): AchievementsLeaderboardDto {
        val cap = limit.coerceIn(1, 200)
        val grouped = pointEvents.sumPointsGrouped()
        val login = currentUserLogin()?.lowercase()
        val viewer = login?.let { accountRepository.findAllByUsernameLowerIn(listOf(it)).firstOrNull() }
        val jwtRoles = currentUserRoles().orEmpty()
        val accountRoles = viewer?.groups.orEmpty()
        val roles = jwtRoles + accountRoles
        val isManager = roles.any {
            it == UserGroup.ADMIN || it == UserGroup.ADMIN_SSO ||
                it == UserGroup.ADMIN_VOLUNTEER || it == UserGroup.MAIN_VOLUNTEER
        }

        val rawTotals = grouped.mapNotNull { row ->
            val username = (row[0] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val points = when (val raw = row[1]) {
                is Number -> raw.toLong()
                else -> 0L
            }
            username to points
        }
        val lookupNames = rawTotals.map { it.first }.toMutableSet()
        if (login != null) lookupNames.add(login)
        val accountMap = accountRepository.findAllByUsernameLowerIn(lookupNames)
            .associateBy { it.username.lowercase() }

        // Staff / test accounts stay out of the public ranking (GDPR + fair play).
        val totals = rawTotals.filter { (username, _) ->
            val account = accountMap[username]
            !isExcludedFromLeaderboard(username, account?.email, account?.fullName)
        }

        val viewerExcluded = login != null && isExcludedFromLeaderboard(
            login,
            viewer?.email,
            viewer?.fullName,
        )
        // Managers and excluded staff (e.g. portal owners) always see the full table.
        val canSeeFull = isManager || viewerExcluded

        val visibleCap = if (canSeeFull) cap else PUBLIC_PODIUM_SIZE
        val leaders = totals.take(visibleCap).mapIndexed { index, (username, points) ->
            val account = accountMap[username]
            PointLeaderDto(
                rank = index + 1,
                username = account?.username ?: username,
                fullName = account?.fullName?.takeIf { it.isNotBlank() } ?: (account?.username ?: username),
                points = points,
                isMe = login != null && username == login && !viewerExcluded,
            )
        }

        val meRank = if (viewerExcluded) -1 else login?.let { me -> totals.indexOfFirst { it.first == me } } ?: -1
        val me = when {
            viewerExcluded || login == null -> null
            meRank >= 0 -> {
                val (username, points) = totals[meRank]
                val account = accountMap[username]
                PointLeaderDto(
                    rank = meRank + 1,
                    username = account?.username ?: username,
                    fullName = account?.fullName?.takeIf { it.isNotBlank() } ?: (account?.username ?: username),
                    points = points,
                    isMe = true,
                )
            }
            else -> {
                val account = accountMap[login]
                PointLeaderDto(
                    rank = totals.size + 1,
                    username = account?.username ?: login,
                    fullName = account?.fullName?.takeIf { it.isNotBlank() } ?: (account?.username ?: login),
                    points = 0,
                    isMe = true,
                )
            }
        }
        return AchievementsLeaderboardDto(
            leaders = leaders,
            me = me,
            totalParticipants = totals.size,
            fullList = canSeeFull,
        )
    }

    private fun isExcludedFromLeaderboard(username: String?, email: String?, fullName: String?): Boolean {
        val u = username?.trim()?.lowercase().orEmpty()
        val e = email?.trim()?.lowercase().orEmpty()
        val name = fullName?.trim()?.lowercase()?.replace(Regex("\\s+"), " ").orEmpty()
        if (u in LEADERBOARD_EXCLUDED_USERNAMES || e in LEADERBOARD_EXCLUDED_EMAILS) return true
        if (e.startsWith("legkov777@")) return true
        return LEADERBOARD_EXCLUDED_NAME_MARKERS.any { marker -> name.contains(marker) }
    }

    /**
     * External / service-account award (Ekomapa podium, etc). Idempotent on (user, code, refId).
     */
    @Transactional
    fun awardExternal(username: String, code: String, points: Int, refId: String, title: String): Pair<Long, Boolean> {
        val login = username.trim()
        if (login.isEmpty() || points == 0 || code.isBlank() || refId.isBlank()) {
            return 0L to false
        }
        val canonical = accountRepository.findAllByUsernameLowerIn(listOf(login.lowercase()))
            .firstOrNull()?.username ?: login.lowercase()
        val already = pointEvents.existsByUsernameAndCodeAndRefId(canonical, code, refId) ||
            pointEvents.existsByUsernameAndCodeAndRefId(login.lowercase(), code, refId)
        if (already) {
            return pointEvents.sumPoints(canonical) to false
        }
        award(
            username = canonical,
            code = code,
            points = points,
            refId = refId,
            title = title,
        )
        return pointEvents.sumPoints(canonical) to true
    }

    @Transactional(readOnly = true)
    fun listMissionClaimEvents(): List<VolunteerPointEvent> =
        pointEvents.findTop200ByCodeOrderByCreatedAtDesc(CODE_MISSION_CLAIM)

    @Transactional
    fun claimMission(
        username: String,
        missionId: UUID,
        points: Int,
        title: String,
        oneTime: Boolean,
    ): MissionClaimResult {
        val ref = missionId.toString()
        if (oneTime && pointEvents.existsByUsernameAndCodeAndRefId(username, CODE_MISSION_CLAIM, ref)) {
            return MissionClaimResult(balance = pointEvents.sumPoints(username), alreadyClaimed = true)
        }
        val refKey = if (oneTime) ref else "${ref}-${System.currentTimeMillis()}"
        award(
            username = username,
            code = CODE_MISSION_CLAIM,
            points = points,
            refId = refKey,
            title = "Миссия: $title",
        )
        return MissionClaimResult(balance = pointEvents.sumPoints(username), alreadyClaimed = false)
    }

    @Transactional
    fun settlePreviousWeekForAllActive() {
        val prev = previousIsoWeek(currentIsoWeek())
        val prevStart = weekStart(prev)
        val prevEnd = weekStart(currentIsoWeek()).minusSeconds(1)
        accountRepository.findAll().asSequence()
            .filter { it.active }
            .forEach { account ->
                val login = account.username
                val visited = activityEvents.existsByUsernameIgnoreCaseAndCreateTimeBetween(
                    login,
                    prevStart,
                    prevEnd,
                )
                if (visited) {
                    award(
                        username = login,
                        code = CODE_WEEKLY_LOGIN,
                        points = 10,
                        refId = prev,
                        title = "Визит на портал за неделю $prev",
                    )
                } else if (hadActivityBefore(login, prevStart)) {
                    award(
                        username = login,
                        code = CODE_WEEKLY_MISS,
                        points = -10,
                        refId = prev,
                        title = "Нет визита за неделю $prev",
                    )
                }
            }
    }

    private fun hadActivityBefore(username: String, before: LocalDateTime): Boolean =
        activityEvents.existsByUsernameIgnoreCaseAndCreateTimeBetween(
            username,
            LocalDateTime.of(2015, 1, 1, 0, 0),
            before.minusSeconds(1),
        )

    private fun award(username: String, code: String, points: Int, refId: String, title: String) {
        val key = refId.ifBlank { "-" }
        if (pointEvents.existsByUsernameAndCodeAndRefId(username, code, key)) return
        try {
            pointEvents.save(
                VolunteerPointEvent(
                    username = username,
                    code = code,
                    points = points,
                    refId = key,
                    title = title,
                )
            )
        } catch (ex: Exception) {
            log.debug("Skip duplicate point award {} {} {}", username, code, key, ex)
        }
    }

    private fun inboxDeliveryStats(username: String): InboxDeliveryStatsDto {
        return try {
            val sent = inboxThreadRepository.countManualSentBy(username)
            val delivered = inboxThreadRepository.countManualDeliveredBy(username)
            InboxDeliveryStatsDto(
                sent = sent,
                delivered = delivered,
                pending = (sent - delivered).coerceAtLeast(0),
            )
        } catch (ex: Exception) {
            log.debug("Inbox delivery stats unavailable", ex)
            InboxDeliveryStatsDto(0, 0, 0)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AchievementsService::class.java)
        const val CODE_WEEKLY_LOGIN = "WEEKLY_LOGIN"
        const val CODE_WEEKLY_MISS = "WEEKLY_MISS"
        const val CODE_REPORT_CLEAN = "REPORT_CLEAN"
        const val CODE_CURATOR_GRATITUDE = "CURATOR_GRATITUDE"
        const val CODE_MANAGER_GRATITUDE = "MANAGER_GRATITUDE"
        const val CODE_MISSION_CLAIM = "MISSION_CLAIM"
        const val CODE_EKOMAPA_PODIUM = "EKOMAPA_MONTHLY_PODIUM"
        const val PUBLIC_PODIUM_SIZE = 3

        /** Portal points for Ekomapa monthly cleanup podium: 1st / 2nd / 3rd. */
        val EKOMAPA_PODIUM_POINTS: Map<Int, Int> = mapOf(1 to 30, 2 to 20, 3 to 10)

        private val LEADERBOARD_EXCLUDED_USERNAMES = setOf(
            "legkov777",
            "leonid.stetsenko",
            "leonid_stetsenko",
            "lstetsenko",
        )
        private val LEADERBOARD_EXCLUDED_EMAILS = setOf("legkov777@gmail.com")
        private val LEADERBOARD_EXCLUDED_NAME_MARKERS = setOf(
            "stetsenko",
            "стеценко",
            "legkov777",
        )

        private val weekFields = WeekFields.of(Locale("ru", "RU"))

        fun currentIsoWeek(today: LocalDate = LocalDate.now()): String {
            val week = today.get(weekFields.weekOfWeekBasedYear())
            val year = today.get(weekFields.weekBasedYear())
            return "%d-W%02d".format(year, week)
        }

        fun previousIsoWeek(weekKey: String): String {
            val parts = weekKey.split("-W")
            val year = parts[0].toInt()
            val week = parts[1].toInt()
            val date = LocalDate.of(year, 6, 1)
                .with(weekFields.weekBasedYear(), year.toLong())
                .with(weekFields.weekOfWeekBasedYear(), week.toLong())
                .with(DayOfWeek.MONDAY)
                .minusWeeks(1)
            return currentIsoWeek(date)
        }

        fun weekStart(weekKey: String): LocalDateTime {
            val parts = weekKey.split("-W")
            val year = parts[0].toInt()
            val week = parts[1].toInt()
            return LocalDate.of(year, 6, 1)
                .with(weekFields.weekBasedYear(), year.toLong())
                .with(weekFields.weekOfWeekBasedYear(), week.toLong())
                .with(DayOfWeek.MONDAY)
                .atStartOfDay()
        }
    }
}
