package rs.russian.portal.impersonate.api

data class ImpersonationStartRequest(
    val username: String = "",
)

data class ImpersonationStatusDto(
    val active: Boolean,
    val canImpersonate: Boolean,
    val targetUsername: String? = null,
    val targetFullName: String? = null,
    val realUsername: String? = null,
    val realFullName: String? = null,
)
