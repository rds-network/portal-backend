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
        JOIN p.thread t
        WHERE p.username = :username AND p.unread = true
          AND (
            t.kind <> 'LEAVE_REQUEST'
            OR LOWER(:username) = LOWER(:leaveApprover)
            OR LOWER(t.createdBy) = LOWER(:username)
          )
          AND (
            t.kind <> 'LEAVE_DECISION'
            OR LOWER(t.recipient) = LOWER(:username)
            OR LOWER(t.createdBy) = LOWER(:username)
          )
        """
    )
    fun countUnread(
        @Param("username") username: String,
        @Param("leaveApprover") leaveApprover: String,
    ): Long

    @Query(
        """
        SELECT COUNT(p) FROM InboxParticipant p
        JOIN p.thread t
        WHERE LOWER(p.username) = LOWER(:username)
          AND p.ackRequired = true
          AND p.receivedAt IS NULL
          AND NOT EXISTS (
            SELECT 1 FROM InboxMessage m
            WHERE m.thread = p.thread AND LOWER(m.author) = LOWER(p.username)
          )
          AND (
            t.kind <> 'LEAVE_REQUEST'
            OR LOWER(:username) = LOWER(:leaveApprover)
            OR LOWER(t.createdBy) = LOWER(:username)
          )
          AND (
            t.kind <> 'LEAVE_DECISION'
            OR LOWER(t.recipient) = LOWER(:username)
            OR LOWER(t.createdBy) = LOWER(:username)
          )
        """
    )
    fun countPendingAck(
        @Param("username") username: String,
        @Param("leaveApprover") leaveApprover: String,
    ): Long
}
