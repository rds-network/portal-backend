package rs.russian.portal.application.domain

import jakarta.persistence.*
import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.EnumType.STRING
import org.hibernate.annotations.SQLRestriction
import rs.russian.generated.model.ContractTypeEnum
import rs.russian.portal.application.domain.ApplicationStatus.CREATED
import rs.russian.portal.application.domain.ApplicationType.NEW
import rs.russian.portal.application.domain.listener.ApplicationEntityListener
import rs.russian.portal.note.domain.Note
import rs.russian.portal.program.domain.Program
import rs.russian.portal.program.domain.Project
import rs.russian.portal.shared.jpa.JpaEntity
import rs.russian.portal.user.domain.enums.Gender
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Entity
@NamedEntityGraph(
    name = Application.GRAPH_FULL,
    attributeNodes = [
        NamedAttributeNode("notes"),
        NamedAttributeNode("program"),
        NamedAttributeNode("project")
    ]
)
@EntityListeners(ApplicationEntityListener::class)
class Application(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    @Enumerated(STRING)
    var status: ApplicationStatus = CREATED,
    @Enumerated(STRING)
    var type: ApplicationType = NEW,
    var created: LocalDateTime = LocalDateTime.now(),
    var assignee: String? = null,

    var email: String,
    var name: String,
    var patronymic: String? = null,
    var birthDate: LocalDate? = null,
    var passport: String? = null,
    var citizenship: String? = null,
    var telegram: String? = null,
    var inSerbia: Boolean? = null,
    var enterDate: LocalDate? = null,
    var city: String? = null,
    var postalCode: String? = null,
    var address: String? = null,
    var phone: String? = null,
    var residenceRequired: Boolean? = null,
    var occupation: String? = null,
    @Enumerated(STRING)
    var gender: Gender? = null,
    var hasExperience: Boolean? = null,
    var experience: String? = null,
    var languages: String? = null,
    var skills: String? = null,
    var goal: String? = null,
    var bio: String? = null,

    var refuseReason: String? = null,
    var comment: String? = null,

    var contractFrom: LocalDate? = null,
    var contractUntil: LocalDate? = null,
    /** Дата в шапке бланка. Ставится один раз, когда даты договора сохраняют впервые. */
    var contractDocumentDate: LocalDate? = null,
    @Enumerated(STRING)
    var contractType: ContractTypeEnum? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_code")
    var program: Program? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_code")
    var project: Project? = null,

    @SQLRestriction("entity_type = 'APPLICATION'")
    @OneToMany(mappedBy = "entityId", cascade = [ALL], orphanRemoval = true)
    var notes: MutableSet<Note> = mutableSetOf(),

    ) : JpaEntity<UUID>() {

    @Transient
    private var persistedStatus: ApplicationStatus = status

    /** In-memory snapshot for lifecycle side effects; this is not a status history. */
    fun capturePersistedStatus(): Boolean {
        val changed = persistedStatus != status
        persistedStatus = status
        return changed
    }

    /**
     * Irreversibly scrubs the applicant's personal data, mirroring account depersonalization. Structural
     * and statistical fields (status, type, created, contract dates/type, program, project) are kept so the
     * application funnel still aggregates; free-text, contact and identifying fields are cleared and the
     * moderation notes (which may quote personal data) are removed.
     */
    fun depersonalize() {
        email = DEPERSONALIZED_EMAIL
        name = DEPERSONALIZED_NAME
        patronymic = null
        birthDate = null
        passport = null
        citizenship = null
        telegram = null
        inSerbia = null
        enterDate = null
        city = null
        postalCode = null
        address = null
        phone = null
        residenceRequired = null
        occupation = null
        gender = null
        hasExperience = null
        experience = null
        languages = null
        skills = null
        goal = null
        bio = null
        refuseReason = null
        comment = null
        notes.clear()
    }

    companion object {
        const val GRAPH_FULL = "Application.Full"
        private const val DEPERSONALIZED_NAME = "Деперсонализированный пользователь"
        private const val DEPERSONALIZED_EMAIL = "depersonalized@deleted.local"
    }
}
