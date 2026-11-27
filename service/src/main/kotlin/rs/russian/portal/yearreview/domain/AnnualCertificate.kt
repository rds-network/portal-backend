package rs.russian.portal.yearreview.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import rs.russian.portal.shared.jpa.JpaEntity
import java.time.LocalDateTime
import java.util.UUID

enum class AnnualCertificateStatus {
    ISSUED,
    REVOKED,
}

enum class AnnualCertificateSource {
    AUTOMATIC,
    MANUAL,
}

@Entity
@Table(name = "annual_certificate")
class AnnualCertificate(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    @Column(nullable = false)
    var username: String,

    @Column(nullable = false)
    var year: Int,

    @Column(name = "certificate_number", nullable = false, length = 64)
    var certificateNumber: String,

    @Column(name = "verification_token", nullable = false, unique = true)
    var verificationToken: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: AnnualCertificateStatus = AnnualCertificateStatus.ISSUED,

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_source", nullable = false, length = 32)
    var issueSource: AnnualCertificateSource = AnnualCertificateSource.AUTOMATIC,

    @Column(name = "qualification_reasons", columnDefinition = "TEXT")
    var qualificationReasons: String? = null,

    @Column(name = "stats_snapshot", columnDefinition = "TEXT")
    var statsSnapshot: String? = null,

    @Column(name = "template_version", nullable = false, length = 64)
    var templateVersion: String = TEMPLATE_ANNUAL_SUPER,

    @Column(name = "recipient_full_name")
    var recipientFullName: String? = null,

    @Column(name = "pdf_file_id")
    var pdfFileId: UUID? = null,

    @Column(name = "issued_at", nullable = false)
    var issuedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "revoked_at")
    var revokedAt: LocalDateTime? = null,

    @Column(name = "revoked_by")
    var revokedBy: String? = null,

    @Column(name = "revoke_reason", length = 1000)
    var revokeReason: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) : JpaEntity<UUID>() {
    override fun equalityProperties() = setOf(AnnualCertificate::id)

    companion object {
        const val TEMPLATE_ANNUAL_SUPER = "annual_super_participant"
    }
}
