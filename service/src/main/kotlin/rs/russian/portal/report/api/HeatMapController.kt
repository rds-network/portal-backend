package rs.russian.portal.report.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import rs.russian.generated.api.ReportHeatMapApi
import rs.russian.generated.model.PageRequest
import rs.russian.generated.model.ReportsHeatMapFilter
import rs.russian.generated.model.ReportsHeatMapPageResponse
import rs.russian.generated.model.VolunteerHeatMapItem
import rs.russian.portal.report.service.HeatMapService
import java.time.LocalDate

@RestController
class HeatMapController(
    private val heatMapService: HeatMapService,
) : ReportHeatMapApi {
    
    @Deprecated("use getCurrentUserHeatMap()")
    override fun getCurrentUserHeatMapDeprecated(): ResponseEntity<VolunteerHeatMapItem> {
        return ResponseEntity.ok(heatMapService.getCurrentUserHeatMap()[LocalDate.now().year.toString()])
    }

    override fun getCurrentUserHeatMap(): ResponseEntity<Map<String, VolunteerHeatMapItem>> {
        return ResponseEntity.ok(heatMapService.getCurrentUserHeatMap())
    }

    override fun getVolunteerHeatMap(
        searchQuery: String,
        pageRequest: PageRequest,
        reportsHeatMapFilter: ReportsHeatMapFilter,
    ): ResponseEntity<ReportsHeatMapPageResponse> {
        return ResponseEntity.ok(heatMapService.getHeatMap(searchQuery, pageRequest, reportsHeatMapFilter))
    }
}
