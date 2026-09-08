package rs.russian.portal.shared.security

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.user.domain.enums.DepersonalizationStatus
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.authentik.AuthentikService
import java.time.Duration

@Service
class AccountAccessService(
    private val accountRepository: AccountRepository,
    private val authentikService: AuthentikService,
) {
    // Bound the delay for deactivations made directly in Authentik. Never cache API failures.
    private val remoteActivity = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofSeconds(60))
        .build<Int, Boolean>()

    @Transactional(readOnly = true)
    fun isActive(username: String): Boolean {
        val account = accountRepository.findForAuthenticationByUsername(username) ?: return false
        if (!account.active || account.depersonalizationStatus == DepersonalizationStatus.DEPERSONALIZED) {
            return false
        }
        val id = account.id ?: return false
        return remoteActivity.get(id) { authentikService.getUser(it)?.isActive == true } == true
    }
}
