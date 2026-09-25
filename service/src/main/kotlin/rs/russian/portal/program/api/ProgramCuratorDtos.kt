package rs.russian.portal.program.api

data class ProgramCuratorDto(
    val programCode: String,
    val programNameRu: String,
    val programNameEn: String,
    val programNameSr: String,
    val username: String,
    val fullName: String,
)

data class ProgramCuratorWriteRequest(
    val programCode: String,
    val username: String,
)
