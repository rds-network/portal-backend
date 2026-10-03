package rs.russian.portal.missions.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "point_mission")
class PointMission(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 200)
    var title: String,

    @Column(length = 1000)
    var description: String? = null,

    @Column(nullable = false)
    var points: Int,

    @Column(length = 1000)
    var link: String? = null,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "one_time", nullable = false)
    var oneTime: Boolean = true,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "created_by")
    var createdBy: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)
