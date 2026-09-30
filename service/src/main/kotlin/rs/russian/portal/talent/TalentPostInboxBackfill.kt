package rs.russian.portal.talent

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import rs.russian.portal.talent.service.TalentService

/**
 * Sends inbox notices for OPEN talent posts that predate the broadcast feature.
 * Idempotent — skips posts that already have a TALENT_POST thread.
 */
@Component
class TalentPostInboxBackfill(
    private val talentService: TalentService,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        try {
            val sent = talentService.backfillInboxForOpenPosts()
            if (sent > 0) {
                log.info("Backfilled inbox notices for {} open talent posts", sent)
            }
        } catch (ex: Exception) {
            log.warn("Talent post inbox backfill failed", ex)
        }
    }
}
