package rs.russian.portal.user

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EkomapaVolunteerCodeTest {

    @Test
    fun `parses EVO codes and urls`() {
        assertEquals(123, EkomapaVolunteerCode.parseIdFromSearch("EVO-123"))
        assertEquals(33, EkomapaVolunteerCode.parseIdFromSearch("evo33"))
        assertEquals(25, EkomapaVolunteerCode.parseIdFromSearch("https://ekomapa.rs/v/EVO-25"))
        assertEquals(7, EkomapaVolunteerCode.parseIdFromSearch("код EVO-7 в отчёте"))
        assertEquals(12, EkomapaVolunteerCode.parseIdFromSearch("12"))
    }

    @Test
    fun `ignores non-code text`() {
        assertNull(EkomapaVolunteerCode.parseIdFromSearch("Анфиска666"))
        assertNull(EkomapaVolunteerCode.parseIdFromSearch("kotik"))
        assertNull(EkomapaVolunteerCode.parseIdFromSearch(""))
    }

    @Test
    fun `parses portal VOL-ID`() {
        assertEquals(72, EkomapaVolunteerCode.parsePortalVolId("RDS-V-000072"))
        assertEquals(1, EkomapaVolunteerCode.parsePortalVolId("rds-v-1"))
        assertNull(EkomapaVolunteerCode.parsePortalVolId("EVO-72"))
    }
}
