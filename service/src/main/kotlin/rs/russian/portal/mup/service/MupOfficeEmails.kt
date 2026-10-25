package rs.russian.portal.mup.service

/**
 * Emails of Odsek/Odeljenje za strance by Policijska uprava seat city.
 * Source: MUP «Adrese i brojevi telefona» (poslovi stranaca), ozs.*@mup.gov.rs.
 * Belgrade uses the central Uprava za strance address.
 */
object MupOfficeEmails {
    const val DEFAULT = "upravazastrance@mup.gov.rs"

    /** Portal city.code → MUP foreigners office email. */
    private val BY_CITY_CODE: Map<String, String> = mapOf(
        "belgrade" to DEFAULT,
        "bor" to "ozs.bor@mup.gov.rs",
        "valjevo" to "ozs.valjevo@mup.gov.rs",
        "vranje" to "ozs.vranje@mup.gov.rs",
        "zajecar" to "ozs.zajecar@mup.gov.rs",
        "zrenjanin" to "ozs.zrenjanin@mup.gov.rs",
        "jagodina" to "ozs.jagodina@mup.gov.rs",
        "kikinda" to "ozs.kikinda@mup.gov.rs",
        "kragujevac" to "ozs.kragujevac@mup.gov.rs",
        "kraljevo" to "ozs.kraljevo@mup.gov.rs",
        "krusevac" to "ozs.krusevac@mup.gov.rs",
        "leskovac" to "ozs.leskovac@mup.gov.rs",
        "nis" to "ozs.nis@mup.gov.rs",
        "novi-pazar" to "ozs.novipazar@mup.gov.rs",
        "novi-sad" to "ozs.novisad@mup.gov.rs",
        "pancevo" to "ozs.pancevo@mup.gov.rs",
        "pirot" to "ozs.pirot@mup.gov.rs",
        "pozarevac" to "ozs.pozarevac@mup.gov.rs",
        "prijepolje" to "ozs.prijepolje@mup.gov.rs",
        "prokuplje" to "ozs.prokuplje@mup.gov.rs",
        "smederevo" to "ozs.smederevo@mup.gov.rs",
        "sombor" to "ozs.sombor@mup.gov.rs",
        "sremska-mitrovica" to "ozs.sremskamitrovica@mup.gov.rs",
        "subotica" to "ozs.subotica@mup.gov.rs",
        "uzice" to "ozs.uzice@mup.gov.rs",
        "cacak" to "ozs.cacak@mup.gov.rs",
        "sabac" to "ozs.sabac@mup.gov.rs",
    )

    /** Extra free-text aliases (normalized) → city.code. */
    private val ALIASES: Map<String, String> = mapOf(
        "belgrade" to "belgrade",
        "beograd" to "belgrade",
        "београд" to "belgrade",
        "bg" to "belgrade",
        "novi sad" to "novi-sad",
        "нови сад" to "novi-sad",
        "ns" to "novi-sad",
        "nis" to "nis",
        "niš" to "nis",
        "ниш" to "nis",
        "novi pazar" to "novi-pazar",
        "нови пазар" to "novi-pazar",
        "sremska mitrovica" to "sremska-mitrovica",
        "сремска митровица" to "sremska-mitrovica",
        "zajecar" to "zajecar",
        "zaječar" to "zajecar",
        "зајечар" to "zajecar",
        "krusevac" to "krusevac",
        "kruševac" to "krusevac",
        "крушевац" to "krusevac",
        "pancevo" to "pancevo",
        "pančevo" to "pancevo",
        "панчево" to "pancevo",
        "pozarevac" to "pozarevac",
        "požarevac" to "pozarevac",
        "пожаревац" to "pozarevac",
        "uzice" to "uzice",
        "užice" to "uzice",
        "ужице" to "uzice",
        "cacak" to "cacak",
        "čačak" to "cacak",
        "чачак" to "cacak",
        "sabac" to "sabac",
        "šabac" to "sabac",
        "шабац" to "sabac",
        // Belgrade city municipalities → central Uprava za strance
        "zemun" to "belgrade",
        "земун" to "belgrade",
        "novi beograd" to "belgrade",
        "нови београд" to "belgrade",
        "vozdovac" to "belgrade",
        "voždovac" to "belgrade",
        "вождовац" to "belgrade",
        "vracar" to "belgrade",
        "vračar" to "belgrade",
        "врачар" to "belgrade",
        "zvezdara" to "belgrade",
        "звездара" to "belgrade",
        "palilula" to "belgrade",
        "палилула" to "belgrade",
        "cukarica" to "belgrade",
        "čukarica" to "belgrade",
        "чукарица" to "belgrade",
        "rakovica" to "belgrade",
        "раковица" to "belgrade",
        "obrenovac" to "belgrade",
        "обреновац" to "belgrade",
        "surcin" to "belgrade",
        "surčin" to "belgrade",
        "сурчин" to "belgrade",
    )

