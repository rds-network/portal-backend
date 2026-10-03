package rs.russian.portal.achievements.service

/**
 * Static catalog (RADIMO-style). Unlock is derived from ledger / account state.
 * Recurring weekly points are separate ledger codes, not one-shot badges.
 */
object AchievementCatalog {

    data class Def(
        val id: String,
        val category: String,
        val title: String,
        val description: String,
        /** Display value on the card (may match a one-shot award). */
        val points: Int,
        val kind: Kind,
        val target: Int = 1,
    )

    enum class Kind {
        WEEKLY_LOGINS,
        CLEAN_REPORTS,
        CURATOR_GRATITUDE,
        MANAGER_GRATITUDE,
        HAS_AVATAR,
        ANY_ACCEPTED_REPORT,
        POSITIVE_BALANCE,
    }

    val ALL: List<Def> = listOf(
        Def(
            id = "weekly_habit",
            category = "presence",
            title = "На связи",
            description = "Заходили на портал хотя бы раз за календарную неделю (+10).",
            points = 10,
            kind = Kind.WEEKLY_LOGINS,
            target = 1,
        ),
        Def(
            id = "weekly_streak_4",
            category = "presence",
            title = "Стабильность",
            description = "4 разные недели с визитом на портал.",
            points = 40,
            kind = Kind.WEEKLY_LOGINS,
            target = 4,
        ),
        Def(
            id = "weekly_streak_12",
            category = "presence",
            title = "Постоянство",
            description = "12 недель с визитом на портал.",
            points = 120,
            kind = Kind.WEEKLY_LOGINS,
            target = 12,
        ),
        Def(
            id = "face_of_service",
            category = "onboarding",
            title = "Лицо сервиса",
            description = "Загрузили фото профиля.",
            points = 20,
            kind = Kind.HAS_AVATAR,
        ),
        Def(
            id = "first_accepted",
            category = "reports",
            title = "Первый принятый отчёт",
            description = "Первый отчёт со статусом «Принят».",
            points = 30,
            kind = Kind.ANY_ACCEPTED_REPORT,
        ),
        Def(
            id = "clean_report",
            category = "reports",
            title = "Без замечаний",
            description = "Отчёт принят без замечания модератора (+5 за каждый).",
            points = 5,
            kind = Kind.CLEAN_REPORTS,
            target = 1,
        ),
        Def(
            id = "clean_report_5",
            category = "reports",
            title = "Аккуратный волонтёр",
            description = "5 отчётов принято без замечаний.",
            points = 25,
            kind = Kind.CLEAN_REPORTS,
            target = 5,
        ),
        Def(
            id = "clean_report_10",
            category = "reports",
            title = "Эталон отчётности",
            description = "10 отчётов принято без замечаний.",
            points = 50,
            kind = Kind.CLEAN_REPORTS,
            target = 10,
        ),
        Def(
            id = "curator_thanks",
            category = "gratitude",
            title = "Благодарность куратора",
            description = "Куратор отметил качественную работу при приёмке отчёта (+15).",
            points = 15,
            kind = Kind.CURATOR_GRATITUDE,
            target = 1,
        ),
        Def(
            id = "curator_thanks_5",
            category = "gratitude",
            title = "Доверие кураторов",
            description = "5 благодарностей куратора.",
            points = 50,
            kind = Kind.CURATOR_GRATITUDE,
            target = 5,
        ),
        Def(
            id = "manager_thanks",
            category = "gratitude",
            title = "Благодарность руководителя",
            description = "Руководитель отметил существенный вклад (+40).",
            points = 40,
            kind = Kind.MANAGER_GRATITUDE,
            target = 1,
        ),
        Def(
            id = "positive_score",
            category = "trust",
            title = "В плюсе",
            description = "Сумма баллов больше нуля.",
            points = 10,
            kind = Kind.POSITIVE_BALANCE,
        ),
    )
}
