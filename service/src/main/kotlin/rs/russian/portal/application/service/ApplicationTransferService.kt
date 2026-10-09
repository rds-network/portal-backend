package rs.russian.portal.application.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.application.domain.Application
import rs.russian.portal.application.domain.ApplicationStatus
import rs.russian.portal.application.repository.ApplicationRepository
import rs.russian.portal.note.domain.Note
import rs.russian.portal.note.domain.enums.EntityType
import rs.russian.portal.note.repository.NoteRepository
import rs.russian.portal.note.service.NoteService
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import rs.russian.portal.user.domain.enums.UserGroup.INTERVIEWER
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class ApplicationTransferService(
    private val applicationRepository: ApplicationRepository,
    private val accountService: AccountService,
    private val noteService: NoteService,
    private val noteRepository: NoteRepository,
) {

    data class TransferResult(val moved: Int)

    @Transactional
    fun transfer(fromLogin: String, toLogin: String, transferDate: LocalDate? = null): TransferResult {
        val from = resolveEmployee(fromLogin)
        val to = resolveEmployee(toLogin)
        if (from.username.equals(to.username, ignoreCase = true)) {
            throw InvalidRequestException("from and to must differ")
        }
        val dateLabel = (transferDate ?: LocalDate.now()).format(DATE_FMT)
        val noteText = transferNote(
            dateLabel = dateLabel,
            fromName = from.fullName,
            toName = to.fullName,
            fromLogin = from.username,
            toLogin = to.username,
        )
        val open = openAssignedTo(from.username)
        open.forEach { app ->
            app.assignee = to.username
            applicationRepository.save(app)
            addSystemNote(app.id!!, noteText)
        }
        return TransferResult(open.size)
    }

    /**
     * Откат: только заявки, которые сейчас у [toLogin] и имеют комментарий о передаче from→to.
     * Не трогает «свои» заявки Фоменко, которые не приходили от Соболевской.
     */
    @Transactional
    fun revert(fromLogin: String, toLogin: String): TransferResult {
        val from = resolveEmployee(fromLogin)
        val to = resolveEmployee(toLogin)
        if (from.username.equals(to.username, ignoreCase = true)) {
            throw InvalidRequestException("from and to must differ")
        }
        val dateLabel = LocalDate.now().format(DATE_FMT)
        val revertText = revertNote(
            dateLabel = dateLabel,
            fromName = from.fullName,
            toName = to.fullName,
            fromLogin = from.username,
            toLogin = to.username,
        )
        val candidates = openAssignedTo(to.username)
        var moved = 0
        for (app in candidates) {
            if (!wasTransferredFromTo(app.id!!, from.username, to.username, from.fullName, to.fullName)) {
                continue
            }
            app.assignee = from.username
            applicationRepository.save(app)
            addSystemNote(app.id!!, revertText)
            moved++
        }
        return TransferResult(moved)
    }

    private fun openAssignedTo(username: String): List<Application> =
        applicationRepository.findOpenByAssigneeIgnoreCase(username, CLOSED)

    private fun wasTransferredFromTo(
        applicationId: UUID,
        fromLogin: String,
        toLogin: String,
        fromName: String,
        toName: String,
    ): Boolean {
        val notes = noteRepository.findAllByEntityIdAndEntityType(
            applicationId,
            EntityType.APPLICATION,
        )
        val tag = transferTag(fromLogin, toLogin)
        val legacyNeedle = "Передача полномочий"
        val arrowLegacy = "${fromName.trim()} → ${toName.trim()}"
        return notes.any { note ->
            val text = note.text
            text.contains(tag, ignoreCase = true) ||
                (text.contains(legacyNeedle) && text.contains(arrowLegacy))
        }
    }

    private fun addSystemNote(applicationId: UUID, text: String) {
        val actor = currentUserLogin() ?: throw NotAuthorizedException()
        noteService.save(
            Note(
                createdBy = actor,
                entityId = applicationId,
                entityType = EntityType.APPLICATION,
                text = text,
            )
        )
    }

    private fun resolveEmployee(login: String) =
        accountService.findAccountByLogin(login.trim())
            ?.takeIf { it.active && it.groups.any { g -> g == ADMIN_VOLUNTEER || g == INTERVIEWER } }
            ?: throw InvalidRequestException("Employee not found or not an application assignee: $login")

    companion object {
        private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        private val CLOSED = listOf(ApplicationStatus.DONE, ApplicationStatus.DENY)

        fun transferTag(fromLogin: String, toLogin: String) =
            "[transfer:${fromLogin.lowercase()}->${toLogin.lowercase()}]"

        fun transferNote(
            dateLabel: String,
            fromName: String,
            toName: String,
            fromLogin: String,
            toLogin: String,
        ): String =
            "Передача полномочий $dateLabel: $fromName → $toName ${transferTag(fromLogin, toLogin)}"

        fun revertNote(
            dateLabel: String,
            fromName: String,
            toName: String,
            fromLogin: String,
            toLogin: String,
        ): String =
            "Откат передачи $dateLabel: $toName → $fromName [revert:${toLogin.lowercase()}->${fromLogin.lowercase()}]"
    }
}
