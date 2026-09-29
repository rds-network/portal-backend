package rs.russian.portal.talent.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rs.russian.portal.talent.domain.TalentPost
import rs.russian.portal.talent.domain.enums.TalentPostStatus
import rs.russian.portal.talent.domain.enums.TalentPostType
import java.util.Optional
import java.util.UUID

interface TalentPostRepository : JpaRepository<TalentPost, UUID> {

    @Query(
        """
        SELECT p FROM TalentPost p
        LEFT JOIN FETCH p.program
        WHERE p.id = :id
        """
    )
    fun findByIdWithProgram(@Param("id") id: UUID): Optional<TalentPost>

    @Query(
        value = """
        SELECT p FROM TalentPost p
        LEFT JOIN p.program prog
        WHERE (:type IS NULL OR p.type = :type)
          AND (:status IS NULL OR p.status = :status)
          AND (
            :city IS NULL OR
            LOWER(COALESCE(p.city, '')) LIKE LOWER(CONCAT('%', :city, '%'))
          )
          AND (:programCode IS NULL OR LOWER(prog.code) = LOWER(:programCode))
          AND (
            :q IS NULL OR
            LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(p.body) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(COALESCE(p.skills, '')) LIKE LOWER(CONCAT('%', :q, '%'))
          )
        """,
        countQuery = """
        SELECT COUNT(p) FROM TalentPost p
        LEFT JOIN p.program prog
        WHERE (:type IS NULL OR p.type = :type)
          AND (:status IS NULL OR p.status = :status)
          AND (
            :city IS NULL OR
            LOWER(COALESCE(p.city, '')) LIKE LOWER(CONCAT('%', :city, '%'))
          )
          AND (:programCode IS NULL OR LOWER(prog.code) = LOWER(:programCode))
          AND (
            :q IS NULL OR
            LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(p.body) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(COALESCE(p.skills, '')) LIKE LOWER(CONCAT('%', :q, '%'))
          )
        """,
    )
    fun search(
        @Param("type") type: TalentPostType?,
        @Param("status") status: TalentPostStatus?,
        @Param("q") q: String?,
        @Param("city") city: String?,
        @Param("programCode") programCode: String?,
        pageable: Pageable,
    ): Page<TalentPost>
}
