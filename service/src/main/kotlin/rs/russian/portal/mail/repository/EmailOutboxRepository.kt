package rs.russian.portal.mail.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import rs.russian.portal.mail.domain.EmailOutbox
import rs.russian.portal.mail.domain.EmailOutboxStatus
import java.util.*

@Repository
interface EmailOutboxRepository : JpaRepository<EmailOutbox, UUID> {

    fun findAllByStatusIn(statuses: List<EmailOutboxStatus>): List<EmailOutbox>

    fun findAllByOrderByCreateTimeDesc(): List<EmailOutbox>
}
