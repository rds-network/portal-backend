package rs.russian.portal.inbox.repository

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import rs.russian.portal.inbox.repository.projections.ReportOverdueProjection
import rs.russian.portal.user.domain.Account
import org.springframework.stereotype.Repository as StereotypeRepository

@StereotypeRepository
interface ReportOverdueRepository : Repository<Account, Int> {

    @Query(
        value = """
        WITH last_weeks AS (
          SELECT
            date_trunc('week', CURRENT_DATE)::date - 7 AS week1,
            date_trunc('week', CURRENT_DATE)::date - 14 AS week2,
            date_trunc('week', CURRENT_DATE)::date - 21 AS week3
        ),
        contracted AS (
          SELECT a.username, a.full_name, ui.program_code AS program
          FROM account a
          LEFT JOIN user_info ui ON ui.username = a.username
          WHERE a.active = true
            AND EXISTS (
              SELECT 1 FROM contract c, last_weeks w
              WHERE c.username = a.username
                AND c.type = 'REGULAR'
                AND c.start_date <= w.week1 + 6
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
        ),
        flags AS (
          SELECT
            c.username,
            c.full_name,
            c.program,
            EXISTS (
              SELECT 1 FROM contract ct, last_weeks w
              WHERE ct.username = c.username AND ct.type = 'REGULAR'
                AND ct.start_date <= w.week1 + 6 AND ct.end_date >= w.week1
            ) AS req1,
            EXISTS (
              SELECT 1 FROM contract ct, last_weeks w
              WHERE ct.username = c.username AND ct.type = 'REGULAR'
                AND ct.start_date <= w.week2 + 6 AND ct.end_date >= w.week2
            ) AS req2,
            EXISTS (
              SELECT 1 FROM contract ct, last_weeks w
              WHERE ct.username = c.username AND ct.type = 'REGULAR'
                AND ct.start_date <= w.week3 + 6 AND ct.end_date >= w.week3
            ) AS req3,
            EXISTS (SELECT 1 FROM week_report wr, last_weeks w WHERE wr.username = c.username AND wr.week_start = w.week1) AS has1,
            EXISTS (SELECT 1 FROM week_report wr, last_weeks w WHERE wr.username = c.username AND wr.week_start = w.week2) AS has2,
            EXISTS (SELECT 1 FROM week_report wr, last_weeks w WHERE wr.username = c.username AND wr.week_start = w.week3) AS has3,
            la.last_report_week
          FROM contracted c
          LEFT JOIN last_accepted la ON la.username = c.username
        )
        SELECT
          username AS "username",
          full_name AS "fullName",
          program AS "program",
          CASE
            WHEN req1 AND NOT has1 AND req2 AND NOT has2 AND req3 AND NOT has3 THEN 3
            WHEN req1 AND NOT has1 AND req2 AND NOT has2 THEN 2
            ELSE 0
          END AS "weeksMissed",
          last_report_week AS "lastReportWeek"
        FROM flags
        WHERE (req1 AND NOT has1 AND req2 AND NOT has2)
        ORDER BY weeksMissed DESC, full_name
        """,
        nativeQuery = true
    )
    fun findOverdue(): List<ReportOverdueProjection>
}
