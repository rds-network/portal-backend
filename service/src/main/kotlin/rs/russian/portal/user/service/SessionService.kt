package rs.russian.portal.user.service

import jakarta.servlet.http.HttpSession
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.Session
import org.springframework.stereotype.Service
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUser

@Service
class SessionService(
    private val sessionRepository: FindByIndexNameSessionRepository<out Session>
) {

    fun invalidate(username: String) {
        sessionRepository.findByPrincipalName(username).keys.forEach(sessionRepository::deleteById)
    }

    /**
     * Invalidates the session(s) based on the specified criteria.
     *
     * @param all indicates whether all sessions should be invalidated.
     *            If set to true, all sessions associated with the current user will be invalidated.
     *            If set to false, only the current session will be invalidated.
     */
    fun invalidate(all: Boolean) {
        if (all) {
            invalidate(currentUser()?.nickName ?: throw NotAuthorizedException())
        } else {
            val requestAttributes = RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes
            val session: HttpSession = requestAttributes.request.getSession(false)
            sessionRepository.deleteById(session.id)
        }
    }
}
