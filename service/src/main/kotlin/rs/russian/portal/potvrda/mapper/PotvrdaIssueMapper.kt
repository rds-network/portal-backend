package rs.russian.portal.potvrda.mapper

import org.springframework.stereotype.Component
import rs.russian.generated.model.PotvrdaIssueDto
import rs.russian.generated.model.PotvrdaKindEnum
import rs.russian.portal.potvrda.domain.PotvrdaIssue
import rs.russian.portal.potvrda.domain.PotvrdaKind

@Component
class PotvrdaIssueMapper {

    fun toDto(entity: PotvrdaIssue): PotvrdaIssueDto =
        PotvrdaIssueDto(
            id = entity.id!!,
            confirmNumber = entity.confirmNumber,
            registryYear = entity.registryYear,
            sequenceNumber = entity.sequenceNumber,
            kind = entity.kind.toDto(),
            volunteerUsername = entity.volunteerUsername,
            volunteerFullName = entity.volunteerFullName,
            issueDate = entity.issueDate,
            periodFrom = entity.periodFrom,
            periodTo = entity.periodTo,
            totalHours = entity.totalHours?.toDouble(),
            programCode = entity.programCode,
            servicesDescription = entity.servicesDescription,
            createTime = entity.createTime,
            issuedBy = entity.issuedBy,
        )

    private fun PotvrdaKind.toDto(): PotvrdaKindEnum =
        when (this) {
            PotvrdaKind.OFFICIAL -> PotvrdaKindEnum.OFFICIAL
            PotvrdaKind.MEMBERSHIP -> PotvrdaKindEnum.MEMBERSHIP
        }

    fun kindFromDto(kind: PotvrdaKindEnum): PotvrdaKind =
        when (kind) {
            PotvrdaKindEnum.OFFICIAL -> PotvrdaKind.OFFICIAL
            PotvrdaKindEnum.MEMBERSHIP -> PotvrdaKind.MEMBERSHIP
        }
}
