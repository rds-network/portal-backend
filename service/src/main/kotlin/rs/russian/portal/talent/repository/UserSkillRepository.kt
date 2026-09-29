package rs.russian.portal.talent.repository

import org.springframework.data.jpa.repository.JpaRepository
import rs.russian.portal.talent.domain.UserSkill
import java.util.UUID

interface UserSkillRepository : JpaRepository<UserSkill, UUID> {
    fun findAllByAccountIdOrderBySkillAsc(accountId: Int): List<UserSkill>

    fun deleteAllByAccountId(accountId: Int)
}
