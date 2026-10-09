package rs.russian.portal.user.domain.specification

import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.JoinType
import org.springframework.data.jpa.domain.Specification
import rs.russian.generated.model.ContractTypeEnum
import rs.russian.generated.model.UserSearchFilter
import rs.russian.portal.program.domain.Program
import rs.russian.portal.program.domain.Program_
import rs.russian.portal.program.domain.Project
import rs.russian.portal.program.domain.Project_
import rs.russian.portal.report.domain.Report
import rs.russian.portal.report.domain.Task
import rs.russian.portal.shared.jpa.empty
import rs.russian.portal.shared.jpa.equal
import rs.russian.portal.shared.jpa.like
import rs.russian.portal.user.EkomapaVolunteerCode
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.Account_
import rs.russian.portal.user.domain.Contract
import rs.russian.portal.user.domain.UserInfo
import rs.russian.portal.user.domain.UserInfo_
import java.time.LocalDate

fun searchSpecification(query: String, filter: UserSearchFilter?): Specification<Account> {
    var resultSpec: Specification<Account> = empty()

    if (query.isNotBlank()) {
        var querySpec = like<Account>(Account_.FULL_NAME, query)
            .or(like(Account_.USERNAME, query))
            .or(like(Account_.EMAIL, query))
            .or(like(Account_.INFO, UserInfo_.TELEGRAM, query.replace("@", "")))
            .or(like(Account_.INFO, UserInfo_.PHONE, query))

        // EVO-123 / цифры → ekomapa_user_id; RDS-V-000072 → portal Account.id
        EkomapaVolunteerCode.parseIdFromSearch(query)?.let { ekomapaId ->
            querySpec = querySpec.or(equal(Account_.EKOMAPA_USER_ID, ekomapaId))
        }
        EkomapaVolunteerCode.parsePortalVolId(query)?.let { portalId ->
            querySpec = querySpec.or(equal(Account_.ID, portalId))
        }

        resultSpec = resultSpec.and(querySpec)
    }

    filter?.let {
        var filterSpec: Specification<Account> = empty()

        it.onlyInactive?.let { onlyInactive ->
            if (onlyInactive) {
                filterSpec = filterSpec.and(equal(Account_.ACTIVE, false))
            }
        }

        it.onlyActive?.let { onlyActive ->
            if (onlyActive) {
                filterSpec = filterSpec.and(equal(Account_.ACTIVE, true))
            }
        }

        it.program?.let { program ->
            filterSpec = filterSpec.and(programEqual(program))
        }

        it.project?.let { project ->
            filterSpec = filterSpec.and(projectEqual(project))
        }

        it.reportBlocked?.let { reportBlocked ->
            if (reportBlocked) {
                filterSpec = filterSpec.and(equal(Account_.REPORT_BLOCKED, true))
            }
        }

        resultSpec = resultSpec.and(filterSpec)
    }

    return resultSpec
}

fun hasActiveRegularContract(on: LocalDate = LocalDate.now()): Specification<Account> =
    hasContractOfTypesOverlapping(on, on, setOf(ContractTypeEnum.REGULAR))

/**
 * Тепловая карта показывает и обычных волонтёров, и ассоциированных с действующим договором.
 * Обязательные часы по-прежнему считаются только по REGULAR (SQL heatmap).
 */
fun hasActiveHeatMapContract(on: LocalDate = LocalDate.now()): Specification<Account> =
    hasHeatMapContractOverlapping(on, on)

/**
 * REGULAR/ASSOCIATED договор пересекается с интервалом [from, to]
 * (startDate <= to AND endDate >= from).
 */
fun hasHeatMapContractOverlapping(from: LocalDate, to: LocalDate): Specification<Account> =
    hasContractOfTypesOverlapping(from, to, setOf(ContractTypeEnum.REGULAR, ContractTypeEnum.ASSOCIATED))

/**
 * Есть хотя бы один (не удалённый) отчёт с задачей, дата которой попадает в [from, to].
 * Используется при поиске по имени, чтобы волонтёры с отчётами не пропадали из тепловой карты
 * из‑за отсутствующего/истёкшего договора.
 */
fun hasReportWithTaskInRange(from: LocalDate, to: LocalDate): Specification<Account> =
    Specification { root, query, cb ->
        val subquery = query!!.subquery(Long::class.java)
        val report = subquery.from(Report::class.java)
        val task = report.join<Report, Task>("tasks")
        subquery.select(cb.literal(1L))
        subquery.where(
            cb.equal(report.get<Account>("account"), root),
            cb.greaterThanOrEqualTo(task.get("date"), from),
            cb.lessThanOrEqualTo(task.get("date"), to),
        )
        cb.exists(subquery)
    }

private fun hasContractOfTypesOverlapping(
    from: LocalDate,
    to: LocalDate,
    types: Set<ContractTypeEnum>,
): Specification<Account> =
    Specification { root, query, cb ->
        val subquery = query!!.subquery(Long::class.java)
        val contract = subquery.from(Contract::class.java)
        subquery.select(cb.literal(1L))
        subquery.where(
            cb.equal(contract.get<Account>("account"), root),
            contract.get<ContractTypeEnum>("type").`in`(types),
            cb.lessThanOrEqualTo(contract.get("startDate"), to),
            cb.greaterThanOrEqualTo(contract.get("endDate"), from),
        )
        cb.exists(subquery)
    }

private fun programEqual(programCode: String) = Specification { root, _, builder ->
    val infoJoin: Join<Account, UserInfo> = root.join(Account_.info, JoinType.LEFT)
    val programJoin: Join<UserInfo, Program> = infoJoin.join(UserInfo_.program, JoinType.LEFT)

    if (programCode.isBlank()) { // Выбрать пользователей без заполненной программы
        builder.isNull(programJoin.get(Program_.code))
    } else { // Выбрать пользователей с указанной программой
        builder.equal(programJoin.get(Program_.code), programCode)
    }
}

private fun projectEqual(projectCode: String) = Specification { root, _, builder ->
    val infoJoin: Join<Account, UserInfo> = root.join(Account_.info, JoinType.LEFT)
    val projectJoin: Join<UserInfo, Project> = infoJoin.join(UserInfo_.project, JoinType.LEFT)

    if (projectCode.isBlank()) { // Выбрать пользователей без заполненного проекта
        builder.isNull(projectJoin.get(Project_.code))
    } else { // Выбрать пользователей с указанным проектом
        builder.equal(projectJoin.get(Project_.code), projectCode)
    }
}
