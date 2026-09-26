package rs.russian.portal.mup.api

import java.time.LocalDateTime
import java.util.UUID

enum class MupLetterReason {
    /** Невыполнение обязанностей / несдача отчётов (организатор расторгает). */
    NON_COMPLIANCE,

    /** Расторжение по желанию волонтёра (ст. 5.1 договора). */
    VOLUNTEER_REQUEST,
}

data class MupLetterDraft(
    val username: String,
    val fullName: String,
    val passport: String,
    val birthDate: String,
    val address: String,
    val phone: String,
    val email: String,
    val to: String = "upravazastrance@mup.gov.rs",
    val subject: String,
    val body: String,
    val reason: MupLetterReason = MupLetterReason.NON_COMPLIANCE,
)

data class MupLetterSendRequest(
    val username: String,
    val to: String? = null,
    val subject: String,
    val body: String,
    val reason: MupLetterReason? = null,
)

data class MupLetterDto(
    val id: UUID,
    val createTime: LocalDateTime,
    val status: String,
    val to: List<String>,
    val subject: String,
    val body: String,
    val deactivated: Boolean = false,
)
