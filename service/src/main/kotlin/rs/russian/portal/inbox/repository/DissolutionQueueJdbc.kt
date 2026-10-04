package rs.russian.portal.inbox.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import rs.russian.portal.inbox.api.DissolutionQueueDto

@Repository
class DissolutionQueueJdbc(
    private val jdbc: JdbcTemplate,
) {

    fun findQueued(): List<DissolutionQueueDto> =
        jdbc.query(SQL) { rs, _ ->
            DissolutionQueueDto(
                accountId = rs.getInt("account_id"),
                username = rs.getString("username"),
                fullName = rs.getString("full_name"),
                program = rs.getString("program"),
                contractEnd = rs.getDate("contract_end")?.toLocalDate(),
                contractType = rs.getString("contract_type"),
                active = rs.getBoolean("active"),
                dissolutionQueuedAt = rs.getObject("dissolution_queued_at", java.time.OffsetDateTime::class.java),
                dissolutionQueuedBy = rs.getString("dissolution_queued_by"),
                dissolutionQueueReason = rs.getString("dissolution_queue_reason"),
            )
        }

    companion object {
        private const val SQL = """
        SELECT
          a.id AS account_id,
          a.username,
          a.full_name,
          a.active,
          ui.program_code AS program,
          c.end_date AS contract_end,
          c.type AS contract_type,
          a.dissolution_queued_at,
          a.dissolution_queued_by,
          a.dissolution_queue_reason
        FROM account a
        LEFT JOIN user_info ui ON ui.username = a.username
        LEFT JOIN LATERAL (
          SELECT ct.end_date, ct.type::text AS type
          FROM contract ct
          WHERE ct.username = a.username
            AND ct.type IN ('REGULAR', 'ASSOCIATED')
            AND ct.end_date >= CURRENT_DATE
          ORDER BY
            CASE WHEN ct.type::text = 'REGULAR' THEN 0 ELSE 1 END,
            ct.end_date DESC
          LIMIT 1
        ) c ON true
        WHERE a.dissolution_queued_at IS NOT NULL
          AND a.mup_letter_sent_at IS NULL
        ORDER BY a.dissolution_queued_at ASC, a.full_name
        """
    }
}
