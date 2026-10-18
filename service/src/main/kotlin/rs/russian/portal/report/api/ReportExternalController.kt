package rs.russian.portal.report.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.generated.api.ReportExternalApi
import rs.russian.generated.model.AppendReportTasksRequest
import rs.russian.generated.model.CreateReportRequest
import rs.russian.generated.model.ReportView
import rs.russian.portal.report.mapper.ReportMapper
import rs.russian.portal.report.service.ReportExternalService
import rs.russian.portal.shared.security.AuthorizedService

@RestController
@RequestMapping("/ext")
class ReportExternalController(
    private val reportMapper: ReportMapper,
    private val reportService: ReportExternalService,
) : ReportExternalApi {

    @AuthorizedService
    override fun createReport(createReportRequest: CreateReportRequest): ResponseEntity<ReportView> {
        val report = reportService.createReport(createReportRequest)
        return ResponseEntity.ok(reportMapper.mapView(report))
    }

    @AuthorizedService
    override fun appendReportTasks(appendReportTasksRequest: AppendReportTasksRequest): ResponseEntity<ReportView> {
        val mapped = ExtAppendReportTasksRequest(
            user = appendReportTasksRequest.user,
            reportId = appendReportTasksRequest.reportId,
            tasks = appendReportTasksRequest.tasks.map { t ->
                ExtCreateTaskRequest(
                    date = t.date,
                    name = t.name,
                    description = t.description,
                    timeSpent = t.timeSpent,
                    result = t.result,
                    nameSr = t.nameSr,
                    descriptionSr = t.descriptionSr,
                )
            },
        )
        val report = reportService.appendOrCreate(mapped)
        return ResponseEntity.ok(reportMapper.mapView(report))
    }
}
