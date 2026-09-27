package rs.russian.portal.accountstatus.domain

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import rs.russian.portal.accountstatus.domain.enums.AccountStatusRequestStatus
import rs.russian.portal.shared.jpa.JpaEntity
import rs.russian.portal.user.domain.Account
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "account_status_request")
class AccountStatusRequest(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_account_id", nullable = false)
    var targetAccount: Account,

    var requestedActive: Boolean,

    @Enumerated(EnumType.STRING)
    var status: AccountStatusRequestStatus = AccountStatusRequestStatus.PENDING,

    var reason: String? = null,

    var createdBy: String,

    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    var decidedBy: String? = null,

    var decidedAt: OffsetDateTime? = null,

    var decisionReason: String? = null,
) : JpaEntity<UUID>() {

    override fun equalityProperties() = setOf(AccountStatusRequest::id)
}
