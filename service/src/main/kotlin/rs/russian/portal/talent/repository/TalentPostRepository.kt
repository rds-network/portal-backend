package rs.russian.portal.talent.repository

import jakarta.persistence.criteria.JoinType
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rs.russian.portal.talent.domain.TalentPost
import rs.russian.portal.talent.domain.enums.TalentPostStatus
import rs.russian.portal.talent.domain.enums.TalentPostType
import java.util.Optional
import java.util.UUID

interface TalentPostRepository : JpaRepository<TalentPost, UUID>, JpaSpecificationExecutor<TalentPost> {

    @Query(
        """
        SELECT p FROM TalentPost p
        LEFT JOIN FETCH p.program
        WHERE p.id = :id
        """
    )
    fun findByIdWithProgram(@Param("id") id: UUID): Optional<TalentPost>
}

object TalentPostSpecs {
    fun search(
        type: TalentPostType?,
        status: TalentPostStatus?,
        q: String?,
        city: String?,
        programCode: String?,
    ): Specification<TalentPost> =
        Specification { root, query, cb ->
            // Avoid duplicate rows when joining program for filters/sort
            if (query?.resultType != Long::class.java && query?.resultType != java.lang.Long.TYPE) {
                query?.distinct(true)
            }
            val preds = mutableListOf<jakarta.persistence.criteria.Predicate>()
            if (status != null) {
                preds += cb.equal(root.get<TalentPostStatus>("status"), status)
            }
            if (type != null) {
                preds += cb.equal(root.get<TalentPostType>("type"), type)
            }
            if (!city.isNullOrBlank()) {
                preds += cb.like(cb.lower(cb.coalesce(root.get("city"), "")), "%${city.trim().lowercase()}%")
            }
            if (!programCode.isNullOrBlank()) {
                val programJoin = root.join<Any, Any>("program", JoinType.LEFT)
                preds += cb.equal(cb.lower(programJoin.get("code")), programCode.trim().lowercase())
            }
            if (!q.isNullOrBlank()) {
                val needle = "%${q.trim().lowercase()}%"
                preds += cb.or(
                    cb.like(cb.lower(root.get("title")), needle),
                    cb.like(cb.lower(root.get("body")), needle),
                    cb.like(cb.lower(cb.coalesce(root.get("skills"), "")), needle),
                )
            }
            cb.and(*preds.toTypedArray())
        }
}
