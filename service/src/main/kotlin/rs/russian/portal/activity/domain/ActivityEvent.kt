package rs.russian.portal.activity.domain

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "activity_event")
class ActivityEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    var createTime: LocalDateTime = LocalDateTime.now(),

    var username: String? = null,

    var ip: String? = null,

    var method: String,

    var path: String,

    var query: String? = null,

    var action: String,
)
