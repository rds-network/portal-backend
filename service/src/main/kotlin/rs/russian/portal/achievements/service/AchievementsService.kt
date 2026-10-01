package rs.russian.portal.achievements.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.achievements.api.AchievementDto
import rs.russian.portal.achievements.api.AchievementsMeDto
import rs.russian.portal.achievements.api.InboxDeliveryStatsDto
import rs.russian.portal.achievements.api.PointEventDto
import rs.russian.portal.achievements.domain.VolunteerPointEvent
import rs.russian.portal.achievements.repository.VolunteerPointEventRepository
import rs.russian.portal.activity.repository.ActivityEventRepository
import rs.russian.portal.inbox.repository.InboxThreadRepository
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.report.repository.ReportRepository
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin
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
        val account = accountRepository.findByUsername(login).orElse(null)
        val hasAvatar = account?.info?.avatar != null
        val acceptedCount =
            reportRepository.countByAccountUsernameIgnoreCaseAndStatus(login, ReportStatus.ACCEPTED)
        val achievements = AchievementCatalog.ALL.map { def ->
            val progress = when (def.kind) {
                AchievementCatalog.Kind.WEEKLY_LOGINS -> weeklyLogins.toInt()
                AchievementCatalog.Kind.CLEAN_REPORTS -> cleanReports.toInt()
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
