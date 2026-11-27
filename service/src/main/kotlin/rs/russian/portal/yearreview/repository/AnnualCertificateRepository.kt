package rs.russian.portal.yearreview.repository

import org.springframework.data.jpa.repository.JpaRepository
import rs.russian.portal.yearreview.domain.AnnualCertificate
import rs.russian.portal.yearreview.domain.AnnualCertificateStatus
import java.util.Optional
import java.util.UUID

interface AnnualCertificateRepository : JpaRepository<AnnualCertificate, UUID> {
    fun findByUsernameIgnoreCaseAndYearAndTemplateVersion(
        username: String,
        year: Int,
        templateVersion: String,
    ): Optional<AnnualCertificate>

    fun findAllByUsernameIgnoreCaseOrderByYearDesc(username: String): List<AnnualCertificate>

    fun findByVerificationToken(token: UUID): Optional<AnnualCertificate>

    fun findByUsernameIgnoreCaseAndYearAndStatus(
        username: String,
        year: Int,
        status: AnnualCertificateStatus,
    ): Optional<AnnualCertificate>

    fun countByYearAndCertificateNumberStartingWith(year: Int, prefix: String): Long
}
