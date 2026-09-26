package rs.russian.portal.report.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import rs.russian.generated.api.ReportApi
import rs.russian.generated.model.*
import rs.russian.portal.note.mapper.NoteMapper
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.report.mapper.ReportMapper
import rs.russian.portal.report.service.ReportService
import rs.russian.portal.shared.jpa.convert
import rs.russian.portal.shared.security.Authorized
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import java.util.*

@RestController
class ReportController(
    private val reportService: ReportService,
    private val reportMapper: ReportMapper,
    private val noteMapper: NoteMapper,
) : ReportApi {

    override fun getReport(id: UUID): ResponseEntity<ReportDto> {
        val report = reportService.getReport(id)
        return ResponseEntity.ok(reportMapper.map(report))
    }

    override fun createReport(reportDto: ReportDto): ResponseEntity<ReportDto> {
        val report = reportService.createReport(reportDto)
        return ResponseEntity.ok(reportMapper.map(report))
    }

    @Authorized(allowed = [ADMIN_VOLUNTEER])
    override fun deleteReport(id: UUID): ResponseEntity<Unit> {
        reportService.deleteReport(id)
        return ResponseEntity.ok().build()
    }

    override fun updateReport(reportDto: ReportDto): ResponseEntity<ReportDto> {
        val report = reportService.updateReport(reportDto)
        return ResponseEntity.ok(reportMapper.map(report))
    }

    override fun getReports(pageRequest: PageRequest, reportFilter: ReportFilter): ResponseEntity<ReportPageResponse> {
        val page = reportService.getReports(reportFilter, convert(pageRequest))
        return ResponseEntity.ok(
            ReportPageResponse(
                page = convert(page),
                content = reportMapper.map(page.content)
            )
        )
    }

    @Authorized(allowed = [ADMIN_VOLUNTEER])
    override fun addNote(id: UUID, noteDto: NoteDto): ResponseEntity<NoteDto> {
        val note = reportService.addNote(id, noteDto)
        return ResponseEntity.ok(noteMapper.map(note))
    }

    override fun changeStatus(id: UUID, changeReportStatusRequest: ChangeReportStatusRequest): ResponseEntity<Unit> {
        reportService.changeStatus(
            id,
            ReportStatus.valueOf(changeReportStatusRequest.status),
            changeReportStatusRequest.note
        )
        return ResponseEntity.ok().build()
    }

    /**
     * Правка снимка программы и проекта, не входит в openapi-контракт: статус и приёмка отчёта не меняются,
     * поэтому модератор может починить программу у уже принятого отчёта.
     */
    @PatchMapping("/report/{id}/assignment")
    fun updateAssignment(
        @PathVariable id: UUID,
        @RequestBody request: ReportAssignmentRequest,
    ): ResponseEntity<ReportDto> {
        val report = reportService.updateAssignment(id, request.programCode, request.projectCode)
        return ResponseEntity.ok(reportMapper.map(report))
    }

}
