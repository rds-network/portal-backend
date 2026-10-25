package rs.russian.portal.inbox.scheduler

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import rs.russian.portal.inbox.service.ReportOverdueService

/**
 * Авторассылка порицаний отключена.
 * Раз в сутки неассоциированные волонтёры с 6+ пропусками ставятся в очередь на расторжение.
 */
@Component
class OverdueReportsScheduler(
    private val reportOverdueService: ReportOverdueService,
) {

    @Scheduled(cron = "\${app.schedulers.overdue-reports}")
    @SchedulerLock(name = "overdueReports")
    fun run() {
        val queued = reportOverdueService.enqueueForRepeatedAbsences()
        if (queued > 0) {
            log.info("[SCHEDULER] Dissolution queue: {} volunteers with 6+ absences", queued)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OverdueReportsScheduler::class.java)
    }
}
