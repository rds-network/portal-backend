package rs.russian.portal.report.api

/** Полная замена назначения отчёта: `null` очищает поле. */
data class ReportAssignmentRequest(
    val programCode: String? = null,
    val projectCode: String? = null,
)
