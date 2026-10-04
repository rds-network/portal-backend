package rs.russian.portal.program.repository

import org.springframework.data.jpa.repository.JpaRepository
import rs.russian.portal.program.domain.ProgramCuratorDelegate
import java.util.UUID

interface ProgramCuratorDelegateRepository : JpaRepository<ProgramCuratorDelegate, UUID> {
    fun findAllByOrderByProgramCodeAscCuratorUsernameAscDelegateUsernameAsc(): List<ProgramCuratorDelegate>

    fun findAllByDelegateUsernameIgnoreCase(delegateUsername: String): List<ProgramCuratorDelegate>

    fun findAllByCuratorUsernameIgnoreCaseAndProgramCode(curatorUsername: String, programCode: String): List<ProgramCuratorDelegate>

    fun existsByProgramCodeAndCuratorUsernameIgnoreCaseAndDelegateUsernameIgnoreCase(
        programCode: String,
        curatorUsername: String,
        delegateUsername: String,
    ): Boolean

    fun deleteByProgramCodeAndCuratorUsernameIgnoreCaseAndDelegateUsernameIgnoreCase(
        programCode: String,
        curatorUsername: String,
        delegateUsername: String,
    )

    fun deleteByProgramCodeAndCuratorUsernameIgnoreCase(programCode: String, curatorUsername: String)
}
