package rs.russian.portal.potvrda.service

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.generated.model.PotvrdaIssueCreateRequest
import rs.russian.generated.model.PotvrdaIssueDto
import rs.russian.generated.model.PotvrdaIssuePageResponse
import rs.russian.generated.model.PotvrdaNextNumberDto
import rs.russian.portal.potvrda.domain.PotvrdaIssue
import rs.russian.portal.potvrda.mapper.PotvrdaIssueMapper
import rs.russian.portal.potvrda.repository.PotvrdaIssueRepository
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.jpa.convert
import rs.russian.portal.shared.security.currentUserLogin
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Year

@Service
class PotvrdaIssueService(
    private val repository: PotvrdaIssueRepository,
    private val mapper: PotvrdaIssueMapper,
) {

    fun nextNumber(year: Int? = null): PotvrdaNextNumberDto {
        assertStaff()
        val registryYear = year ?: Year.now().value
        val sequence = repository.maxSequenceForYear(registryYear) + 1
        return PotvrdaNextNumberDto(
            confirmNumber = formatConfirmNumber(sequence, registryYear),
            registryYear = registryYear,
            sequenceNumber = sequence,
        )
    }

    @Transactional
    fun create(request: PotvrdaIssueCreateRequest): PotvrdaIssueDto {
        val issuer = currentUserLogin() ?: throw NotAuthorizedException()
        assertStaff()

        val registryYear = request.issueDate.year
        val sequence = repository.maxSequenceForYear(registryYear) + 1
        val entity =
            PotvrdaIssue(
                issuedBy = issuer,
                confirmNumber = formatConfirmNumber(sequence, registryYear),
                registryYear = registryYear,
                sequenceNumber = sequence,
                kind = mapper.kindFromDto(request.kind),
                volunteerUsername = request.volunteerUsername.trim(),
                volunteerFullName = request.volunteerFullName.trim(),
                issueDate = request.issueDate,
                periodFrom = request.periodFrom,
                periodTo = request.periodTo,
                totalHours = request.totalHours?.let { BigDecimal.valueOf(it.toDouble()) },
                programCode = request.programCode?.trim()?.takeIf { it.isNotEmpty() },
                servicesDescription = request.servicesDescription?.trim()?.takeIf { it.isNotEmpty() },
            )
        return mapper.toDto(repository.save(entity))
    }

    fun list(pageNumber: Int, pageSize: Int, search: String?): PotvrdaIssuePageResponse {
        assertStaff()
        val safeSize = pageSize.coerceIn(1, 200)
        val pageable = PageRequest.of(pageNumber.coerceAtLeast(0), safeSize)
        val page = repository.search(search?.trim()?.takeIf { it.isNotEmpty() }, pageable)
        return PotvrdaIssuePageResponse(
            page = convert(page),
            content = page.content.map(mapper::toDto).toMutableList(),
        )
    }

    private fun formatConfirmNumber(sequence: Int, year: Int): String =
        "%03d/%d".format(sequence, year)

    private fun assertStaff() {
        if (currentUserLogin() == null) throw NotAuthorizedException()
    }
}
