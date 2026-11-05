package rs.russian.portal.application.domain.specification

import org.springframework.data.jpa.domain.Specification
import rs.russian.generated.model.ApplicationsFilter
import rs.russian.portal.application.domain.Application
import rs.russian.portal.application.domain.ApplicationStatus
import rs.russian.portal.application.domain.ApplicationStatus.DENY
import rs.russian.portal.application.domain.ApplicationStatus.DONE
import rs.russian.portal.application.domain.Application_
import rs.russian.portal.shared.jpa.contains
import rs.russian.portal.shared.jpa.empty
import rs.russian.portal.shared.jpa.equal
import rs.russian.portal.shared.jpa.isNull
import rs.russian.portal.shared.jpa.less
import rs.russian.portal.shared.jpa.like
import rs.russian.portal.shared.jpa.notContains
import java.time.LocalDateTime


fun searchSpecification(query: String?, filter: ApplicationsFilter?): Specification<Application> {
    var specification: Specification<Application> = empty()

    if (!query.isNullOrBlank()) {
        specification = specification.and(
            like<Application>(Application_.NAME, query)
                .or(like(Application_.EMAIL, query))
                .or(like(Application_.PHONE, query))
                .or(like(Application_.TELEGRAM, query))
        )
    }

    filter?.let {
        var filterSpec: Specification<Application> = empty()

        if (!it.showCompleted) {
            filterSpec = filterSpec.and(notContains(Application_.STATUS, listOf(DONE, DENY)))
        }

        if (it.unassigned == true) {
            filterSpec = filterSpec.and(isNull(Application_.ASSIGNEE))
        } else {
            it.assignee?.takeIf { login -> login.isNotBlank() }?.let { login ->
                filterSpec = filterSpec.and(equal(Application_.ASSIGNEE, login))
            }
        }

        val statuses = it.statuses
            ?.mapNotNull { code -> runCatching { ApplicationStatus.valueOf(code.trim()) }.getOrNull() }
            ?.distinct()
            .orEmpty()
        if (statuses.isNotEmpty()) {
            filterSpec = filterSpec.and(contains(Application_.STATUS, statuses))
        }

        it.staleDays?.takeIf { days -> days > 0 }?.let { days ->
            val cutoff = LocalDateTime.now().minusDays(days.toLong())
            filterSpec = filterSpec.and(less(Application_.VERSION, cutoff))
        }

        specification = specification.and(filterSpec)
    }

    return specification
}
