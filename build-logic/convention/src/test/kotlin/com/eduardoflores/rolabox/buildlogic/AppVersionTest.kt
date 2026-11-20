package com.eduardoflores.rolabox.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app's version from `rolabox.versionName` (ADR-035, rule 4). */
class AppVersionTest {
    private fun valid(value: String): AppVersion = (appVersion(value) as AppVersionResult.Valid).version

    private fun reason(value: String?): String = (appVersion(value) as AppVersionResult.Invalid).reason

    @Test
    fun `1_0_0 is code 10000`() {
        assertEquals(AppVersion(1, 0, 0), valid("1.0.0"))
        assertEquals(10_000, valid("1.0.0").code)
        assertEquals("1.0.0", valid("1.0.0").name)
    }

    @Test
    fun `1_2_3 is code 10203`() {
        assertEquals(10_203, valid("1.2.3").code)
    }

    @Test
    fun `the largest version stays under Play's limit`() {
        assertEquals(2_099_999_999, valid("209999.99.99").code)
    }

    @Test
    fun `the code grows with every part`() {
        val ordered = listOf("1.0.0", "1.0.1", "1.0.99", "1.1.0", "1.99.99", "2.0.0").map { valid(it).code }
        assertEquals(ordered.sorted(), ordered)
        assertEquals(ordered.size, ordered.toSet().size)
    }

    @Test
    fun `a missing or blank version is invalid`() {
        assertTrue(reason(null).contains("is not set"))
        assertTrue(reason(" ").contains("is not set"))
    }

    @Test
    fun `malformed versions are invalid`() {
        listOf("1.0", "1", "1.0.0.0", "v1.0.0", "1.0.0-beta", "1.100.0", "1.0.100", "01.0.0", "1.00.0", "a.b.c")
            .forEach { assertTrue(it, reason(it).contains("isn't MAJOR.MINOR.PATCH")) }
    }

    @Test
    fun `major 0 and majors past the limit are invalid`() {
        assertTrue(reason("0.1.0").contains("MAJOR goes from 1"))
        assertTrue(reason("210000.0.0").contains("MAJOR goes from 1"))
    }

    @Test
    fun `a variant must have the derived version`() {
        val version = AppVersion(1, 2, 3)
        assertNull(appVersionViolation(version, 10_203, "1.2.3"))
        assertTrue(appVersionViolation(version, 1, "1.2.3").orEmpty().startsWith("the version is 1.2.3 (1)"))
        assertTrue(appVersionViolation(version, 10_203, "1.0").orEmpty().contains("instead of 1.2.3 (10203)"))
    }
}
