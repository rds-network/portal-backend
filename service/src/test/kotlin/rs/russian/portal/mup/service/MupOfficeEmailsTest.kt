package rs.russian.portal.mup.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MupOfficeEmailsTest {

    @Test
    fun `belgrade uses central foreigners office`() {
        assertEquals("upravazastrance@mup.gov.rs", MupOfficeEmails.forCityCode("belgrade"))
        assertEquals("upravazastrance@mup.gov.rs", MupOfficeEmails.forCityName("Beograd"))
        assertEquals("upravazastrance@mup.gov.rs", MupOfficeEmails.forCityName("Београд"))
        assertEquals("upravazastrance@mup.gov.rs", MupOfficeEmails.forCityName("Zemun"))
    }

    @Test
    fun `regional seats use ozs mailbox`() {
        assertEquals("ozs.novisad@mup.gov.rs", MupOfficeEmails.forCityCode("novi-sad"))
        assertEquals("ozs.novisad@mup.gov.rs", MupOfficeEmails.forCityName("Novi Sad"))
        assertEquals("ozs.nis@mup.gov.rs", MupOfficeEmails.forCityName("Niš"))
        assertEquals("ozs.kragujevac@mup.gov.rs", MupOfficeEmails.forCityCode("kragujevac"))
    }

    @Test
    fun `unknown city falls back to belgrade office`() {
        assertEquals("upravazastrance@mup.gov.rs", MupOfficeEmails.forCityName(null))
        assertEquals("upravazastrance@mup.gov.rs", MupOfficeEmails.forCityName("Atlantis"))
    }

    @Test
    fun `office label for seat cities`() {
        assertEquals("Beograd", MupOfficeEmails.officeLabel("Zemun"))
        assertEquals("Novi Sad", MupOfficeEmails.officeLabel("Novi Sad"))
        assertEquals("Niš", MupOfficeEmails.officeLabelForEmail("ozs.nis@mup.gov.rs"))
        assertEquals("Beograd", MupOfficeEmails.officeLabelForEmail("upravazastrance@mup.gov.rs"))
    }
}
