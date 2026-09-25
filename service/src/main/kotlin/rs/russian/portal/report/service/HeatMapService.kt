package rs.russian.portal.report.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.generated.model.*
import rs.russian.portal.report.mapper.HeatMapMapper
import rs.russian.portal.report.repository.ReportHeatMapRepository
import rs.russian.portal.shared.jpa.convert
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.mapper.UserMapper
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate.now

@Service
class HeatMapService(
    private val userMapper: UserMapper,
    private val userService: AccountService,
    private val heatMapMapper: HeatMapMapper,
    private val reportHeatMapRepository: ReportHeatMapRepository,
) {

    @Transactional(readOnly = true)
    fun getCurrentUserHeatMap(): Map<String, VolunteerHeatMapItem> {
        val account = userService.getCurrentAccount()
        val currentYear = now().year
        val previousYear = currentYear - 1
        val currentYearData = reportHeatMapRepository.findVolunteerHeatmap(
            usernames = setOf(account.username),
            year = now().year
        )
        val previousYearData = reportHeatMapRepository.findVolunteerHeatmap(
            usernames = setOf(account.username),
            year = now().year - 1
        )
        return mapOf(
            previousYear.toString() to createHeatMapItem(account, heatMapMapper.map(previousYearData)),
            currentYear.toString() to createHeatMapItem(account, heatMapMapper.map(currentYearData))
        )
    }

    @Transactional(readOnly = true)
    fun getHeatMap(
        searchQuery: String,
        pageRequest: PageRequest,
        filter: ReportsHeatMapFilter,
    ): ReportsHeatMapPageResponse {
        val accounts = userService.searchWithActiveRegularContract(
            searchQuery,
            pageRequest,
            UserSearchFilter(program = filter.program, project = filter.project, onlyActive = true)
        )
        val data = reportHeatMapRepository.findVolunteerHeatmap(
            usernames = accounts.map { it.username }.toSet(),
            year = filter.year ?: now().year
        )
        val heatMap = HashMap<String, MutableList<HeatMapItem>>()
        data.forEach { row ->
            val weekItem = heatMapMapper.map(row)
            heatMap.merge(row.username, mutableListOf(weekItem)) { old, new -> (old + new).toMutableList() }
        }
        val result = ArrayList<VolunteerHeatMapItem>()
        accounts.forEach { account -> result.add(createHeatMapItem(account, heatMap[account.username])) }
        return ReportsHeatMapPageResponse(
            content = result.sortedByDescending { r -> r.totalRequired!!.minus(r.totalWorked!!) }.toMutableList(),
            page = convert(accounts),
        )
    }

    private fun createHeatMapItem(account: Account, weekItems: List<HeatMapItem>?): VolunteerHeatMapItem {
        val weeks = weekItems ?: emptyList()
        return VolunteerHeatMapItem(
            volunteerInfo = userMapper.map(account.info),
            weeks = weeks.toMutableList(),
            totalWorked = weeks.sumOf { it.hoursWorked },
            totalRequired = weeks.sumOf { it.hoursRequired }
        )
    }
}
