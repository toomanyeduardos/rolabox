package com.eduardoflores.rolabox.buildlogic

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The release optimization check: a release must run R8 and shrink resources. */
class ReleaseOptimizationTest {
    @Test
    fun `a minified release that shrinks resources is allowed`() {
        assertNull(releaseOptimizationViolation(isMinifyEnabled = true, shrinkResources = true))
    }

    @Test
    fun `a release without R8 is not`() {
        val reason = releaseOptimizationViolation(isMinifyEnabled = false, shrinkResources = true)
        assertTrue(reason.orEmpty().startsWith("code shrinking (R8) turned off"))
    }

    @Test
    fun `a release without resource shrinking is not`() {
        val reason = releaseOptimizationViolation(isMinifyEnabled = true, shrinkResources = false)
        assertTrue(reason.orEmpty().startsWith("resource shrinking turned off"))
    }

    @Test
    fun `a release with both off names both`() {
        val reason = releaseOptimizationViolation(isMinifyEnabled = false, shrinkResources = false)
        assertTrue(reason.orEmpty().startsWith("code shrinking (R8) and resource shrinking turned off"))
    }
}
