package com.eduardoflores.rolabox.buildlogic

import org.gradle.api.Project

/** Every module convention plugin gets the same static analysis, so `./gradlew check` covers the whole codebase. */
internal fun Project.applyStaticAnalysis() {
    pluginManager.apply("rolabox.ktlint")
    pluginManager.apply("rolabox.detekt")
}
