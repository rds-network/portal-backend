package rs.russian.portal.accountstatus.domain

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import rs.russian.portal.accountstatus.domain.enums.AccountStatusEventSource
import rs.russian.portal.shared.jpa.JpaEntity
import rs.russian.portal.user.domain.Account
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "account_status_event")
class AccountStatusEvent(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    var account: Account,

    var activeTo: Boolean,

    @Enumerated(EnumType.STRING)
    var source: AccountStatusEventSource,

    var actorUsername: String? = null,

    var reason: String? = null,

    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    var request: AccountStatusRequest? = null,
) : JpaEntity<UUID>() {

    override fun equalityProperties() = setOf(AccountStatusEvent::id)
}
