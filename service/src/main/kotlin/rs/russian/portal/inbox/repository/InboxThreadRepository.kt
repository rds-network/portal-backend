package rs.russian.portal.inbox.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rs.russian.portal.inbox.domain.InboxThread
import java.util.UUID

interface InboxThreadRepository : JpaRepository<InboxThread, UUID> {

    @Query(
        """
        SELECT DISTINCT t FROM InboxThread t
        JOIN t.participants p
        WHERE p.username = :username
        ORDER BY t.createTime DESC
        """
    )
    fun findAllForUser(@Param("username") username: String): List<InboxThread>

    @Query(
        """
        SELECT DISTINCT t FROM InboxThread t
        ORDER BY t.createTime DESC
        """
    )
    fun findAllForManagers(): List<InboxThread>

    @Query(
        """
        SELECT COUNT(p) FROM InboxParticipant p
        WHERE p.username = :username AND p.unread = true
        """
    )
    fun countUnread(@Param("username") username: String): Long
}
