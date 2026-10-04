package rs.russian.portal.user.scheduler

import io.authentik.model.UserTypeEnum
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import rs.russian.portal.accountstatus.domain.enums.AccountStatusEventSource
import rs.russian.portal.accountstatus.service.AccountStatusService
import rs.russian.portal.shared.jpa.isNull
import rs.russian.portal.shared.jpa.less
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.Account_
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.AccountService
import rs.russian.portal.user.service.AccountSynchronizer
import rs.russian.portal.user.service.authentik.AuthentikService
import java.time.Duration
import java.time.LocalDateTime

private val log = LoggerFactory.getLogger(UserSyncScheduler::class.java)

@Component
class UserSyncScheduler(
    private val accountService: AccountService,
    private val accountStatusService: AccountStatusService,
    private val accountRepository: AccountRepository,
    private val accountSynchronizers: List<AccountSynchronizer>,
    private val authentikUserService: AuthentikService,
) {

    @Scheduled(cron = "\${app.schedulers.user-sync}")
    @SchedulerLock(name = "syncUsers")
    fun sync() {
        log.info("Starting user sync")
        val syncStart = LocalDateTime.now()
        val authentikUsers = authentikUserService.getAllUsers()
        val usersToSync = authentikUsers
            .asSequence()
            .filter { it.type != UserTypeEnum.service_account }
            .filter { it.type != UserTypeEnum.internal_service_account }
            .filter { !it.email.isNullOrBlank() }
            .filter { it.isActive == true }
            .map { ssoUser ->
                accountService.createOrUpdateAccount(ssoUser)
            }
            .toList()
        accountSynchronizers.forEach { it.sync(usersToSync) }

        val inactiveSpec = less<Account, LocalDateTime>(Account_.LAST_SYNCED, syncStart)
            .or(isNull(Account_.LAST_SYNCED))
        val inactiveUsers = accountRepository.findAll(inactiveSpec)
        inactiveUsers.forEach { user ->
            val wasActive = user.active
            accountService.save(user.also { it.active = false })
            accountSynchronizers.forEach { it.delete(user) }
            if (wasActive) {
                accountStatusService.recordExternalChange(
                    account = user,
                    activeTo = false,
                    source = AccountStatusEventSource.SYNC,
                    reason = "user sync: absent from Authentik active users",
                )
            }
        }

        log.info(
            "User sync completed in ${Duration.between(syncStart, LocalDateTime.now()).toMillis()}ms: " +
                    "${usersToSync.size} users attempted to sync, ${inactiveUsers.size} users attempted to deactivate"
        )
    }

}
