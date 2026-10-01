package rs.russian.portal.user.service

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import rs.russian.portal.user.event.UserCreatedEvent

/**
 * Legacy hook for [UserCreatedEvent].
 *
 * Welcome email for application completion is sent explicitly from
 * [rs.russian.portal.application.service.ApplicationEventListener] (nested AFTER_COMMIT
 * listeners were dropping invites). Admin-created users are invited from
 * [AccountService.create].
 *
 * Kept so existing Spring wiring / tests that expect the bean still resolve; no-op by design.
 */
@Component
@Profile("!local")
class AccountEventListener {

    @EventListener
    fun handleUserCreation(event: UserCreatedEvent) {
        log.debug("UserCreatedEvent id={} (welcome email handled by caller)", event.id)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AccountEventListener::class.java)
    }
}
