package com.eduardoflores.rolabox

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADR-030 rule 7: a debug build doesn't start Crashlytics, so it saves no crash for a release build to send later.
 * Firebase creates only the products whose registrars its discovery service lists, and instrumented tests run on
 * the debug build.
 */
class NoCrashlyticsInDebugTest {
    private val registrars: Set<String> = run {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val discovery = ComponentName(context, "com.google.firebase.components.ComponentDiscoveryService")
        val metaData = context.packageManager.getServiceInfo(discovery, PackageManager.GET_META_DATA).metaData
        metaData.keySet().filter { it.startsWith("com.google.firebase.components:") }.toSet()
    }

    @Test
    fun firebaseIsStillDiscovered() {
        assertTrue(registrars.toString(), registrars.any { it.contains("firebase.auth") })
    }

    @Test
    fun crashlyticsIsNot() {
        assertEquals(emptyList<String>(), registrars.filter { it.contains("crashlytics", ignoreCase = true) })
    }
}
