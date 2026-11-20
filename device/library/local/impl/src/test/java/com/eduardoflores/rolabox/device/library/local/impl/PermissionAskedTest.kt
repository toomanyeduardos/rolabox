package com.eduardoflores.rolabox.device.library.local.impl

import com.eduardoflores.rolabox.device.library.local.impl.mediastore.askedOnThisInstall
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionAskedTest {
    @Test
    fun askedOnThisInstall_isAsked() {
        assertTrue(askedOnThisInstall("1700000000000", installedAt = 1_700_000_000_000))
    }

    @Test
    fun askedOnAnotherInstall_restoredFromABackup_isNotAsked() {
        assertFalse(askedOnThisInstall("1600000000000", installedAt = 1_700_000_000_000))
    }

    @Test
    fun neverAsked_orAnOlderVersionsValue_isNotAsked() {
        assertFalse(askedOnThisInstall(null, installedAt = 1_700_000_000_000))
        assertFalse(askedOnThisInstall("true", installedAt = 1_700_000_000_000))
    }
}
