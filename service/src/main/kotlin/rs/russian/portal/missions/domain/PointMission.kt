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

    /** PICTOGRAM | LOGO | COVER */
    @Column(name = "visual_type", nullable = false, length = 20)
    var visualType: String = "PICTOGRAM",

    /** Pictogram id from the shared set, when visualType = PICTOGRAM. */
    @Column(name = "visual_key", length = 40)
    var visualKey: String? = null,

    /** Image URL for LOGO (circle) or COVER (rectangle). */
    @Column(name = "image_url", length = 1024)
    var imageUrl: String? = null,

    @Column(name = "created_by")
    var createdBy: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)
