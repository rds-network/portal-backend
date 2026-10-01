package rs.russian.portal.achievements.scheduler

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import rs.russian.portal.achievements.service.AchievementsService

@Component
class AchievementsWeeklyScheduler(
    private val achievementsService: AchievementsService,
) {
    /** Monday 03:15 — settle previous ISO week for all active volunteers. */
    @Scheduled(cron = "0 15 3 * * MON")
    @SchedulerLock(name = "achievementsWeeklySettle")
    fun settle() {
        log.info("Settling weekly presence points")
        achievementsService.settlePreviousWeekForAllActive()
        log.info("Weekly presence points settled")
    }

    companion object {
        private val log = LoggerFactory.getLogger(AchievementsWeeklyScheduler::class.java)
    }
}
