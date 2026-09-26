package rs.russian.portal.mup.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.generated.model.ContractTypeEnum
import rs.russian.portal.application.repository.ApplicationRepository
import rs.russian.portal.mail.domain.EmailOutbox
import rs.russian.portal.mail.repository.EmailOutboxRepository
import rs.russian.portal.mail.service.EmailService
import rs.russian.portal.mup.api.MupLetterDraft
import rs.russian.portal.mup.api.MupLetterDto
import rs.russian.portal.mup.api.MupLetterReason
import rs.russian.portal.mup.api.MupLetterSendRequest
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.report.repository.ReportRepository
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class MupLetterService(
    private val accountService: AccountService,
    private val applicationRepository: ApplicationRepository,
    private val reportRepository: ReportRepository,
    private val emailService: EmailService,
    private val emailOutboxRepository: EmailOutboxRepository,
) {

    @Transactional(readOnly = true)
    fun draft(username: String, reason: MupLetterReason = MupLetterReason.NON_COMPLIANCE): MupLetterDraft {
        val account = accountService.findAccountByLogin(username)
            ?: throw InvalidRequestException("User '$username' not found")
        val info = account.info
        val application = applicationRepository.findAllByEmail(account.email).maxByOrNull { it.created }
        val fullName = account.fullName
        val passport = application?.passport.orEmpty()
        val birth = (info?.birthDate ?: application?.birthDate)?.format(DATE).orEmpty()
        val address = listOfNotNull(
            info?.postalCode ?: application?.postalCode,
            info?.city ?: application?.city,
            info?.address ?: application?.address,
        ).filter { it.isNotBlank() }.joinToString(", ")
        val phone = info?.phone ?: application?.phone.orEmpty()
        val email = account.email
        val citizenship = application?.citizenship?.takeIf { it.isNotBlank() }
            ?: account.residencePermits.maxByOrNull { it.validUntil }?.nationality.orEmpty()
        val today = LocalDate.now()
        val lastAccepted = reportRepository
            .findTopByAccountUsernameAndStatusOrderByCreateTimeDesc(username, ReportStatus.ACCEPTED)
            ?.createTime
            ?.toLocalDate()
        val contractStart = account.contracts
            .filter { it.type == ContractTypeEnum.REGULAR }
            .maxByOrNull { it.startDate }
            ?.startDate
        val periodFrom = when {
            lastAccepted != null -> lastAccepted.plusDays(1).let { if (it.isAfter(today)) lastAccepted else it }
            contractStart != null -> contractStart
            else -> today.withDayOfYear(1)
        }
        val termination = today.format(DATE)
        val from = periodFrom.format(DATE)
        val to = today.format(DATE)
        return MupLetterDraft(
            username = account.username,
            fullName = fullName,
            passport = passport,
            birthDate = birth,
            address = address,
            phone = phone,
            email = email,
            to = "upravazastrance@mup.gov.rs",
            subject = "Обавештење о престанку уговора о волонтирању – $fullName",
            body = letter(reason, fullName, birth, citizenship, passport, termination, from, to),
            reason = reason,
        )
    }

    @Transactional
    fun send(request: MupLetterSendRequest): MupLetterDto {
        val subject = request.subject.trim()
        val body = request.body.trim()
        if (subject.length < 8 || body.length < 40) {
            throw InvalidRequestException("subject and body are required")
        }
        val to = request.to?.trim()?.takeIf { it.isNotEmpty() } ?: "upravazastrance@mup.gov.rs"
        emailService.sendCommonEmail(to, subject, body.replace("\n", "<br/>"))
        val saved = emailOutboxRepository.findAllByOrderByCreateTimeDesc().firstOrNull {
            it.properties.subject == subject && it.properties.toList.contains(to)
        }
        val deactivated = deactivateVolunteer(request.username)
        return saved?.let { toDto(it, deactivated) } ?: MupLetterDto(
            id = java.util.UUID.randomUUID(),
            createTime = java.time.LocalDateTime.now(),
            status = "CREATED",
            to = listOf(to),
            subject = subject,
            body = body,
            deactivated = deactivated,
        )
    }

    @Transactional
    fun sendForVolunteer(
        username: String,
        reason: MupLetterReason = MupLetterReason.NON_COMPLIANCE,
    ): MupLetterDto {
        val draft = draft(username, reason)
        return send(
            MupLetterSendRequest(
                username = username,
                to = draft.to,
                subject = draft.subject,
                body = draft.body,
                reason = reason,
            )
        )
    }

    @Transactional(readOnly = true)
    fun list(): List<MupLetterDto> =
        emailOutboxRepository.findAllByOrderByCreateTimeDesc()
            .filter { letter ->
                val subject = letter.properties.subject
                subject.contains("престанку уговора", ignoreCase = true) ||
                    subject.contains("раскиду уговора", ignoreCase = true)
            }
            .map(::toDto)

    private fun deactivateVolunteer(username: String): Boolean {
        val account = accountService.findAccountByLogin(username)
        if (account == null) {
            log.warn("MUP letter sent but account '{}' was not found for deactivation", username)
            return false
        }
        if (account.groups.any { it in MANAGERS }) {
            log.warn("Skip portal deactivation for manager {}", username)
            return false
        }
        val id = account.id ?: return false
        return try {
            accountService.switchActiveState(id, false)
            true
        } catch (ex: Exception) {
            log.error("Failed to deactivate {} after MUP letter", username, ex)
            false
        }
    }

    private fun toDto(item: EmailOutbox, deactivated: Boolean = false) = MupLetterDto(
        id = item.id!!,
        createTime = item.createTime,
        status = item.status.name,
        to = item.properties.toList,
        subject = item.properties.subject,
        body = item.properties.body,
        deactivated = deactivated,
    )

    companion object {
        private val log = LoggerFactory.getLogger(MupLetterService::class.java)
        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        private val MANAGERS = setOf(UserGroup.ADMIN, UserGroup.ADMIN_SSO, UserGroup.ADMIN_VOLUNTEER)

        fun letter(
            reason: MupLetterReason,
            fullName: String,
            birthDate: String,
            citizenship: String,
            passport: String,
            terminationDate: String,
            periodFrom: String,
            periodTo: String,
        ): String {
            val header = """
                Удружење „Руска дијаспора у Србији“ обавештава вас да је дана ${terminationDate.ifBlank { "—" }} раскинут уговор о волонтирању закључен са следећим лицем:

                Име и презиме: ${fullName.ifBlank { "—" }}
                Датум рођења: ${birthDate.ifBlank { "—" }}
                Држављанство: ${citizenship.ifBlank { "—" }}
                Број пасоша: ${passport.ifBlank { "—" }}
            """.trimIndent()
            val reasonBlock = when (reason) {
                MupLetterReason.VOLUNTEER_REQUEST ->
                    "Уговор је раскинут на захтев волонтера (по жељи волонтера), на основу члана 5.1 уговора, којим је предвиђено да волонтер може у свако доба раскинути уговор о волонтирању без обавезе навођења разлога."
                MupLetterReason.NON_COMPLIANCE ->
                    "Волонтер није доставио извештаје о активностима за период од ${periodFrom.ifBlank { "—" }} до ${periodTo.ifBlank { "—" }}. Након упућених обавештења и провере волонтерског ангажовања, Организатор је утврдио да волонтер не испуњава уговорене обавезе. Уговор је раскинут на основу члана 5.2 тачка 3) уговора, у вези са чланом 19. тачка 3) и чланом 20. став 2. тачка 3) Закона о волонтирању."
            }
            val footer = """
                О престанку уговора који је послужио као основ за одобрење привременог боравка обавештавамо вас у складу са чланом 8. став 1. Закона о странцима. Молимо да ову чињеницу евидентирате.

                С поштовањем,
                Леонид Стеценко
                Председник удружења „Руска дијаспора у Србији“
            """.trimIndent()
            return "$header\n\n$reasonBlock\n\n$footer"
        }

        @Deprecated("Use letter(reason, ...)", ReplaceWith("letter(MupLetterReason.NON_COMPLIANCE, fullName, birthDate, citizenship, passport, terminationDate, periodFrom, periodTo)"))
        fun letter(
            fullName: String,
            birthDate: String,
            citizenship: String,
            passport: String,
            terminationDate: String,
            periodFrom: String,
            periodTo: String,
        ): String = letter(
            MupLetterReason.NON_COMPLIANCE,
            fullName,
            birthDate,
            citizenship,
            passport,
            terminationDate,
            periodFrom,
            periodTo,
        )
    }
}
