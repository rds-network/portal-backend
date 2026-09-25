package rs.russian.portal.inbox.scheduler

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import rs.russian.portal.inbox.service.ReportOverdueService

@Component
class OverdueReportsScheduler(
    private val reportOverdueService: ReportOverdueService,
) {

    @Scheduled(cron = "\${app.schedulers.overdue-reports}")
    @SchedulerLock(name = "overdueReports")
    fun run() {
        reportOverdueService.notifyDue()
    }
}
