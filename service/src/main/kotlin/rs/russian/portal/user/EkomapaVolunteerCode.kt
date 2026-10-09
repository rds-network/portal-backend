package rs.russian.portal.user

/**
 * Публичный код волонтёра Экомапы: EVO-{ekomapa users.id}.
 * Совпадает с App\Support\VolunteerPublicCode на ekomapa.
 */
object EkomapaVolunteerCode {
    const val PREFIX = "EVO"

    fun format(ekomapaUserId: Int): String = "$PREFIX-$ekomapaUserId"

    /**
     * Достаёт ekomapa user id из EVO-123 / evo123 / URL /v/EVO-25 / «чистых» цифр.
     * Для произвольного текста имени возвращает null (не путать с телефоном — только целиком цифры).
     */
    fun parseIdFromSearch(term: String): Int? {
        var raw = term.trim()
        if (raw.isEmpty()) return null

        val urlMatch = Regex("""(?:https?://[^\s/]+)?/v/(EVO[-#\s]?\d+)""", RegexOption.IGNORE_CASE)
            .find(raw)
        if (urlMatch != null) {
            raw = urlMatch.groupValues[1]
        } else {
            val inline = Regex("""\b(EVO[-#\s]?\d+)\b""", RegexOption.IGNORE_CASE).find(raw)
            if (inline != null) raw = inline.groupValues[1]
        }

        raw = raw.replace(Regex("""\s+"""), "")

        val evo = Regex("""^evo[-#]?(\d+)$""", RegexOption.IGNORE_CASE).matchEntire(raw)
        if (evo != null) {
            val id = evo.groupValues[1].toIntOrNull() ?: return null
            return id.takeIf { it > 0 }
        }

        if (raw.all { it.isDigit() }) {
            val id = raw.toIntOrNull() ?: return null
            return id.takeIf { it > 0 }
        }

        return null
    }

    /** RDS-V-000072 → portal Account.id (карта VOL-ID на портале). */
    fun parsePortalVolId(term: String): Int? {
        val raw = term.trim().replace(Regex("""\s+"""), "")
        val match = Regex("""^rds-?v-?0*(\d+)$""", RegexOption.IGNORE_CASE).matchEntire(raw) ?: return null
        val id = match.groupValues[1].toIntOrNull() ?: return null
        return id.takeIf { it > 0 }
    }
}
