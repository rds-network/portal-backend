package rs.russian.portal.report.repository

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import rs.russian.portal.report.domain.Report
import rs.russian.portal.report.repository.projections.VolunteerWeekProjection
import java.util.*
import org.springframework.stereotype.Repository as StereotypeRepository

@StereotypeRepository
interface ReportHeatMapRepository : Repository<Report, UUID> {

    @Query(
        value = """
    WITH input_accounts AS (
      SELECT a.username
      FROM account a
      WHERE a.username IN (:usernames)
    ),
    bounds AS (
      SELECT 
        make_date(:year, 1, 1)                         AS start_date,
        -- Ограничиваемся текущей датой, если год текущий
        LEAST(make_date(:year, 12, 31), CURRENT_DATE)  AS end_date
    ),
    weeks AS (
      SELECT
          GREATEST(gs::date, b.start_date)             AS week_start,
          LEAST(gs::date + interval '6 days', b.end_date)::date AS week_end,
          
          -- ВМЕСТО extract(iso_week):
          -- Просто нумеруем сгенерированные недели по порядку (1, 2, 3...)
          ROW_NUMBER() OVER (ORDER BY gs)::int         AS seq_week_number,
          
          -- Год всегда тот, который мы запросили (раз мы обрезали даты по границам)
          CAST(:year AS int)                           AS static_year
      FROM bounds b,
           generate_series(
             -- Начинаем с понедельника недели, в которую попадает 1 января
             date_trunc('week', b.start_date),
             date_trunc('week', b.end_date),
             interval '1 week'
           ) gs
    ),
    grid AS (
      SELECT ia.username, w.week_start, w.week_end, w.static_year, w.seq_week_number
      FROM input_accounts ia
      CROSS JOIN weeks w
    ),
    worked_minutes AS (
      SELECT
          r.user_login                                     AS username,
          date_trunc('week', t.date)::date                 AS week_start,
          SUM(t.time_spent)::bigint                        AS minutes_worked
      FROM report r
      JOIN task   t ON t.report_id = r.id
      CROSS JOIN bounds b
      WHERE r.deleted = FALSE
        AND r.status  = 'ACCEPTED'
        AND t.date BETWEEN b.start_date AND b.end_date
      GROUP BY r.user_login, date_trunc('week', t.date)::date
    ),
    weekly_base AS (
      SELECT
          g.username,
          g.week_start,
          g.week_end,
          g.static_year,
          g.seq_week_number,
          COALESCE(wm.minutes_worked, 0)                   AS minutes_worked,
          GREATEST(0,
            (
              SELECT COALESCE(SUM(
                  GREATEST(
                      0, 
                      (LEAST(g.week_end, c.end_date) - GREATEST(g.week_start, c.start_date)) + 1
                  )
              ), 0)
              FROM contract c
              WHERE c.username = g.username
                AND c.type     = 'REGULAR'
                AND c.start_date <= g.week_end
                AND c.end_date   >= g.week_start
            ) - COALESCE((
              SELECT COUNT(DISTINCT gs.d)::int
              FROM leave_request lr
              CROSS JOIN LATERAL generate_series(
                GREATEST(lr.start_date, g.week_start),
                LEAST(lr.end_date, g.week_end),
                interval '1 day'
              ) AS gs(d)
              WHERE lr.username = g.username
                AND lr.status = 'ACCEPTED'
                AND lr.start_date <= g.week_end
                AND lr.end_date >= g.week_start
            ), 0)
          )                                                AS active_days
      FROM grid g
      LEFT JOIN worked_minutes wm
             ON wm.username   = g.username
             -- Соединяем по интервалам
             AND wm.week_start <= g.week_end 
             AND wm.week_start + interval '6 days' >= g.week_start
    )
    SELECT
        wb.username                                        AS username,
        wb.static_year                                     AS year,
        wb.seq_week_number                                 AS week,
        wb.week_start                                      AS weekStart,
        wb.week_end                                        AS weekEnd,
        ROUND(wb.minutes_worked / 60.0, 2)::numeric(10,2)  AS hoursWorked,
        ROUND((wb.active_days::numeric / 7.0) * 10.0)::int AS hoursRequired
        
    FROM weekly_base wb
    ORDER BY wb.username, wb.week_start
    """,
        nativeQuery = true
    )
    fun findVolunteerHeatmap(
        @Param("usernames") usernames: Set<String>,
        @Param("year") year: Int,
    ): List<VolunteerWeekProjection>
}
