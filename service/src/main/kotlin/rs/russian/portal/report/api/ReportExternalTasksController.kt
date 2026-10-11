package rs.russian.portal.report.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.generated.model.ReportView
import rs.russian.portal.report.mapper.ReportMapper
import rs.russian.portal.report.service.ReportExternalService
import rs.russian.portal.shared.security.AuthorizedService

/**
 * Append tasks to an open Ekomapa auto-report, or create a new one.
 * Hand-written (like /ext/ekomapa/link) so we do not depend on OpenAPI codegen for this path.
 */
@RestController
@RequestMapping("/ext")
class ReportExternalTasksController(
    private val reportMapper: ReportMapper,
    private val reportExternalService: ReportExternalService,
) {

    @AuthorizedService
    @PostMapping("/report/tasks")
    fun appendTasks(@RequestBody request: ExtAppendReportTasksRequest): ResponseEntity<ReportView> {
        val report = reportExternalService.appendOrCreate(request)
        return ResponseEntity.ok(reportMapper.mapView(report))
    }
}
