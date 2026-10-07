package rs.russian.portal.report.domain

import jakarta.persistence.*
import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.FetchType.LAZY
import org.hibernate.annotations.SQLRestriction
import rs.russian.portal.note.domain.Note
import rs.russian.portal.program.domain.Program
import rs.russian.portal.program.domain.Project
import rs.russian.portal.report.domain.enums.ReportStatus
import rs.russian.portal.report.domain.listener.ReportEntityListener
import rs.russian.portal.shared.jpa.JpaEntity
import rs.russian.portal.user.domain.Account
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

@Entity
@EntityListeners(ReportEntityListener::class)
@SQLRestriction("deleted = false")
@NamedEntityGraph(
    name = Report.GRAPH_FULL,
    attributeNodes = [
        NamedAttributeNode("tasks", subgraph = Task.GRAPH_FULL),
        NamedAttributeNode("account", subgraph = Account.GRAPH_USERNAME),
        NamedAttributeNode("moderator", subgraph = Account.GRAPH_USERNAME),
        NamedAttributeNode("notes"),
        NamedAttributeNode("program"),
        NamedAttributeNode("project")],
    subgraphs = [
        NamedSubgraph(
            name = Account.GRAPH_USERNAME,
            attributeNodes = [NamedAttributeNode("username")]
        ),
        NamedSubgraph(
            name = Task.GRAPH_FULL,
            attributeNodes = [
                NamedAttributeNode("customer"),
                NamedAttributeNode("files")
            ]
        )
    ]
)
class Report(
    @Id
    override var id: UUID? = UUID.randomUUID(),
    override var version: LocalDateTime? = null,

    var createTime: OffsetDateTime = OffsetDateTime.now(),

    /** Last time the volunteer submitted / resubmitted the report. */
    var submittedAt: OffsetDateTime = OffsetDateTime.now(),

    /** Set when status becomes ACCEPTED; cleared when acceptance is cancelled. */
    var acceptedAt: OffsetDateTime? = null,

    @Enumerated(STRING)
    var status: ReportStatus = ReportStatus.CREATED,

    @OneToMany(mappedBy = "report", cascade = [ALL], orphanRemoval = true)
    var tasks: MutableSet<Task> = mutableSetOf(),

    @OneToMany(mappedBy = "report", cascade = [ALL], orphanRemoval = true)
    var customerDecisions: MutableSet<ReportCustomerDecision> = mutableSetOf(),

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "user_login", referencedColumnName = "username")
    var account: Account,

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "program_code")
    var program: Program? = null,

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "project_code")
    var project: Project? = null,

    @SQLRestriction("entity_type = 'REPORT'")
    @OneToMany(mappedBy = "entityId", cascade = [ALL], orphanRemoval = true)
    var notes: MutableSet<Note> = mutableSetOf(),

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "moderator", referencedColumnName = "username")
    var moderator: Account? = null,

    var isAuto: Boolean = false,

    var deleted: Boolean = false,

    ) : JpaEntity<UUID>() {

    override fun equalityProperties() = setOf(Report::id, Report::status, Report::deleted)

    companion object {
        const val GRAPH_FULL = "Report.Full"
    }
}
