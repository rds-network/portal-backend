package rs.russian.portal.report.api

import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rs.russian.generated.model.ReportDto
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.report.mapper.ReportMapper
import rs.russian.portal.report.service.ReportService

@RestController
@RequestMapping("/customer-reports")
class CustomerReportController(
    private val reportService: ReportService,
    private val reportMapper: ReportMapper,
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
    ): ResponseEntity<CustomerReportPageDto> {
        val parsed = status?.trim()?.takeIf { it.isNotEmpty() }?.let { ReportStatus.valueOf(it) }
        val result = reportService.getReportsForCustomer(parsed, PageRequest.of(page, size))
        return ResponseEntity.ok(
            CustomerReportPageDto(
                total = result.totalElements,
                page = result.number,
                size = result.size,
                content = reportMapper.map(result.content),
            )
        )
    }

    @GetMapping("/pending-count")
    fun pendingCount(): ResponseEntity<Map<String, Long>> =
        ResponseEntity.ok(mapOf("count" to reportService.pendingCountForCustomer()))
}

data class CustomerReportPageDto(
    val total: Long,
    val page: Int,
    val size: Int,
    val content: List<ReportDto>,
)
