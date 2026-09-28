package rs.russian.portal.applicationjoin.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "portal_application_join_settings")
class PortalApplicationJoinSettings(
    @Id
    @Column(name = "id")
    var id: Int = 1,

    @Column(name = "title", nullable = false, length = 500)
    var title: String = "",

    @Column(name = "body", nullable = false, columnDefinition = "text")
    var body: String = "",

    @Column(name = "agree1_label", nullable = false, columnDefinition = "text")
    var agree1Label: String = "",

    @Column(name = "agree2_label", nullable = false, columnDefinition = "text")
    var agree2Label: String = "",

    @Column(name = "button_label", nullable = false, length = 200)
    var buttonLabel: String = "",

    @Column(name = "updated_at")
    var updatedAt: Instant? = null,
)
