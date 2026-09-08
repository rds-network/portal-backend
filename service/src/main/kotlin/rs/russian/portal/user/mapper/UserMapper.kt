package rs.russian.portal.user.mapper

import io.authentik.model.User
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget
import org.mapstruct.Named
import org.mapstruct.NullValuePropertyMappingStrategy.IGNORE
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.core.oidc.OidcUserInfo
import rs.russian.generated.model.ContractDto
import rs.russian.generated.model.UserInfoDto
import rs.russian.generated.model.UserInfoUpdateRequest
import rs.russian.portal.file.mapper.FileInfoMapper
import rs.russian.portal.program.mapper.ProgramMapper
import rs.russian.portal.program.mapper.ProjectMapper
import rs.russian.portal.shared.security.userGroups
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.Contract
import rs.russian.portal.user.domain.UserInfo
import rs.russian.portal.user.domain.enums.UserGroup
import java.time.LocalDateTime
import java.util.*

@Mapper(
    imports = [HashSet::class, LocalDateTime::class, UUID::class],
    uses = [FileInfoMapper::class, ProgramMapper::class, ProjectMapper::class, ContractMapper::class, ResidencePermitMapper::class]
)
abstract class UserMapper {

    @Autowired
    private lateinit var fileInfoMapper: FileInfoMapper

    @Autowired
    private lateinit var contractMapper: ContractMapper

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "info", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "depersonalizationStatus", constant = "NONE")
    @Mapping(target = "depersonalizedAt", ignore = true)
    @Mapping(target = "username", source = "nickName")
    @Mapping(target = "contracts", expression = "java(new HashSet<>())")
    @Mapping(target = "residencePermits", expression = "java(new HashSet<>())")
    @Mapping(target = "lastSynced", expression = "java(LocalDateTime.now())")
    @Mapping(target = "fullName", source = "oidcUserInfo", qualifiedByName = ["nameOidc"])
    @Mapping(target = "groups", source = "oidcUserInfo", qualifiedByName = ["mapGroups"])
    abstract fun map(oidcUserInfo: OidcUserInfo): Account

    @Mapping(target = "id", source = "pk")
    @Mapping(target = "info", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "depersonalizationStatus", constant = "NONE")
    @Mapping(target = "depersonalizedAt", ignore = true)
    @Mapping(target = "contracts", expression = "java(new HashSet<>())")
    @Mapping(target = "residencePermits", expression = "java(new HashSet<>())")
    @Mapping(target = "fullName", source = "ssoUser", qualifiedByName = ["nameSso"])
    @Mapping(target = "lastSynced", expression = "java(LocalDateTime.now())")
    @Mapping(target = "groups", source = "groupsObj", qualifiedByName = ["mapGroupsSso"])
    abstract fun map(ssoUser: User): Account

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "info", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "contracts", ignore = true)
    @Mapping(target = "residencePermits", ignore = true)
    @Mapping(target = "depersonalizationStatus", ignore = true)
    @Mapping(target = "depersonalizedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "username", source = "nickName")
    @Mapping(target = "lastSynced", expression = "java(LocalDateTime.now())")
    @Mapping(target = "fullName", source = "oidcUserInfo", qualifiedByName = ["nameOidc"])
    @Mapping(target = "groups", source = "oidcUserInfo", qualifiedByName = ["mapGroups"])
    abstract fun update(oidcUserInfo: OidcUserInfo, @MappingTarget account: Account)

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "info", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "contracts", ignore = true)
    @Mapping(target = "residencePermits", ignore = true)
    @Mapping(target = "depersonalizationStatus", ignore = true)
    @Mapping(target = "depersonalizedAt", ignore = true)
    @Mapping(target = "lastSynced", expression = "java(LocalDateTime.now())")
    @Mapping(target = "fullName", source = "ssoUser", qualifiedByName = ["nameSso"])
    @Mapping(target = "groups", source = "groupsObj", qualifiedByName = ["mapGroupsSso"])
    abstract fun update(ssoUser: User, @MappingTarget account: Account)

    @Mapping(target = "id", source = "account.id")
    @Mapping(target = "email", source = "account.email")
    @Mapping(target = "username", source = "account.username")
    @Mapping(target = "fullName", source = "account.fullName")
    @Mapping(target = "groups", source = "account.groups")
    @Mapping(target = "active", source = "account.active")
    @Mapping(target = "contracts", source = "account.contracts")
    @Mapping(target = "residencePermits", source = "account.residencePermits")
    abstract fun map(userInfo: UserInfo?): UserInfoDto

    @Mapping(target = "account", source = "account")
    @Mapping(target = "id", expression = "java(UUID.randomUUID())")
    abstract fun map(contactDto: ContractDto, account: Account): Contract

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "program", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "gender", nullValuePropertyMappingStrategy = IGNORE)
    @Mapping(target = "city", nullValuePropertyMappingStrategy = IGNORE)
    @Mapping(target = "postalCode", nullValuePropertyMappingStrategy = IGNORE)
    @Mapping(target = "address", nullValuePropertyMappingStrategy = IGNORE)
    @Mapping(target = "birthDate", nullValuePropertyMappingStrategy = IGNORE)
    @Mapping(target = "telegram", nullValuePropertyMappingStrategy = IGNORE)
    @Mapping(target = "phone", nullValuePropertyMappingStrategy = IGNORE)
    abstract fun updateInfo(@MappingTarget target: UserInfo, source: UserInfoUpdateRequest)

    @Named("mapGroups")
    fun mapGroups(oidcUserInfo: OidcUserInfo): Set<UserGroup> {
        return oidcUserInfo.userGroups()
    }

    @Named("mapGroupsSso")
    fun mapGroupsSso(groupsSso: MutableList<io.authentik.model.UserGroup>?): Set<UserGroup> {
        if (groupsSso.isNullOrEmpty()) {
            return mutableSetOf()
        }
        val groups = mutableSetOf<UserGroup>()
        groupsSso.forEach { groupSso ->
            UserGroup.of(groupSso.name)?.let {
                groups.add(it)
            }
        }
        return groups
    }

    @Named("nameSso")
    fun name(ssoUser: User): String {
        return ssoUser.name.ifBlank {
            ssoUser.username
        }
    }

    @Named("nameOidc")
    fun name(oidcUserInfo: OidcUserInfo): String {
        return if (oidcUserInfo.fullName.isNullOrBlank()) {
            oidcUserInfo.nickName
        } else {
            oidcUserInfo.fullName
        }
    }
}
