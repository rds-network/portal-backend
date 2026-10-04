package rs.russian.portal.program.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import rs.russian.portal.program.domain.ProgramCurator
import java.util.UUID

@Repository
interface ProgramCuratorRepository : JpaRepository<ProgramCurator, UUID> {
    fun findAllByOrderByProgramCodeAscUsernameAsc(): List<ProgramCurator>
    fun findAllByUsernameIgnoreCase(username: String): List<ProgramCurator>
    fun findAllByProgramCodeIgnoreCase(programCode: String): List<ProgramCurator>
    fun existsByUsernameIgnoreCase(username: String): Boolean
    fun existsByProgramCodeAndUsernameIgnoreCase(programCode: String, username: String): Boolean
    fun deleteByProgramCodeAndUsernameIgnoreCase(programCode: String, username: String)
}
