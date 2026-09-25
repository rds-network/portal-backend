package rs.russian.portal.inbox.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.inbox.api.OverdueNoticePersonDto
import rs.russian.portal.inbox.api.OverdueNotifyResultDto
import rs.russian.portal.inbox.api.OverduePreviewDto
import rs.russian.portal.inbox.api.OverdueTemplateDto
import rs.russian.portal.inbox.api.ReportOverdueDto
import rs.russian.portal.inbox.domain.ReportOverdueNotice
import rs.russian.portal.inbox.repository.ReportOverdueJdbc
import rs.russian.portal.inbox.repository.ReportOverdueNoticeRepository
import rs.russian.portal.mup.service.MupLetterService
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Service
class ReportOverdueService(
    private val reportOverdueJdbc: ReportOverdueJdbc,
    private val reportOverdueNoticeRepository: ReportOverdueNoticeRepository,
    private val inboxService: InboxService,
    private val mupLetterService: MupLetterService,
    private val accountService: AccountService,
) {

    @Transactional(readOnly = true)
    fun list(): List<ReportOverdueDto> {
        val counts = warningCountsMap()
        return reportOverdueJdbc.findOverdue().map { item ->
            val count = counts[item.username] ?: 0
            withText(item.copy(warningCount = count, notified = count >= 1, watchlist = count >= 2))
        }
    }

    @Transactional(readOnly = true)
    fun preview(): OverduePreviewDto {
        val items = list()
        return OverduePreviewDto(
            count = items.size,
            templates = listOf(
                OverdueTemplateDto("HOURS", SUBJECT_HOURS, TEMPLATE_HOURS),
                OverdueTemplateDto("WEEK_1", SUBJECT_1, TEMPLATE_1),
                OverdueTemplateDto("WEEK_2", SUBJECT_2, TEMPLATE_2),
                OverdueTemplateDto("WEEK_3", SUBJECT_3, TEMPLATE_3),
            ),
            samples = items.take(8),
        )
    }

    @Transactional(readOnly = true)
    fun warningCount(username: String): Int {
        val current = accountService.getCurrentAccount()
        val managers = setOf(UserGroup.ADMIN, UserGroup.ADMIN_VOLUNTEER, UserGroup.MAIN_VOLUNTEER)
        if (current.username != username && current.groups.none { it in managers }) {
            throw NotAuthorizedException()
        }
        return reportOverdueNoticeRepository.countByUsernameAndLevelLessThan(username, MUP_LEVEL).toInt()
    }

    @Transactional(readOnly = true)
    fun warningCounts(usernames: Collection<String> = emptyList()): Map<String, Int> {
        currentUserLogin() ?: throw NotAuthorizedException()
        val all = warningCountsMap()
        if (usernames.isEmpty()) return all
        val wanted = usernames.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return all.filterKeys { it in wanted }
    }

    private fun warningCountsMap(): Map<String, Int> =
        reportOverdueNoticeRepository.countGrouped().associate { it.username to it.cnt.toInt() }

    private fun withText(item: ReportOverdueDto) = item.copy(
        subject = subjectFor(item),
        body = bodyFor(item, item.warningCount + 1),
    )

    @Transactional(readOnly = true)
    fun noticeLedger(): List<OverdueNoticePersonDto> {
        val notices = reportOverdueNoticeRepository.findAllByOrderBySentAtDesc()
        if (notices.isEmpty()) return emptyList()
        val accounts = accountService.resolve(notices.map { it.username }.distinct())
            .associateBy { it.username.lowercase() }
        return notices.groupBy { it.username.lowercase() }.map { (key, items) ->
            val account = accounts[key]
            val warnings = items.count { it.level < MUP_LEVEL }
            OverdueNoticePersonDto(
                username = account?.username ?: items.first().username,
                fullName = account?.fullName ?: items.first().username,
                program = account?.info?.program?.code,
                warningCount = warnings,
                lastSentAt = items.maxOf { it.sentAt },
                notified = warnings >= 1,
                watchlist = warnings >= 2,
                mupSent = items.any { it.level >= MUP_LEVEL },
            )
        }.sortedWith(compareByDescending<OverdueNoticePersonDto> { it.watchlist }.thenByDescending { it.warningCount })
    }

    @Transactional
    fun notifyDue(exclude: Collection<String> = emptyList()): OverdueNotifyResultDto {
        val skip = exclude.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val weekKey = LocalDate.now().with(java.time.DayOfWeek.MONDAY).format(WEEK_KEY)
        val recipients = mutableListOf<OverdueNoticePersonDto>()
        for (item in list()) {
            if (item.username.lowercase() in skip) continue
            val level = noticeLevel(item)
            val periodKey = if (level == 0) HOURS_SNAPSHOT_KEY else weekKey
            val already = reportOverdueNoticeRepository.existsByUsernameAndLevelAndPeriodKey(
                item.username,
                level,
                periodKey,
            )
            if (already) continue
            val nextCount = item.warningCount + 1
            inboxService.notifyOverdue(item.username, level, subjectFor(item), bodyFor(item, nextCount))
            reportOverdueNoticeRepository.save(
                ReportOverdueNotice(
                    username = item.username,
                    level = level,
                    periodKey = periodKey,
                )
            )
            val mup = nextCount >= 3 && !alreadySentMup(item.username)
            if (mup) {
                sendMup(item.username)
            }
            recipients += OverdueNoticePersonDto(
                username = item.username,
                fullName = item.fullName,
                program = item.program,
                warningCount = nextCount,
                lastSentAt = OffsetDateTime.now(),
                notified = true,
                watchlist = nextCount >= 2,
                mupSent = mup || alreadySentMup(item.username),
            )
        }
        log.info("[SCHEDULER] Overdue report notices sent: {}", recipients.size)
        return OverdueNotifyResultDto(sent = recipients.size, recipients = recipients)
    }

    private fun alreadySentMup(username: String): Boolean =
        reportOverdueNoticeRepository.existsByUsernameAndLevelAndPeriodKey(username, MUP_LEVEL, MUP_PERIOD)

    private fun sendMup(username: String) {
        try {
            mupLetterService.sendForVolunteer(username)
            reportOverdueNoticeRepository.save(
                ReportOverdueNotice(
                    username = username,
                    level = MUP_LEVEL,
                    periodKey = MUP_PERIOD,
                )
            )
            log.info("Automatic MUP letter sent for {}", username)
        } catch (ex: Exception) {
            log.error("Failed to send automatic MUP letter for {}", username, ex)
        }
    }

    private fun noticeLevel(item: ReportOverdueDto): Int =
        when {
            item.weeksMissed >= 3 -> 3
            item.weeksMissed >= 2 -> 2
            item.weeksMissed >= 1 -> 1
            else -> 0
        }

    private fun subjectFor(item: ReportOverdueDto) =
        when (noticeLevel(item)) {
            3 -> SUBJECT_3
            2 -> SUBJECT_2
            1 -> SUBJECT_1
            else -> SUBJECT_HOURS
        }

    private fun bodyFor(item: ReportOverdueDto, warningNumber: Int): String {
        val last = item.lastReportWeek?.format(DATE) ?: "нет принятых отчётов"
        val hours = item.hoursShort
        val counter = "Это предупреждение $warningNumber из 3. " +
            if (warningNumber >= 3) {
                "При третьем уведомлении информация о расторжении договора уходит в МУП автоматически."
            } else {
                "При 3 уведомлениях информация о расторжении договора уходит в МУП автоматически."
            }
        val base = when (noticeLevel(item)) {
            3 ->
                "Здравствуйте, ${item.fullName}.\n\n" +
                    "Вы не сдавали отчёт 3 недели подряд. Если отчёт не будет сдан, аккаунт будет заблокирован, а договор расторгнут.\n\n" +
                    "Недосдача часов: $hours. Последняя принятая неделя: $last."
            2 ->
                "Здравствуйте, ${item.fullName}.\n\n" +
                    "Вы не сдавали отчёт 2 недели подряд. Просим закрыть отчётность.\n\n" +
                    "Недосдача часов: $hours. Последняя принятая неделя: $last."
            1 ->
                "Здравствуйте, ${item.fullName}.\n\n" +
                    "За прошлую неделю нет принятого отчёта (+1 неделя). Просим сдать отчёт в личном кабинете.\n\n" +
                    "Недосдача часов: $hours. Последняя принятая неделя: $last."
            else ->
                "Здравствуйте, ${item.fullName}.\n\n" +
                    "По норме за период у вас недосдача $hours ч. Просим закрыть отчётность и нагнать часы.\n\n" +
                    "Последняя принятая неделя: $last."
        }
        return "$base\n\n$counter"
    }

    companion object {
        private val log = LoggerFactory.getLogger(ReportOverdueService::class.java)
        private val WEEK_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("YYYY-'W'ww")
        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        const val HOURS_SNAPSHOT_KEY = "HOURS-SNAPSHOT"
        const val MUP_LEVEL = 99
        const val MUP_PERIOD = "MUP"
        const val SUBJECT_HOURS = "Недосдача часов: текущий срез"
        const val SUBJECT_1 = "Напоминание: не сдан отчёт за прошлую неделю"
        const val SUBJECT_2 = "Напоминание: не сдан отчёт 2 недели"
        const val SUBJECT_3 = "Предупреждение: блокировка аккаунта и расторжение договора"
        const val TEMPLATE_HOURS =
            "Здравствуйте, {имя}.\n\nПо срезу за последние 5 недель у вас недосдача больше 20 часов.\n\nЭто предупреждение N из 3. При 3 уведомлениях информация о расторжении уходит в МУП автоматически."
        const val TEMPLATE_1 =
            "Здравствуйте, {имя}.\n\nЗа прошлую неделю нет принятого отчёта (+1 неделя).\n\nЭто предупреждение N из 3. При 3 уведомлениях информация о расторжении уходит в МУП автоматически."
        const val TEMPLATE_2 =
            "Здравствуйте, {имя}.\n\nВы не сдавали отчёт 2 недели подряд.\n\nЭто предупреждение N из 3. При 3 уведомлениях информация о расторжении уходит в МУП автоматически."
        const val TEMPLATE_3 =
            "Здравствуйте, {имя}.\n\nВы не сдавали отчёт 3 недели подряд. Если отчёт не будет сдан, аккаунт будет заблокирован, а договор расторгнут.\n\nЭто предупреждение N из 3. При третьем уведомлении информация о расторжении уходит в МУП автоматически."
    }
}
