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
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
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
    fun pendingAckCount(): Long {
        val login = currentUserLogin() ?: throw NotAuthorizedException()
        return inboxThreadRepository.countPendingAck(login)
    }

    @Transactional(readOnly = true)
    fun list(): List<InboxThreadDto> {
        val account = accountService.getCurrentAccount()
        val threads = if (isManager(account.groups)) {
            inboxThreadRepository.findAllForManagers()
        } else {
            inboxThreadRepository.findAllForUser(account.username)
        }
        val names = nameMap(
            threads.flatMap { thread ->
                listOfNotNull(recipientOf(thread), thread.createdBy) +
                    thread.participants.map { it.username }
            }
        )
        return threads.map { toListDto(it, account.username, lastSeenMap(threads), names) }
    }

    @Transactional
    fun get(id: UUID): InboxThreadDetailDto {
        val account = accountService.getCurrentAccount()
        val thread = inboxThreadRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Inbox thread $id not found") }
        if (!canSee(thread, account.username, isManager(account.groups))) {
            throw NotAuthorizedException()
        }
        rememberReceived(thread)
        thread.participants.filter { it.username.equals(account.username, ignoreCase = true) }
            .forEach { participant ->
                if (!(participant.ackRequired && receivedAtOf(thread) == null)) {
                    participant.unread = false
                }
            }
        return toDetailDto(thread, account.username)
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
            val mine = participant.username.equals(account.username, ignoreCase = true)
            participant.unread = !mine
            if (mine && participant.receivedAt == null) {
                participant.receivedAt = OffsetDateTime.now()
            }
        }
        return get(id)
    }

    @Transactional
    fun ack(id: UUID): InboxThreadDetailDto {
        val account = accountService.getCurrentAccount()
        val thread = inboxThreadRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Inbox thread $id not found") }
        if (!canSee(thread, account.username, isManager(account.groups))) {
            throw NotAuthorizedException()
        }
        val now = OffsetDateTime.now()
        thread.participants.filter { it.username.equals(account.username, ignoreCase = true) }
            .forEach { participant ->
                participant.unread = false
                if (participant.receivedAt == null) {
                    participant.receivedAt = now
                }
            }
        return toDetailDto(thread, account.username)
    }

    @Transactional
    fun notifyAssigned(username: String, title: String, details: String?, createdBy: String) {
        val extra = details?.trim()?.takeIf { it.isNotEmpty() }?.let { "\n\n$it" } ?: ""
        openThread(
            subject = "Вам назначена задача",
            body = "Вам назначена задача: $title$extra\n\nОткройте раздел «Задачи», чтобы взять её в работу.",
            kind = InboxThread.KIND_TASK,
            createdBy = createdBy,
            recipient = username,
            extraParticipants = listOf(createdBy),
            recipientUnread = true,
        )
    }

    @Transactional
    fun notifyReportCustomer(customer: String, volunteerName: String, reportId: String) {
        openThread(
            subject = "Отчёт на приёмку: $volunteerName",
            body = "Вам отправили отчёт как заказчику задачи.\n\nВолонтёр: $volunteerName.\nОткройте отчёт и примите или верните его, если вы заказчик этой работы.\n\n/report/$reportId",
            kind = InboxThread.KIND_REPORT_CUSTOMER,
            createdBy = currentUserLogin(),
            recipient = customer,
            extraParticipants = emptyList(),
            recipientUnread = true,
        )
    }

    @Transactional
    fun notifyOverdue(username: String, level: Int, subject: String, body: String): InboxThread {
        val extra = accountRepository.findAllActiveByGroup(ADMIN_VOLUNTEER.name).map { it.username }
        return openThread(
            subject = subject,
            body = body,
            kind = when {
                level >= 3 -> InboxThread.KIND_OVERDUE_3
                level >= 2 -> InboxThread.KIND_OVERDUE_2
                level >= 1 -> InboxThread.KIND_OVERDUE_1
                else -> InboxThread.KIND_OVERDUE_HOURS
            },
            createdBy = null,
            recipient = username,
            extraParticipants = extra,
            recipientUnread = true,
            extraUnread = true,
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
        extraUnread: Boolean = false,
    ): InboxThread {
        val thread = InboxThread(subject = subject, kind = kind, createdBy = createdBy, recipient = recipient)
        val people = (extraParticipants + recipient).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        people.forEach { login ->
            val isRecipient = login.equals(recipient, ignoreCase = true)
            thread.participants.add(
                InboxParticipant(
                    thread = thread,
                    username = login,
                    unread = if (isRecipient) recipientUnread else extraUnread,
                    ackRequired = isRecipient && requiresAck(kind),
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

    private fun toListDto(
        thread: InboxThread,
        username: String,
        lastSeen: Map<String, LocalDateTime?> = emptyMap(),
        names: Map<String, String> = emptyMap(),
    ): InboxThreadDto {
        val recipient = recipientOf(thread)
        val counterpart = recipient
            ?: thread.participants.map { it.username }.firstOrNull { !it.equals(username, ignoreCase = true) }
        val mine = thread.participants.firstOrNull { it.username.equals(username, ignoreCase = true) }
        val receivedAt = receivedAtOf(thread)
        return InboxThreadDto(
            id = thread.id!!,
            createTime = thread.createTime,
            subject = thread.subject,
            kind = thread.kind,
            createdBy = thread.createdBy,
            unread = mine?.unread == true,
            lastBody = thread.messages.lastOrNull()?.body,
            counterpart = counterpart,
            counterpartName = displayName(counterpart, names),
            heatmapUser = heatmapUser(thread),
            reportId = reportId(thread),
            recipient = recipient,
            recipientName = displayName(recipient, names),
            recipientLastSeen = toOffset(lastSeen[recipient?.lowercase()]),
            receivedAt = receivedAt,
            ackRequired = mine?.ackRequired == true,
            needsAck = mine?.ackRequired == true && receivedAt == null,
        )
    }

    private fun toDetailDto(thread: InboxThread, username: String): InboxThreadDetailDto {
        val recipient = recipientOf(thread)
        val mine = thread.participants.firstOrNull { it.username.equals(username, ignoreCase = true) }
        val receivedAt = receivedAtOf(thread)
        val lastSeen = recipient?.let { lastSeenMap(listOf(thread))[it.lowercase()] }
        val names = nameMap(
            listOfNotNull(recipient, thread.createdBy) +
                thread.messages.mapNotNull { it.author }
        )
        return InboxThreadDetailDto(
            id = thread.id!!,
            subject = thread.subject,
            kind = thread.kind,
            createdBy = thread.createdBy,
            createdByName = displayName(thread.createdBy, names),
            heatmapUser = heatmapUser(thread),
            reportId = reportId(thread),
            recipient = recipient,
            recipientName = displayName(recipient, names),
            recipientLastSeen = toOffset(lastSeen),
            receivedAt = receivedAt,
            ackRequired = mine?.ackRequired == true,
            needsAck = mine?.ackRequired == true && receivedAt == null,
            messages = thread.messages.map {
                InboxMessageDto(
                    id = it.id!!,
                    author = it.author,
                    authorName = displayName(it.author, names),
                    body = it.body,
                    createTime = it.createTime,
                )
            },
        )
    }

    private fun lastSeenMap(threads: List<InboxThread>): Map<String, LocalDateTime?> {
        val logins = threads.mapNotNull { recipientOf(it) }.distinct()
        if (logins.isEmpty()) return emptyMap()
        return accountRepository.findLastSeenByUsernames(logins)
            .associate { it.username.lowercase() to it.lastSeen }
    }

    private fun nameMap(logins: Collection<String?>): Map<String, String> {
        val keys = logins.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct()
        if (keys.isEmpty()) return emptyMap()
        return accountRepository.findAllByUsernameIn(keys)
            .associate { it.username.lowercase() to it.fullName }
    }

    private fun displayName(login: String?, names: Map<String, String>): String? {
        if (login.isNullOrBlank()) return null
        return names[login.lowercase()] ?: login
    }

    private fun receivedAtOf(thread: InboxThread): OffsetDateTime? {
        val recipient = recipientOf(thread) ?: return null
        val stored = thread.participants
            .firstOrNull { it.username.equals(recipient, ignoreCase = true) }
            ?.receivedAt
        if (stored != null) return stored
        return thread.messages
            .filter { it.author.equals(recipient, ignoreCase = true) }
            .maxOfOrNull { it.createTime }
    }

    private fun rememberReceived(thread: InboxThread) {
        val recipient = recipientOf(thread) ?: return
        val inferred = receivedAtOf(thread) ?: return
        thread.participants
            .filter { it.username.equals(recipient, ignoreCase = true) && it.receivedAt == null }
            .forEach { participant ->
                participant.receivedAt = inferred
                participant.unread = false
            }
    }

    private fun recipientOf(thread: InboxThread): String? =
        thread.recipient?.takeIf { it.isNotBlank() }
            ?: heatmapUser(thread)
            ?: thread.participants.map { it.username }
                .firstOrNull { !it.equals(thread.createdBy, ignoreCase = true) }

    private fun toOffset(at: LocalDateTime?): OffsetDateTime? =
        at?.atZone(ZoneId.systemDefault())?.toOffsetDateTime()

    private fun requiresAck(kind: String): Boolean =
        kind == InboxThread.KIND_MANUAL ||
            kind == InboxThread.KIND_TASK ||
            kind.startsWith("OVERDUE")

    private fun heatmapUser(thread: InboxThread): String? {
        if (!thread.kind.startsWith("OVERDUE") && thread.kind != InboxThread.KIND_TASK) return null
        return thread.participants
            .map { it.username }
            .lastOrNull { !it.equals(thread.createdBy, ignoreCase = true) }
            ?: thread.participants.lastOrNull()?.username
    }

    private fun reportId(thread: InboxThread): String? {
        if (thread.kind != InboxThread.KIND_REPORT_CUSTOMER) return null
        val body = thread.messages.lastOrNull()?.body.orEmpty()
        return REPORT_PATH.find(body)?.groupValues?.get(1)
    }

    private fun isManager(groups: Set<rs.russian.portal.user.domain.enums.UserGroup>): Boolean =
        groups.any { it == ADMIN || it == ADMIN_VOLUNTEER || it == MAIN_VOLUNTEER }

    companion object {
        private val REPORT_PATH = Regex("/report/([0-9a-fA-F-]{36})")
    }
}
