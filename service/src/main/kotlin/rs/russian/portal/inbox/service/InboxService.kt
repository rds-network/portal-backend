package rs.russian.portal.inbox.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.inbox.api.InboxCreateRequest
import rs.russian.portal.inbox.api.InboxMessageDto
import rs.russian.portal.inbox.api.InboxReplyRequest
import rs.russian.portal.inbox.api.InboxThreadDetailDto
import rs.russian.portal.inbox.api.InboxThreadDto
import rs.russian.portal.inbox.domain.InboxMessage
import rs.russian.portal.inbox.domain.InboxParticipant
import rs.russian.portal.inbox.domain.InboxThread
import rs.russian.portal.inbox.repository.InboxThreadRepository
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import rs.russian.portal.user.domain.enums.UserGroup.MAIN_VOLUNTEER
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.AccountService
import java.util.UUID

@Service
class InboxService(
    private val inboxThreadRepository: InboxThreadRepository,
    private val accountService: AccountService,
    private val accountRepository: AccountRepository,
) {

    @Transactional(readOnly = true)
    fun unreadCount(): Long {
        val login = currentUserLogin() ?: throw NotAuthorizedException()
        return inboxThreadRepository.countUnread(login)
    }

    @Transactional(readOnly = true)
    fun list(): List<InboxThreadDto> {
        val account = accountService.getCurrentAccount()
        val threads = if (isManager(account.groups)) {
            inboxThreadRepository.findAllForManagers()
        } else {
            inboxThreadRepository.findAllForUser(account.username)
        }
        return threads.map { toListDto(it, account.username) }
    }

    @Transactional
    fun get(id: UUID): InboxThreadDetailDto {
        val account = accountService.getCurrentAccount()
        val thread = inboxThreadRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Inbox thread $id not found") }
        if (!canSee(thread, account.username, isManager(account.groups))) {
            throw NotAuthorizedException()
        }
        thread.participants.filter { it.username.equals(account.username, ignoreCase = true) }
            .forEach { it.unread = false }
        return InboxThreadDetailDto(
            id = thread.id!!,
            subject = thread.subject,
            kind = thread.kind,
            createdBy = thread.createdBy,
            messages = thread.messages.map {
                InboxMessageDto(it.id!!, it.author, it.body, it.createTime)
            },
        )
    }

    @Transactional
    fun create(request: InboxCreateRequest): List<InboxThreadDto> {
        val account = accountService.getCurrentAccount()
        if (!isManager(account.groups)) throw NotAuthorizedException()
        val subject = request.subject.trim()
        val body = request.body.trim()
        if (subject.isEmpty() || body.isEmpty()) {
            throw InvalidRequestException("subject and body are required")
        }
        val recipients = request.recipients.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (recipients.isEmpty()) {
            throw InvalidRequestException("recipients are required")
        }
        return recipients.map { recipient ->
            val found = accountService.findAccountByLogin(recipient)
                ?: throw InvalidRequestException("Recipient '$recipient' not found")
            toListDto(
                openThread(
                    subject = subject,
                    body = body,
                    kind = InboxThread.KIND_MANUAL,
                    createdBy = account.username,
                    recipient = found.username,
                    extraParticipants = listOf(account.username),
                    recipientUnread = true,
                ),
                account.username,
            )
        }
    }

    @Transactional
    fun reply(id: UUID, request: InboxReplyRequest): InboxThreadDetailDto {
        val account = accountService.getCurrentAccount()
        val thread = inboxThreadRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Inbox thread $id not found") }
        if (!canSee(thread, account.username, isManager(account.groups))) {
            throw NotAuthorizedException()
        }
        val body = request.body.trim()
        if (body.isEmpty()) throw InvalidRequestException("body is required")
        ensureParticipant(thread, account.username, unread = false)
        thread.messages.add(
            InboxMessage(thread = thread, author = account.username, body = body)
        )
        thread.participants.forEach { participant ->
            participant.unread = !participant.username.equals(account.username, ignoreCase = true)
        }
        return get(id)
    }

    @Transactional
    fun notifyOverdue(username: String, level: Int, subject: String, body: String): InboxThread {
        val extra = accountRepository.findAllActiveByGroup(ADMIN_VOLUNTEER.name).map { it.username }
        return openThread(
            subject = subject,
            body = body,
            kind = if (level >= 3) InboxThread.KIND_OVERDUE_3 else InboxThread.KIND_OVERDUE_2,
            createdBy = null,
            recipient = username,
            extraParticipants = extra,
            recipientUnread = true,
        )
    }

    private fun openThread(
        subject: String,
        body: String,
        kind: String,
        createdBy: String?,
        recipient: String,
        extraParticipants: List<String>,
        recipientUnread: Boolean,
    ): InboxThread {
        val thread = InboxThread(subject = subject, kind = kind, createdBy = createdBy)
        val people = (extraParticipants + recipient).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        people.forEach { login ->
            thread.participants.add(
                InboxParticipant(
                    thread = thread,
                    username = login,
                    unread = login.equals(recipient, ignoreCase = true) && recipientUnread,
                )
            )
        }
        thread.messages.add(InboxMessage(thread = thread, author = createdBy, body = body))
        return inboxThreadRepository.save(thread)
    }

    private fun ensureParticipant(thread: InboxThread, username: String, unread: Boolean) {
        if (thread.participants.none { it.username.equals(username, ignoreCase = true) }) {
            thread.participants.add(InboxParticipant(thread = thread, username = username, unread = unread))
        }
    }

    private fun canSee(thread: InboxThread, username: String, manager: Boolean): Boolean {
        if (manager) return true
        return thread.participants.any { it.username.equals(username, ignoreCase = true) }
    }

    private fun toListDto(thread: InboxThread, username: String) = InboxThreadDto(
        id = thread.id!!,
        createTime = thread.createTime,
        subject = thread.subject,
        kind = thread.kind,
        createdBy = thread.createdBy,
        unread = thread.participants.firstOrNull { it.username.equals(username, ignoreCase = true) }?.unread == true,
        lastBody = thread.messages.lastOrNull()?.body,
        counterpart = thread.participants
            .map { it.username }
            .firstOrNull { !it.equals(username, ignoreCase = true) },
    )

    private fun isManager(groups: Set<rs.russian.portal.user.domain.enums.UserGroup>): Boolean =
        groups.any { it == ADMIN || it == ADMIN_VOLUNTEER || it == MAIN_VOLUNTEER }
}
