package rs.russian.portal.application.mapper

import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget
import org.mapstruct.Named
import org.mapstruct.NullValuePropertyMappingStrategy.IGNORE
import org.springframework.beans.factory.annotation.Autowired
import rs.russian.generated.model.ApplicationDto
import rs.russian.generated.model.ApplicationStatusDto
import rs.russian.generated.model.ContractDto
import rs.russian.portal.application.domain.Application
import rs.russian.portal.application.domain.ApplicationStatus
import rs.russian.portal.note.mapper.NoteMapper
import rs.russian.portal.program.domain.Program
import rs.russian.portal.program.domain.Project
import rs.russian.portal.program.repository.ProgramRepository
import rs.russian.portal.program.repository.ProjectRepository
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.UserInfo
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

@Mapper(
    imports = [UUID::class], nullValuePropertyMappingStrategy = IGNORE,
    uses = [NoteMapper::class]
)
abstract class ApplicationMapper {

    @Autowired
    private lateinit var noteMapper: NoteMapper

    @Autowired
    private lateinit var programRepository: ProgramRepository

    @Autowired
    private lateinit var projectRepository: ProjectRepository

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "notes", ignore = true)
    @Mapping(target = "assignee", ignore = true)
    @Mapping(target = "refuseReason", ignore = true)
    @Mapping(target = "contractFrom", source = "contract.startDate")
    @Mapping(target = "contractUntil", source = "contract.endDate")
    @Mapping(target = "contractType", source = "contract.type")
    @Mapping(target = "contractDocumentDate", ignore = true)
    @Mapping(target = "program", source = "program", qualifiedByName = ["programByCode"])
    @Mapping(target = "project", source = "project", qualifiedByName = ["projectByCode"])
    abstract fun toEntity(applicationDto: ApplicationDto, @MappingTarget application: Application)

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "notes", ignore = true)
    @Mapping(target = "assignee", ignore = true)
    @Mapping(target = "contractFrom", source = "contract.startDate")
    @Mapping(target = "contractUntil", source = "contract.endDate")
    @Mapping(target = "contractType", source = "contract.type")
    @Mapping(target = "contractDocumentDate", ignore = true)
    @Mapping(target = "program", source = "program", qualifiedByName = ["programByCode"])
    @Mapping(target = "project", source = "project", qualifiedByName = ["projectByCode"])
    abstract fun update(applicationDto: ApplicationDto, @MappingTarget application: Application)

    @Mapping(target = "contract", source = "application", qualifiedByName = ["contract"])
    @Mapping(target = "program", source = "program.code")
    @Mapping(target = "project", source = "project.code")
    @Mapping(target = "lastUpdate", source = "version")
    abstract fun toDto(application: Application): ApplicationDto

    @Mapping(target = "progress", source = "status")
    @Mapping(target = "terminated", source = "status")
    @Mapping(target = "lastUpdate", source = "version")
    abstract fun mapStatus(application: Application): ApplicationStatusDto

    @Mapping(target = "account", source = "account")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "city", source = "application.city")
    @Mapping(target = "postalCode", source = "application.postalCode")
    @Mapping(target = "program", source = "application", qualifiedByName = ["programForUserInfo"])
    @Mapping(target = "project", source = "application.project")
    @Mapping(target = "gender", source = "application.gender")
    @Mapping(target = "avatar", ignore = true)
    abstract fun mapToInfo(application: Application, account: Account): UserInfo

    fun mapProgress(status: ApplicationStatus): Int = status.progress

    fun mapTerminated(status: ApplicationStatus): Boolean = status.terminated

    fun map(value: LocalDateTime): OffsetDateTime {
        return OffsetDateTime.of(value, ZoneOffset.UTC)
    }

    @Named("programByCode")
    fun mapProgram(code: String?): Program? = code?.takeIf { it.isNotBlank() }?.let(programRepository::findByCode)

    @Named("projectByCode")
    fun mapProject(code: String?): Project? = code?.takeIf { it.isNotBlank() }?.let(projectRepository::findByCode)

    @Named("programForUserInfo")
    fun mapProgram(application: Application): Program? = application.project?.program ?: application.program

    @Named("contract")
    fun mapContract(application: Application): ContractDto? {
        if (application.contractFrom == null || application.contractUntil == null || application.contractType == null) {
            return null
        }
        return ContractDto(
            id = UUID.randomUUID(),
            startDate = application.contractFrom!!,
            endDate = application.contractUntil!!,
            type = application.contractType!!
        )
    }
}
