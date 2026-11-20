package com.eduardoflores.rolabox.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a release is signed from the `rolaboxUpload*` properties (ADR-035, rule 1). */
class ReleaseSigningTest {
    private val all = mapOf(
        UPLOAD_STORE_FILE to "/keys/upload.jks",
        UPLOAD_STORE_PASSWORD to "store-secret",
        UPLOAD_KEY_ALIAS to "upload",
        UPLOAD_KEY_PASSWORD to "key-secret",
    )

    @Test
    fun `all four set signs with the upload key`() {
        assertEquals(
            ReleaseSigning.Upload("/keys/upload.jks", "store-secret", "upload", "key-secret"),
            releaseSigning(all),
        )
    }

    @Test
    fun `none set signs with the debug key`() {
        assertEquals(ReleaseSigning.Debug, releaseSigning(emptyMap()))
    }

    @Test
    fun `blank values count as not set`() {
        assertEquals(ReleaseSigning.Debug, releaseSigning(all.mapValues { " " }))
    }

    @Test
    fun `some set is incomplete and names the missing ones in order`() {
        val signing = releaseSigning(mapOf(UPLOAD_KEY_ALIAS to "upload"))
        assertEquals(
            ReleaseSigning.Incomplete(listOf(UPLOAD_STORE_FILE, UPLOAD_STORE_PASSWORD, UPLOAD_KEY_PASSWORD)),
            signing,
        )
    }

    @Test
    fun `one blank password is incomplete`() {
        assertEquals(
            ReleaseSigning.Incomplete(listOf(UPLOAD_KEY_PASSWORD)),
            releaseSigning(all + (UPLOAD_KEY_PASSWORD to "")),
        )
    }

    @Test
    fun `the upload signing never prints its passwords`() {
        val text = releaseSigning(all).toString()
        assertFalse(text.contains("secret"))
    }

    @Test
    fun `the release must end up with the config the convention chose`() {
        assertNull(releaseSigningViolation(releaseSigning(all), "upload"))
        assertNull(releaseSigningViolation(ReleaseSigning.Debug, "debug"))
    }

    @Test
    fun `a module that sets another signing config is a violation`() {
        val reason = releaseSigningViolation(releaseSigning(all), "debug")
        assertTrue(reason.orEmpty().startsWith("the release is signed with `debug` instead of `upload`"))
        assertTrue(releaseSigningViolation(ReleaseSigning.Debug, null).orEmpty().contains("`nothing`"))
    }
}