    /** Display name of Policijska uprava seat for UI badges. */
    private val LABEL_BY_CODE: Map<String, String> = mapOf(
        "belgrade" to "Beograd",
        "bor" to "Bor",
        "valjevo" to "Valjevo",
        "vranje" to "Vranje",
        "zajecar" to "Zaječar",
        "zrenjanin" to "Zrenjanin",
        "jagodina" to "Jagodina",
        "kikinda" to "Kikinda",
        "kragujevac" to "Kragujevac",
        "kraljevo" to "Kraljevo",
        "krusevac" to "Kruševac",
        "leskovac" to "Leskovac",
        "nis" to "Niš",
        "novi-pazar" to "Novi Pazar",
        "novi-sad" to "Novi Sad",
        "pancevo" to "Pančevo",
        "pirot" to "Pirot",
        "pozarevac" to "Požarevac",
        "prijepolje" to "Prijepolje",
        "prokuplje" to "Prokuplje",
        "smederevo" to "Smederevo",
        "sombor" to "Sombor",
        "sremska-mitrovica" to "Sremska Mitrovica",
        "subotica" to "Subotica",
        "uzice" to "Užice",
        "cacak" to "Čačak",
        "sabac" to "Šabac",
    )

    fun forCityCode(code: String?): String {
        if (code.isNullOrBlank()) return DEFAULT
        return BY_CITY_CODE[code.trim().lowercase()] ?: DEFAULT
    }

    /**
     * Resolve office email from free-text city (profile / application).
     * Prefer exact / alias match; optionally pass portal city codes already resolved.
     */
    fun forCityName(raw: String?, knownCityCode: String? = null): String {
        if (!knownCityCode.isNullOrBlank()) return forCityCode(knownCityCode)
        val normalized = normalize(raw) ?: return DEFAULT
        ALIASES[normalized]?.let { return forCityCode(it) }
        if (BY_CITY_CODE.containsKey(normalized)) return forCityCode(normalized)
        val dashed = normalized.replace(' ', '-')
        if (BY_CITY_CODE.containsKey(dashed)) return forCityCode(dashed)
        return DEFAULT
    }

    fun cityCodeForName(raw: String?, knownCityCode: String? = null): String? {
        if (!knownCityCode.isNullOrBlank()) return knownCityCode.trim().lowercase()
        val normalized = normalize(raw) ?: return null
        ALIASES[normalized]?.let { return it }
        if (BY_CITY_CODE.containsKey(normalized)) return normalized
        val dashed = normalized.replace(' ', '-')
        if (BY_CITY_CODE.containsKey(dashed)) return dashed
        return null
    }

    /** Human-readable seat city for the office that handles [raw] city. */
    fun officeLabel(raw: String?, knownCityCode: String? = null): String {
        val code = cityCodeForName(raw, knownCityCode) ?: "belgrade"
        return LABEL_BY_CODE[code] ?: LABEL_BY_CODE["belgrade"]!!
    }

    fun officeLabelForEmail(email: String?): String {
        val trimmed = email?.trim()?.takeIf { it.isNotEmpty() } ?: return LABEL_BY_CODE["belgrade"]!!
        if (trimmed.equals(DEFAULT, ignoreCase = true)) return LABEL_BY_CODE["belgrade"]!!
        val code = BY_CITY_CODE.entries.firstOrNull { it.value.equals(trimmed, ignoreCase = true) }?.key
        return code?.let { LABEL_BY_CODE[it] } ?: trimmed
    }

    fun normalize(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return trimmed
            .lowercase()
            .replace('č', 'c')
            .replace('ć', 'c')
            .replace('š', 's')
            .replace('ž', 'z')
            .replace('đ', 'd')
            .replace('Ч', 'c')
            .replace('Ć', 'c')
            .replace('Š', 's')
            .replace('Ž', 'z')
            .replace('Đ', 'd')
            .replace(Regex("\\s+"), " ")
    }
}
