package rs.russian.portal.user.domain

import jakarta.persistence.*
import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.FetchType.LAZY
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import rs.russian.portal.program.domain.Program
import rs.russian.portal.shared.jpa.JpaEntity
import rs.russian.portal.shared.jpa.converter.UserGroupSetConverter
import rs.russian.portal.user.domain.enums.DepersonalizationStatus
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.domain.listener.AccountEntityListener
import java.time.LocalDateTime
import java.time.OffsetDateTime

@Entity
@EntityListeners(AccountEntityListener::class)
@NamedEntityGraph(
    name = Account.GRAPH_FULL,
    attributeNodes = [
        NamedAttributeNode("info", subgraph = UserInfo.GRAPH_FULL),
        NamedAttributeNode("contracts"),
        NamedAttributeNode("residencePermits", subgraph = Account.GRAPH_RESIDENCE_PERMIT_PHOTOS),
    ],
    subgraphs = [
        NamedSubgraph(
            name = UserInfo.GRAPH_FULL,
            attributeNodes = [
                NamedAttributeNode("avatar"),
                NamedAttributeNode("program", subgraph = Program.GRAPH_FULL),
                NamedAttributeNode("project")
            ]
        ),
        NamedSubgraph(
            name = Program.GRAPH_FULL,
            attributeNodes = [NamedAttributeNode("projects")]
        ),
        NamedSubgraph(
            name = Account.GRAPH_RESIDENCE_PERMIT_PHOTOS,
            attributeNodes = [
                NamedAttributeNode("frontSidePhoto"),
                NamedAttributeNode("backSidePhoto")
            ]
        )
    ]
)
class Account(
    @Id
    override var id: Int? = null,
    override var version: LocalDateTime? = null,

    var username: String,
    var email: String,
    var fullName: String,

    @OneToOne(mappedBy = "account", cascade = [ALL], fetch = LAZY, orphanRemoval = true)
    var info: UserInfo? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = UserGroupSetConverter::class)
    var groups: Set<UserGroup> = mutableSetOf(),

    @OneToMany(mappedBy = "account", cascade = [ALL], orphanRemoval = true)
    var contracts: MutableSet<Contract> = mutableSetOf(),

    @OneToMany(mappedBy = "account", cascade = [ALL], orphanRemoval = true)
    var residencePermits: MutableSet<ResidencePermit> = mutableSetOf(),

    var active: Boolean = true,

    @Enumerated(STRING)
    var depersonalizationStatus: DepersonalizationStatus = DepersonalizationStatus.NONE,

    var depersonalizedAt: LocalDateTime? = null,

    var lastSynced: LocalDateTime? = null,

    var lastSeenAt: LocalDateTime? = null,

    var reportBlocked: Boolean = false,

    var reportBlockedAt: OffsetDateTime? = null,

    var reportBlockedBy: String? = null,

    var reportBlockedReason: String? = null,

    var reportControllerUsername: String? = null,

    var reportControllerReason: String? = null,

    var reportControllerAt: OffsetDateTime? = null,

    /** Когда в МУП ушло письмо о расторжении (ручная отправка). */
    var mupLetterSentAt: OffsetDateTime? = null,

    /** Причина письма в МУП: NON_COMPLIANCE / VOLUNTEER_REQUEST. */
    var mupLetterReason: String? = null,

    /** Когда аккаунт поставлен в очередь на расторжение. */
    var dissolutionQueuedAt: OffsetDateTime? = null,

    /** Кто поставил в очередь на расторжение. */
    var dissolutionQueuedBy: String? = null,

    /** Опциональная причина постановки в очередь. */
    var dissolutionQueueReason: String? = null,

    /**
     * ID пользователя в Экомапе (код EVO-{id}).
     * Заполняется синками из Экомапы при привязке Authentik / artisan backfill.
     */
    var ekomapaUserId: Int? = null,

    ) : JpaEntity<Int>() {

    override fun equalityProperties() = setOf(Account::username)

    companion object {
        const val GRAPH_FULL = "Account.Full"
        const val GRAPH_USERNAME = "Account.Username"
        const val GRAPH_RESIDENCE_PERMIT_PHOTOS = "ResidencePermit.photos"
    }
}
