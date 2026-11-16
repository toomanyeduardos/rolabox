package com.eduardoflores.rolabox.buildlogic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ADR-030 rules 7 and 8: what Crashlytics collects, and what each build uploads. */
class CrashReportingTest {
    @Test
    fun `collection is on in release only`() {
        assertTrue(crashlyticsCollectionEnabled("release"))
        assertFalse(crashlyticsCollectionEnabled("debug"))
    }

    @Test
    fun `a release with a real config uploads its mapping file`() {
        assertTrue(mappingFileUploadEnabled("release", usesRealConfig = true))
    }

    @Test
    fun `a release with the placeholder doesn't`() {
        assertFalse(mappingFileUploadEnabled("release", usesRealConfig = false))
    }

    @Test
    fun `debug never does`() {
        assertFalse(mappingFileUploadEnabled("debug", usesRealConfig = true))
        assertFalse(mappingFileUploadEnabled("debug", usesRealConfig = false))
    }
}
