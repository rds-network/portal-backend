package rs.russian.portal.user.service.authentik

import io.authentik.model.User
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import rs.russian.portal.user.domain.Account
import java.util.*
import kotlin.random.Random

@Service
@Profile("no-auth")
class AuthentikServiceMock : AuthentikService {

    override fun getUser(email: String): User {
        log.info("Mock get user")
        return getDefaultUser(email.split("@").first(), email)
    }

    override fun getUser(id: Int): User? = null

    override fun getAllUsers(): List<User> {
        log.info("Mock get all users")
        return emptyList()
    }

    override fun createUser(username: String, name: String, email: String): User {
        log.info("Mock create user")
        return getDefaultUser(username, email)
    }

    override fun switchActiveState(account: Account, isActive: Boolean) {
        log.info("Mock switch active state")
    }

    override fun createRecoveryLink(account: Account): String {
        log.info("Mock create recovery link")
        return "https://id.russian.rs/account-recovery/${account.id}"
    }

    private fun getDefaultUser(username: String, email: String): User {
        val uid = UUID.randomUUID()
        return User(
            pk = Random.nextInt(),
            username = username,
            email = email,
            avatar = "",
            name = "John Smith",
            uuid = uid,
            uid = uid.toString(),
            groupsObj = mutableListOf(),
            isSuperuser = false
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }
}
