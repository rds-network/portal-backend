package rs.russian.portal.inbox.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.inbox.api.OverduePreviewDto
import rs.russian.portal.inbox.api.OverdueTemplateDto
import rs.russian.portal.inbox.api.ReportOverdueDto
import rs.russian.portal.inbox.domain.ReportOverdueNotice
import rs.russian.portal.inbox.repository.ReportOverdueJdbc
import rs.russian.portal.inbox.repository.ReportOverdueNoticeRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class ReportOverdueService(
    private val reportOverdueJdbc: ReportOverdueJdbc,
    private val reportOverdueNoticeRepository: ReportOverdueNoticeRepository,
    private val inboxService: InboxService,
) {

    @Transactional(readOnly = true)
    fun list(): List<ReportOverdueDto> = reportOverdueJdbc.findOverdue().map { withText(it) }

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

    private fun withText(item: ReportOverdueDto) = item.copy(
        subject = subjectFor(item),
        body = bodyFor(item),
    )

    @Transactional
    fun notifyDue(): Int {
        val weekKey = LocalDate.now().with(java.time.DayOfWeek.MONDAY).format(WEEK_KEY)
        var sent = 0
        for (item in list()) {
            val level = noticeLevel(item)
            val periodKey = if (level == 0) HOURS_SNAPSHOT_KEY else weekKey
            val already = reportOverdueNoticeRepository.existsByUsernameAndLevelAndPeriodKey(
                item.username,
                level,
                periodKey,
            )
            if (already) continue
            inboxService.notifyOverdue(item.username, level, subjectFor(item), bodyFor(item))
            reportOverdueNoticeRepository.save(
                ReportOverdueNotice(
                    username = item.username,
                    level = level,
                    periodKey = periodKey,
                )
            )
            sent += 1
        }
        log.info("[SCHEDULER] Overdue report notices sent: {}", sent)
        return sent
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

    private fun bodyFor(item: ReportOverdueDto): String {
        val last = item.lastReportWeek?.format(DATE) ?: "нет принятых отчётов"
        val hours = item.hoursShort
        return when (noticeLevel(item)) {
            3 ->
                "Здравствуйте, ${item.fullName}.\n\n" +
                    "Вы не сдавали отчёт 3 недели подряд. Если отчёт не будет сдан, аккаунт будет заблокирован, а договор расторгнут.\n\n" +
                    "Недосдача часов: $hours. Последняя принятая неделя: $last.\n\n" +
                    "Пожалуйста, заполните отчётность в личном кабинете и ответьте на это сообщение, если нужна помощь."
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
                    "По текущему срезу у вас недосдача больше 20 часов ($hours ч). Просим закрыть отчётность и нагнать часы.\n\n" +
                    "Дальше учитываются пропущенные недели: +1, +2, затем предупреждение о блокировке.\n\n" +
                    "Последняя принятая неделя: $last."
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ReportOverdueService::class.java)
        private val WEEK_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("YYYY-'W'ww")
        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        const val HOURS_SNAPSHOT_KEY = "HOURS-SNAPSHOT"
        const val SUBJECT_HOURS = "Недосдача часов: текущий срез"
        const val SUBJECT_1 = "Напоминание: не сдан отчёт за прошлую неделю"
        const val SUBJECT_2 = "Напоминание: не сдан отчёт 2 недели"
        const val SUBJECT_3 = "Предупреждение: блокировка аккаунта и расторжение договора"
        const val TEMPLATE_HOURS =
            "Здравствуйте, {имя}.\n\nПо текущему срезу у вас недосдача больше 20 часов. Просим закрыть отчётность и нагнать часы.\n\nДальше учитываются пропущенные недели: +1, +2, затем предупреждение о блокировке."
        const val TEMPLATE_1 =
            "Здравствуйте, {имя}.\n\nЗа прошлую неделю нет принятого отчёта (+1 неделя). Просим сдать отчёт в личном кабинете."
        const val TEMPLATE_2 =
            "Здравствуйте, {имя}.\n\nВы не сдавали отчёт 2 недели подряд. Просим закрыть отчётность."
        const val TEMPLATE_3 =
            "Здравствуйте, {имя}.\n\nВы не сдавали отчёт 3 недели подряд. Если отчёт не будет сдан, аккаунт будет заблокирован, а договор расторгнут."
    }
}
