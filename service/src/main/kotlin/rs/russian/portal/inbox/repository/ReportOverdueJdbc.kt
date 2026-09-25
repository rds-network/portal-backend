package rs.russian.portal.inbox.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import rs.russian.portal.inbox.api.ReportOverdueDto
import rs.russian.portal.inbox.domain.InboxThread

@Repository
class ReportOverdueJdbc(
    private val jdbc: JdbcTemplate,
) {

    fun findOverdue(): List<ReportOverdueDto> =
        jdbc.query(SQL) { rs, _ ->
            val weeks = rs.getInt("weeks_missed")
            ReportOverdueDto(
                username = rs.getString("username"),
                fullName = rs.getString("full_name"),
                program = rs.getString("program"),
                weeksMissed = weeks,
                level = if (weeks >= 3) InboxThread.KIND_OVERDUE_3 else InboxThread.KIND_OVERDUE_2,
                lastReportWeek = rs.getDate("last_report_week")?.toLocalDate(),
            )
        }

    companion object {
        private const val SQL = """
        WITH last_weeks AS (
          SELECT
            (date_trunc('week', CURRENT_DATE)::date - 7) AS week1,
            (date_trunc('week', CURRENT_DATE)::date - 14) AS week2,
            (date_trunc('week', CURRENT_DATE)::date - 21) AS week3
        ),
        contracted AS (
          SELECT a.username, a.full_name, ui.program_code AS program
          FROM account a
          LEFT JOIN user_info ui ON ui.username = a.username
          WHERE a.active = true
            AND EXISTS (
              SELECT 1 FROM contract c
              CROSS JOIN last_weeks w
              WHERE c.username = a.username
                AND c.type = 'REGULAR'
                AND c.start_date <= (w.week1 + 6)
                AND c.end_date >= w.week1
            )
        ),
        week_report AS (
          SELECT r.user_login AS username, date_trunc('week', t.date)::date AS week_start
          FROM report r
          JOIN task t ON t.report_id = r.id
          CROSS JOIN last_weeks w
          WHERE r.deleted = FALSE
            AND r.status = 'ACCEPTED'
            AND date_trunc('week', t.date)::date IN (w.week1, w.week2, w.week3)
          GROUP BY r.user_login, date_trunc('week', t.date)::date
        ),
        last_accepted AS (
          SELECT r.user_login AS username, MAX(date_trunc('week', t.date)::date) AS last_report_week
          FROM report r
          JOIN task t ON t.report_id = r.id
          WHERE r.deleted = FALSE AND r.status = 'ACCEPTED'
          GROUP BY r.user_login
        )
        SELECT
          c.username,
          c.full_name,
          c.program,
          CASE
            WHEN NOT EXISTS (SELECT 1 FROM week_report wr, last_weeks w WHERE wr.username = c.username AND wr.week_start = w.week1)
             AND NOT EXISTS (SELECT 1 FROM week_report wr, last_weeks w WHERE wr.username = c.username AND wr.week_start = w.week2)
             AND EXISTS (
               SELECT 1 FROM contract ct, last_weeks w
               WHERE ct.username = c.username AND ct.type = 'REGULAR'
                 AND ct.start_date <= (w.week2 + 6) AND ct.end_date >= w.week2
             )
             AND NOT EXISTS (SELECT 1 FROM week_report wr, last_weeks w WHERE wr.username = c.username AND wr.week_start = w.week3)
             AND EXISTS (
               SELECT 1 FROM contract ct, last_weeks w
               WHERE ct.username = c.username AND ct.type = 'REGULAR'
                 AND ct.start_date <= (w.week3 + 6) AND ct.end_date >= w.week3
             )
            THEN 3
            ELSE 2
          END AS weeks_missed,
          la.last_report_week
        FROM contracted c
        LEFT JOIN last_accepted la ON la.username = c.username
        CROSS JOIN last_weeks w
        WHERE NOT EXISTS (SELECT 1 FROM week_report wr WHERE wr.username = c.username AND wr.week_start = w.week1)
          AND NOT EXISTS (SELECT 1 FROM week_report wr WHERE wr.username = c.username AND wr.week_start = w.week2)
          AND EXISTS (
            SELECT 1 FROM contract ct
            WHERE ct.username = c.username AND ct.type = 'REGULAR'
              AND ct.start_date <= (w.week2 + 6) AND ct.end_date >= w.week2
          )
        ORDER BY weeks_missed DESC, c.full_name
        """
    }
}
