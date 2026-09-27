package rs.russian.portal.user.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import rs.russian.portal.user.domain.UserSecondaryProgram
import java.util.UUID

@Repository
interface UserSecondaryProgramRepository : JpaRepository<UserSecondaryProgram, UUID> {

    fun findAllByAccountIdOrderByProgramCodeAsc(accountId: Int): List<UserSecondaryProgram>

    fun deleteAllByAccountId(accountId: Int)

    @Modifying
    @Query(
        """
        DELETE FROM UserSecondaryProgram usp
        WHERE usp.accountId = :accountId
          AND LOWER(usp.programCode) = LOWER(:programCode)
        """
    )
    fun deleteByAccountIdAndProgramCodeIgnoreCase(
        @Param("accountId") accountId: Int,
        @Param("programCode") programCode: String,
    ): Int
}
