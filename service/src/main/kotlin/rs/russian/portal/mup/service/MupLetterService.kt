package rs.russian.portal.mup.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.application.repository.ApplicationRepository
import rs.russian.portal.mail.domain.EmailOutbox
import rs.russian.portal.mail.repository.EmailOutboxRepository
import rs.russian.portal.mail.service.EmailService
import rs.russian.portal.mup.api.MupLetterDraft
import rs.russian.portal.mup.api.MupLetterDto
import rs.russian.portal.mup.api.MupLetterSendRequest
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.user.service.AccountService
import java.time.format.DateTimeFormatter

@Service
class MupLetterService(
    private val accountService: AccountService,
    private val applicationRepository: ApplicationRepository,
    private val emailService: EmailService,
    private val emailOutboxRepository: EmailOutboxRepository,
) {

    @Transactional(readOnly = true)
    fun draft(username: String): MupLetterDraft {
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
        return MupLetterDraft(
            username = account.username,
            fullName = fullName,
            passport = passport,
            birthDate = birth,
            address = address,
            phone = phone,
            email = email,
            to = "upravazastrance@mup.gov.rs",
            subject = "Обавештење о раскиду уговора о волонтирању – $fullName",
            body = letter(fullName, passport, birth, address, phone, email),
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
        return saved?.let(::toDto) ?: MupLetterDto(
            id = java.util.UUID.randomUUID(),
            createTime = java.time.LocalDateTime.now(),
            status = "CREATED",
            to = listOf(to),
            subject = subject,
            body = body,
        )
    }

    @Transactional(readOnly = true)
    fun list(): List<MupLetterDto> =
        emailOutboxRepository.findAllByOrderByCreateTimeDesc()
            .filter { it.properties.subject.contains("раскиду уговора", ignoreCase = true) }
            .map(::toDto)

    private fun toDto(item: EmailOutbox) = MupLetterDto(
        id = item.id!!,
        createTime = item.createTime,
        status = item.status.name,
        to = item.properties.toList,
        subject = item.properties.subject,
        body = item.properties.body,
    )

    companion object {
        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        fun letter(
            fullName: String,
            passport: String,
            birthDate: String,
            address: String,
            phone: String,
            email: String,
        ): String = """
            ПРЕДМЕТ: Обавештење о раскиду уговора о волонтирању – $fullName

            Поштовани,

            Овим путем Вас обавештавамо да је Удружење „РУСКА ДИЈАСПОРА У СРБИЈИ“ донело одлуку о једностраном раскиду уговора о волонтирању са следећим лицем:

            Име и презиме: $fullName
            Број пасоша: ${passport.ifBlank { "—" }}
            Датум рођења: ${birthDate.ifBlank { "—" }}
            Место боравка: ${address.ifBlank { "—" }}
            Телефон: ${phone.ifBlank { "—" }}
            Е-маил: ${email.ifBlank { "—" }}

            Разлог за раскид је одсуство активности и непостојање стварног ангажовања у оквиру волонтерских програма удружења, односно неиспуњење обавеза из уговора о волонтирању у вези са боравком (ВНЖ). Уговор сматрати неважећим.

            Молимо да се ово унесе у евиденцију и узме у обзир приликом евентуалних административних поступака у вези са боравком наведеног лица.

            С поштовањем,
            Леонид Стеценко
            Председник удружења „Руска дијаспора у Србији“
            Šarplaninska 54, Нови Сад
            Тел: +381 62 154 78 93
            Email: ruskadijasporausrbiji@gmail.com
        """.trimIndent()
    }
}
