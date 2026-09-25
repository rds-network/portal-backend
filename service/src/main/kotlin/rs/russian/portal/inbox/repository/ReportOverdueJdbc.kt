package rs.russian.portal.inbox.repository

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import rs.russian.portal.inbox.api.OverdueWeekDto
import rs.russian.portal.inbox.api.ReportOverdueDto
import rs.russian.portal.inbox.domain.InboxThread

@Repository
class ReportOverdueJdbc(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
) {

    fun findOverdue(): List<ReportOverdueDto> =
        jdbc.query(SQL) { rs, _ ->
            val weeks = rs.getInt("weeks_missed")
            val hoursShort = rs.getInt("hours_short")
            ReportOverdueDto(
                username = rs.getString("username"),
                fullName = rs.getString("full_name"),
                program = rs.getString("program"),
                weeksMissed = weeks,
                hoursShort = hoursShort,
                hoursWorked = rs.getInt("hours_worked"),
                hoursRequired = rs.getInt("hours_required"),
                contractEnd = rs.getDate("contract_end")?.toLocalDate(),
                recentWeeks = parseWeeks(rs.getString("weeks_json")),
                level = when {
                    weeks >= 3 -> InboxThread.KIND_OVERDUE_3
                    weeks >= 2 -> InboxThread.KIND_OVERDUE_2
                    weeks >= 1 -> InboxThread.KIND_OVERDUE_1
                    else -> InboxThread.KIND_OVERDUE_HOURS
                },
                lastReportWeek = rs.getDate("last_report_week")?.toLocalDate(),
            )
        }

    private fun parseWeeks(raw: String?): List<OverdueWeekDto> {
        if (raw.isNullOrBlank()) return emptyList()
        return mapper.readValue(raw, WEEKS_TYPE)
    }

    companion object {
        private val WEEKS_TYPE = object : TypeReference<List<OverdueWeekDto>>() {}

        private const val SQL = """
        WITH bounds AS (
          SELECT
            CURRENT_DATE AS today,
            date_trunc('week', CURRENT_DATE)::date AS this_monday,
            (date_trunc('week', CURRENT_DATE)::date - 7) AS last_monday
        ),
        contracted AS (
          SELECT
            a.username,
            a.full_name,
            ui.program_code AS program,
            (
              SELECT MAX(ct.end_date)
              FROM contract ct
              WHERE ct.username = a.username
                AND ct.type = 'REGULAR'
            ) AS contract_end
          FROM account a
          LEFT JOIN user_info ui ON ui.username = a.username
          CROSS JOIN bounds b
          WHERE a.active = true
            AND EXISTS (
              SELECT 1 FROM contract c
              WHERE c.username = a.username
                AND c.type = 'REGULAR'
                AND c.start_date <= (b.last_monday + 6)
                AND c.end_date >= b.last_monday
            )
        ),
        weeks AS (
          SELECT
            gs::date AS week_start,
            LEAST((gs::date + 6), b.today)::date AS week_end
          FROM bounds b,
               generate_series(
                 b.last_monday - interval '4 weeks',
                 b.last_monday,
                 interval '1 week'
               ) gs
        ),
        week_hours AS (
          SELECT
            c.username,
            w.week_start,
            COALESCE((
              SELECT SUM(t.time_spent)
              FROM report r
              JOIN task t ON t.report_id = r.id
              WHERE r.user_login = c.username
                AND r.deleted = FALSE
                AND r.status = 'ACCEPTED'
                AND date_trunc('week', t.date)::date = w.week_start
            ), 0) AS minutes_worked,
            (
              SELECT COALESCE(SUM(
                GREATEST(0, (LEAST(w.week_end, ct.end_date) - GREATEST(w.week_start, ct.start_date)) + 1)
              ), 0)
              FROM contract ct
              WHERE ct.username = c.username
                AND ct.type = 'REGULAR'
                AND ct.start_date <= w.week_end
                AND ct.end_date >= w.week_start
            ) AS active_days
          FROM contracted c
          CROSS JOIN weeks w
        ),
        totals AS (
          SELECT
            username,
            ROUND(SUM(minutes_worked) / 60.0)::int AS hours_worked,
            SUM(ROUND((active_days::numeric / 7.0) * 10.0))::int AS hours_required,
            json_agg(
              json_build_object(
                'weekStart', week_start,
                'hoursWorked', ROUND((minutes_worked / 60.0)::numeric, 1),
                'hoursRequired', ROUND((active_days::numeric / 7.0) * 10.0)::int
              ) ORDER BY week_start
            ) AS weeks_json
          FROM week_hours
          GROUP BY username
        ),
        week_report AS (
          SELECT r.user_login AS username, date_trunc('week', t.date)::date AS week_start
          FROM report r
          JOIN task t ON t.report_id = r.id
          WHERE r.deleted = FALSE AND r.status = 'ACCEPTED'
          GROUP BY r.user_login, date_trunc('week', t.date)::date
        ),
        streak AS (
          SELECT
            c.username,
            CASE
              WHEN EXISTS (
                SELECT 1 FROM week_report wr, bounds b
                WHERE wr.username = c.username AND wr.week_start = b.last_monday
              ) THEN 0
              WHEN EXISTS (
                SELECT 1 FROM week_report wr, bounds b
                WHERE wr.username = c.username AND wr.week_start = (b.last_monday - 7)
              ) THEN 1
              WHEN EXISTS (
                SELECT 1 FROM week_report wr, bounds b
                WHERE wr.username = c.username AND wr.week_start = (b.last_monday - 14)
              ) THEN 2
              ELSE 3
            END AS weeks_missed
          FROM contracted c
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
          c.contract_end,
          s.weeks_missed,
          GREATEST(COALESCE(t.hours_required, 0) - COALESCE(t.hours_worked, 0), 0) AS hours_short,
          COALESCE(t.hours_worked, 0) AS hours_worked,
          COALESCE(t.hours_required, 0) AS hours_required,
          t.weeks_json,
          la.last_report_week
        FROM contracted c
        JOIN streak s ON s.username = c.username
        LEFT JOIN totals t ON t.username = c.username
        LEFT JOIN last_accepted la ON la.username = c.username
        WHERE (COALESCE(t.hours_required, 0) - COALESCE(t.hours_worked, 0)) > 0
          AND (
            s.weeks_missed >= 1
            OR (COALESCE(t.hours_required, 0) - COALESCE(t.hours_worked, 0)) >= 20
          )
        ORDER BY s.weeks_missed DESC, hours_short DESC, c.full_name
        """
    }
}
