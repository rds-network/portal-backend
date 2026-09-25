package rs.russian.portal.inbox.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    fun list(): List<ReportOverdueDto> = reportOverdueJdbc.findOverdue()

    @Transactional
    fun notifyDue(): Int {
        val periodKey = LocalDate.now().with(java.time.DayOfWeek.MONDAY).format(WEEK_KEY)
        var sent = 0
        for (item in list()) {
            val already = reportOverdueNoticeRepository.existsByUsernameAndLevelAndPeriodKey(
                item.username,
                item.weeksMissed,
                periodKey,
            )
            if (already) continue
            inboxService.notifyOverdue(item.username, item.weeksMissed, subjectFor(item.weeksMissed), bodyFor(item))
            reportOverdueNoticeRepository.save(
                ReportOverdueNotice(
                    username = item.username,
                    level = item.weeksMissed,
                    periodKey = periodKey,
                )
            )
            sent += 1
        }
        log.info("[SCHEDULER] Overdue report notices sent: {}", sent)
        return sent
    }

    private fun subjectFor(weeks: Int) =
        if (weeks >= 3) SUBJECT_3 else SUBJECT_2

    private fun bodyFor(item: ReportOverdueDto): String {
        val last = item.lastReportWeek?.format(DATE) ?: "нет принятых отчётов"
        return if (item.weeksMissed >= 3) {
            "Здравствуйте, ${item.fullName}.\n\n" +
                "Вы не сдавали отчёт 3 недели подряд. Если отчёт не будет сдан, аккаунт будет заблокирован, а договор расторгнут.\n\n" +
                "Последняя принятая неделя: $last.\n\nПожалуйста, заполните отчётность в личном кабинете и ответьте на это сообщение, если нужна помощь."
        } else {
            "Здравствуйте, ${item.fullName}.\n\n" +
                "Вы не сдавали отчёт 2 недели. Просим заполнить отчётность в личном кабинете.\n\n" +
                "Последняя принятая неделя: $last."
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ReportOverdueService::class.java)
        private val WEEK_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("YYYY-'W'ww")
        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        const val SUBJECT_2 = "Напоминание: не сдан отчёт 2 недели"
        const val SUBJECT_3 = "Предупреждение: блокировка аккаунта и расторжение договора"
    }
}
