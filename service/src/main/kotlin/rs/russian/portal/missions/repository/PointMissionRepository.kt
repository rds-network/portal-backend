package rs.russian.portal.missions.repository

import org.springframework.data.jpa.repository.JpaRepository
import rs.russian.portal.missions.domain.PointMission
import java.util.UUID

interface PointMissionRepository : JpaRepository<PointMission, UUID> {
    fun findByActiveTrueOrderBySortOrderAscCreatedAtAsc(): List<PointMission>

    fun findAllByOrderBySortOrderAscCreatedAtAsc(): List<PointMission>
}
