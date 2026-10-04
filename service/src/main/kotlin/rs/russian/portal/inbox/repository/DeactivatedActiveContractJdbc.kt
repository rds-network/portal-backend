package rs.russian.portal.inbox.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import rs.russian.portal.inbox.api.DeactivatedActiveContractDto

@Repository
class DeactivatedActiveContractJdbc(
    private val jdbc: JdbcTemplate,
) {

    fun findDeactivatedWithActiveContract(): List<DeactivatedActiveContractDto> =
        jdbc.query(SQL) { rs, _ ->
            DeactivatedActiveContractDto(
                accountId = rs.getInt("account_id"),
                username = rs.getString("username"),
                fullName = rs.getString("full_name"),
                program = rs.getString("program"),
                contractEnd = rs.getDate("contract_end")?.toLocalDate(),
                contractType = rs.getString("contract_type"),
                deactivatedReason = rs.getString("deactivated_reason"),
                mupLetterSentAt = rs.getObject("mup_letter_sent_at", java.time.OffsetDateTime::class.java),
            )
        }

    companion object {
        private const val SQL = """
        SELECT
          a.id AS account_id,
          a.username,
          a.full_name,
          ui.program_code AS program,
          c.end_date AS contract_end,
          c.type AS contract_type,
          NULL::text AS deactivated_reason,
          a.mup_letter_sent_at
        FROM account a
        LEFT JOIN user_info ui ON ui.username = a.username
        JOIN LATERAL (
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
        WHERE a.active = false
          AND a.dissolution_queued_at IS NULL
        ORDER BY c.end_date ASC, a.full_name
        """
    }
}
