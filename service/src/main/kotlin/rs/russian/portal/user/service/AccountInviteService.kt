package rs.russian.portal.user.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import rs.russian.portal.mail.service.EmailService
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.service.authentik.AuthentikService

/**
 * Welcome / password-setup email for newly provisioned portal accounts.
 * Kept outside [AccountEventListener] so application completion can invoke it
 * reliably (nested AFTER_COMMIT transactional events are easy to drop).
 */
@Service
class AccountInviteService(
    private val emailService: EmailService,
    private val templateEngine: TemplateEngine,
    private val authentikService: AuthentikService,
) {

    fun sendWelcomeEmail(account: Account) {
        val recoveryLink = authentikService.createRecoveryLink(account)
        val message = templateEngine.process(
            "account_created",
            Context().also { it.setVariables(mapOf("link" to recoveryLink)) },
        )
        emailService.sendCommonEmail(account, "Учетная запись", message)
        log.info("Queued welcome email for account {} ({})", account.username, account.email)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AccountInviteService::class.java)
    }
}
