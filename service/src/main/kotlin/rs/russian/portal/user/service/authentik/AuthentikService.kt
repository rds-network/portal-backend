package rs.russian.portal.user.service.authentik

import io.authentik.model.User
import rs.russian.portal.user.domain.Account

interface AuthentikService {

    fun getUser(email: String): User?

    fun getUser(id: Int): User?

    fun getAllUsers(): List<User>

    fun createUser(username: String, name: String, email: String): User

    fun switchActiveState(account: Account, isActive: Boolean)

    fun createRecoveryLink(account: Account): String
}
