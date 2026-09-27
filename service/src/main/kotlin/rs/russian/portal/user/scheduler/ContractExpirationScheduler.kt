package rs.russian.portal.user.scheduler

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import rs.russian.portal.accountstatus.domain.enums.AccountStatusEventSource
import rs.russian.portal.accountstatus.service.AccountStatusService
import rs.russian.portal.application.domain.ApplicationStatus.DENY
import rs.russian.portal.application.domain.ApplicationStatus.DONE
import rs.russian.portal.application.domain.ApplicationType.PROLONGATION
import rs.russian.portal.application.repository.ApplicationRepository
import rs.russian.portal.mail.service.EmailService
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.repository.AccountRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class ContractExpirationScheduler(
    private val accountRepository: AccountRepository,
    private val applicationRepository: ApplicationRepository,
    private val accountStatusService: AccountStatusService,
    private val emailService: EmailService,
    private val templateEngine: TemplateEngine,
) {

    @Scheduled(cron = "\${app.schedulers.contract-expiration}")
    @SchedulerLock(name = "contractExpiration")
    fun run() {
        val today = LocalDate.now()
        val reminderDate = today.plusDays(REMINDER_DAYS)
        sendExpirationReminders(reminderDate)
        deactivateExpiredAccounts(today)
    }

    private fun sendExpirationReminders(reminderDate: LocalDate) {
        val accounts = accountRepository.findByLatestContractDate(reminderDate, true)
        val notified = accounts.filterNot { hasActiveProlongation(it) }
        var failures = 0
        notified.forEach { account ->
            try {
                val endDate = account.contracts.maxOfOrNull { it.endDate } ?: return@forEach
                val formattedDate = endDate.format(DATE_FORMATTER)
                val deactivationDate = endDate.plusDays(1).format(DATE_FORMATTER)
                if (account.email.isNotBlank()) {
                    val message = templateEngine.process(
                        "contract_expiration_reminder",
                        Context().also {
                            it.setVariables(
                                mapOf(
                                    "contractEndDate" to formattedDate,
                                    "deactivationDate" to deactivationDate
                                )
                            )
                        }
                    )
                    emailService.sendCommonEmail(account, REMINDER_SUBJECT, message)
                } else {
                    log.warn("Skipping reminder for account {} due to missing email", account.username)
                }
            } catch (ex: Exception) {
                failures += 1
                log.error("Failed to send contract reminder for account {}", account.username, ex)
            }
        }
        log.info(
            "[SCHEDULER] Contract reminders sent: {} (failures: {})",
            notified.size - failures,
            failures
        )
    }

    private fun deactivateExpiredAccounts(today: LocalDate) {
        val cutoffDate = today.minusDays(1)
        val accounts = accountRepository.findByLatestContractDate(cutoffDate, false)
        val expired = accounts.filterNot { hasActiveProlongation(it) }
        val admins = accountRepository.findAllActiveByGroup(UserGroup.ADMIN_VOLUNTEER.name)
        var failures = 0
        expired.forEach { account ->
            try {
                account.id?.let {
                    accountStatusService.applyImmediate(
                        accountId = it,
                        activeTo = false,
                        source = AccountStatusEventSource.SCHEDULER,
                        actorUsername = null,
                        reason = "contract expired",
                        notifyApprover = true,
                    )
                }
                notifyAdminsOfDeactivation(admins, account)
            } catch (ex: Exception) {
                failures += 1
                log.error("Failed to deactivate account {}", account.username, ex)
            }
        }
        log.info(
            "[SCHEDULER] Accounts deactivated due to expired contracts: {} (failures: {})",
            expired.size - failures,
            failures
        )
    }

    private fun hasActiveProlongation(account: Account): Boolean {
        val email = account.email
        if (email.isBlank()) {
            return false
        }
        return applicationRepository.existsByEmailAndTypeAndStatusNotIn(email, PROLONGATION, listOf(DONE, DENY))
    }

    private fun notifyAdminsOfDeactivation(admins: List<Account>, account: Account) {
        if (admins.isEmpty()) {
            log.warn("No admins found for deactivation notification")
            return
        }
        val endDate = account.contracts.maxOfOrNull { it.endDate } ?: return
        val formattedDate = endDate.format(DATE_FORMATTER)
        val message = templateEngine.process(
            "contract_deactivation_admin",
            Context().also {
                it.setVariables(
                    mapOf(
                        "fullName" to account.fullName,
                        "contractEndDate" to formattedDate
                    )
                )
            }
        )
        admins.forEach { admin ->
            if (admin.email.isNotBlank()) {
                emailService.sendCommonEmail(admin, DEACTIVATION_SUBJECT, message)
            } else {
                log.warn("Skipping admin notification for account {} due to missing email", admin.username)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ContractExpirationScheduler::class.java)
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        private const val REMINDER_DAYS = 7L
        private const val REMINDER_SUBJECT = "Окончание контракта"
        private const val DEACTIVATION_SUBJECT = "Деактивация учетной записи волонтера"
    }
}
