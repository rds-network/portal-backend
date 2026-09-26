package rs.russian.portal.report.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.generated.model.*
import rs.russian.portal.program.service.ProgramCuratorService
import rs.russian.portal.report.mapper.HeatMapMapper
import rs.russian.portal.report.repository.ReportHeatMapRepository
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.jpa.convert
import rs.russian.portal.shared.security.currentUserRoles
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_SSO
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import rs.russian.portal.user.domain.enums.UserGroup.MAIN_VOLUNTEER
import rs.russian.portal.user.mapper.UserMapper
import rs.russian.portal.user.service.AccountService
import java.time.LocalDate.now

@Service
class HeatMapService(
    private val userMapper: UserMapper,
    private val userService: AccountService,
    private val heatMapMapper: HeatMapMapper,
    private val reportHeatMapRepository: ReportHeatMapRepository,
    private val programCuratorService: ProgramCuratorService,
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
        val scopedFilter = resolveHeatMapFilter(filter)
        val accounts = userService.searchWithActiveRegularContract(
            searchQuery,
            pageRequest,
            UserSearchFilter(program = scopedFilter.program, project = scopedFilter.project, onlyActive = true)
        )
        val data = reportHeatMapRepository.findVolunteerHeatmap(
            usernames = accounts.map { it.username }.toSet(),
            year = scopedFilter.year ?: now().year
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

    private fun resolveHeatMapFilter(filter: ReportsHeatMapFilter): ReportsHeatMapFilter {
        val roles = currentUserRoles() ?: throw NotAuthorizedException()
        val managers = setOf(ADMIN, ADMIN_VOLUNTEER, ADMIN_SSO, MAIN_VOLUNTEER)
        if (roles.any { it in managers }) {
            return filter
        }

        val curatorPrograms = programCuratorService.programCodesOfCurrentUser()
        if (curatorPrograms.isEmpty()) {
            throw NotAuthorizedException()
        }

        val requested = filter.program?.trim()?.takeIf { it.isNotEmpty() }
        val program = when {
            requested != null -> {
                if (curatorPrograms.none { it.equals(requested, ignoreCase = true) }) {
                    throw NotAuthorizedException()
                }
                requested.uppercase()
            }
            curatorPrograms.size == 1 -> curatorPrograms.first()
            else -> throw InvalidRequestException("program is required for curators")
        }

        return ReportsHeatMapFilter(
            year = filter.year,
            program = program,
            project = filter.project,
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
