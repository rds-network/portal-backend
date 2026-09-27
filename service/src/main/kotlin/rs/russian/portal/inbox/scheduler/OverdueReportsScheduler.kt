package rs.russian.portal.inbox.scheduler

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cron остаётся, но авторассылка отключена.
 * Порицания выносят модераторы вручную (issueWarning на профиле).
 */
@Component
class OverdueReportsScheduler {

    private val logged = AtomicBoolean(false)

    @Scheduled(cron = "\${app.schedulers.overdue-reports}")
    @SchedulerLock(name = "overdueReports")
    fun run() {
        if (logged.compareAndSet(false, true)) {
            log.info("[SCHEDULER] Overdue auto-notify disabled; list only, manual issueWarning")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OverdueReportsScheduler::class.java)
    }
}
