package rs.russian.portal.report.api

data class ReportCustomerAcceptanceDto(
    val customer: String,
    val customerName: String? = null,
    val status: String? = null,
    val decidedBy: String? = null,
    val decidedAt: String? = null,
)

data class ReportCustomerAcceptancesResponse(
    val reportId: String,
    val multiCustomer: Boolean,
    val pendingForMe: Boolean,
    val acceptances: List<ReportCustomerAcceptanceDto>,
)
