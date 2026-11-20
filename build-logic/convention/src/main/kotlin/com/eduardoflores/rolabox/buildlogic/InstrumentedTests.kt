package com.eduardoflores.rolabox.buildlogic

import org.gradle.api.Project

/**
 * Whether the module builds instrumented tests. The app always does; a library only when it has `src/androidTest`,
 * which is how `rolabox.android.library` turns androidTest on or off. Convention plugins add androidTest
 * dependencies only where this is true: AGP warns about every one declared in a module whose androidTest is off.
 */
internal fun Project.buildsInstrumentedTests(): Boolean =
    pluginManager.hasPlugin("com.android.application") || file("src/androidTest").exists()
